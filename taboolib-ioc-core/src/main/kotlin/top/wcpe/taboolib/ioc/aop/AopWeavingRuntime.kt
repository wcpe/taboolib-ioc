package top.wcpe.taboolib.ioc.aop

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 编译期织入的**运行期入口** —— 由 `top.wcpe.taboolib.ioc` Gradle 插件生成的字节码调用。
 *
 * ## 生成的调用长什么样
 *
 * 构建期把被切点命中的方法体搬进合成方法（`<原名>$ioc$original`），原方法体替换为：
 *
 * ```java
 * public String greet(String name) {
 *     return (String) AopWeavingRuntime.invoke(this, "greet(Ljava/lang/String;)Ljava/lang/String;",
 *             "greet$ioc$original", new Object[]{ name });
 * }
 * ```
 *
 * 第二个参数是**构建期就算好的「方法名 + 描述符」key**（保证同名不同返回/参数的重载也能区分），
 * 运行期直接拿它做表查找 —— **不再反射解析方法、不再拼接字符串**。
 * （这一条是实测逼出来的：早期版本每次调用都反射 `getMethod` + 解析描述符 + 拼 key，
 *   真机稳态 886 ns/op；改成缓存后降回与代理路径同量级。）
 *
 * ## 热路径（每次调用都走）
 *
 * `ClassValue<ConcurrentHashMap<String, WeavingEntry>>` → 按 key 取到预热的 [WeavingEntry]（通知链或直达调用器），
 * 注册表版本一致就直接执行。只有「首次调用」与「切面注册表版本变化」时才重建。
 *
 * ## 安全网
 *
 * 任何异常（容器未初始化、无匹配切面、解析失败）都**回退到直接调用原始方法体**，
 * 绝不因为 AOP 让业务代码失败。
 */
object AopWeavingRuntime {

    /** 织入后原始方法体的名字后缀（与 Gradle 插件侧保持一致）。 */
    const val ORIGINAL_SUFFIX: String = "\$ioc\$original"

    @Volatile
    private var registry: AdvisorRegistry? = null

    /**
     * 代际号：attach/detach 时自增。
     *
     * `ClassValue.removeAll()` 是 protected 无法直接调用，因此改用「代际号 + 注册表版本号」
     * 双重校验让缓存失效 —— 同时保留 ClassValue 随类卸载自动回收的好处。
     */
    @Volatile
    private var generation: Int = 0

    /** 每个类一份「key → 调用入口」表；ClassValue 让缓存随类卸载一起释放。 */
    private val caches = object : ClassValue<ConcurrentHashMap<String, WeavingEntry>>() {
        override fun computeValue(type: Class<*>): ConcurrentHashMap<String, WeavingEntry> = ConcurrentHashMap()
    }

    private val warned = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** 挂上切面注册表。**仅供容器实现与测试支撑模块调用**，业务代码不要调用。 */
    fun attach(advisorRegistry: AdvisorRegistry) {
        registry = advisorRegistry
        generation++
    }

    /** 摘掉切面注册表（仅供容器实现与测试支撑模块调用）。 */
    fun detach() {
        registry = null
        generation++
    }

    /**
     * 织入方法的调用入口 —— 签名由构建期生成的字节码决定，不要手写调用。
     *
     * @param target 被调用实例
     * @param key 构建期算好的 `方法名 + 描述符`，如 `greet(Ljava/lang/String;)Ljava/lang/String;`
     * @param originalName 织入前保存原始方法体的合成方法名
     * @param args 装箱后的实参（无参时可为 null）
     */
    @JvmStatic
    fun invoke(target: Any, key: String, originalName: String, args: Array<Any?>?): Any? {
        val current = registry ?: return fallback(target, key, originalName, args)
        return try {
            val currentGeneration = generation
            val entry = entry(target.javaClass, key, originalName)
            if (entry.generation != currentGeneration || entry.version != current.version) {
                synchronized(entry) {
                    if (entry.generation != currentGeneration || entry.version != current.version) {
                        entry.rebuild(current, currentGeneration)
                    }
                }
            }
            val plan = entry.plan
            if (plan != null) plan.execute(target, args) else entry.direct.invoke(target, args)
        } catch (t: Throwable) {
            warnOnce(key, "[IoC] 织入方法 $key 的通知执行失败，已回退直接调用: ${t.message}")
            fallback(target, key, originalName, args)
        }
    }

    private fun entry(owner: Class<*>, key: String, originalName: String): WeavingEntry =
        caches.get(owner).getOrPut(key) { WeavingEntry(owner, key, originalName) }

    /** 无容器 / 异常时的兜底：直接调用原始方法体。 */
    private fun fallback(target: Any, key: String, originalName: String, args: Array<Any?>?): Any? =
        entry(target.javaClass, key, originalName).direct.invoke(target, args)

    private fun warnOnce(key: String, message: String) {
        if (warned.add(key)) {
            warning(message)
        }
    }

    /**
     * 一个被织入方法对应的调用入口。
     *
     * [direct] 必然可用（内部已含反射兜底）；[plan] 仅在**确实匹配到通知**时非空。
     */
    private class WeavingEntry(
        private val owner: Class<*>,
        key: String,
        private val originalName: String,
    ) {

        private val methodName: String
        private val descriptor: String

        init {
            val paren = key.indexOf('(')
            if (paren > 0) {
                methodName = key.substring(0, paren)
                descriptor = key.substring(paren)
            } else {
                methodName = key
                descriptor = "()V"
            }
        }

        @Volatile
        var version: Int = Int.MIN_VALUE
            private set

        @Volatile
        var generation: Int = Int.MIN_VALUE
            private set

        @Volatile
        var direct: TargetInvoker = AopInvokers.prepareUnboundTarget(owner, originalName, descriptor)
            private set

        @Volatile
        var plan: AopPlan? = null
            private set

        fun rebuild(advisorRegistry: AdvisorRegistry, currentGeneration: Int) {
            // 每次重建都重新准备句柄：句柄失败会内部回退反射，与原方法体保持一致
            direct = AopInvokers.prepareUnboundTarget(owner, originalName, descriptor)
            val method = resolveMethod()
            plan = if (method == null || AopExclusions.isMethodExcluded(owner, method)) {
                null
            } else {
                val matched = advisorRegistry.snapshotFor(owner).filter { it.matches(owner, method) }
                if (matched.isEmpty()) {
                    null
                } else {
                    debug("[IoC] 织入方法首次调用，已建立通知链: ${owner.name}#$methodName")
                    AopPlans.build(method, matched, direct)
                }
            }
            version = advisorRegistry.version
            generation = currentGeneration
        }

        private fun resolveMethod(): Method? = try {
            owner.getMethod(methodName, *MethodTypeParams.parse(descriptor, owner.classLoader))
        } catch (t: Throwable) {
            null
        }
    }
}

/** 从 JVM 描述符解析参数类型（避免依赖 asm）。**只在建表时调用一次**。 */
internal object MethodTypeParams {

    fun parse(descriptor: String, classLoader: ClassLoader?): Array<Class<*>> {
        val types = ArrayList<Class<*>>(4)
        var i = 1
        require(descriptor.startsWith("(")) { "非法的 JVM 方法描述符: $descriptor" }
        while (i < descriptor.length && descriptor[i] != ')') {
            var arrayDepth = 0
            while (descriptor[i] == '[') {
                arrayDepth++
                i++
            }
            val (type, next) = when (val ch = descriptor[i]) {
                'L' -> {
                    val end = descriptor.indexOf(';', i)
                    require(end > 0) { "非法的 JVM 方法描述符: $descriptor" }
                    descriptor.substring(i + 1, end).replace('/', '.') to end + 1
                }

                'B' -> "byte" to i + 1
                'C' -> "char" to i + 1
                'D' -> "double" to i + 1
                'F' -> "float" to i + 1
                'I' -> "int" to i + 1
                'J' -> "long" to i + 1
                'S' -> "short" to i + 1
                'Z' -> "boolean" to i + 1
                else -> throw IllegalArgumentException("非法的 JVM 方法描述符类型 '$ch': $descriptor")
            }
            var clazz = when (type) {
                "byte" -> Byte::class.javaPrimitiveType!!
                "char" -> Char::class.javaPrimitiveType!!
                "double" -> Double::class.javaPrimitiveType!!
                "float" -> Float::class.javaPrimitiveType!!
                "int" -> Int::class.javaPrimitiveType!!
                "long" -> Long::class.javaPrimitiveType!!
                "short" -> Short::class.javaPrimitiveType!!
                "boolean" -> Boolean::class.javaPrimitiveType!!
                else -> Class.forName(type, false, classLoader)
            }
            repeat(arrayDepth) { clazz = java.lang.reflect.Array.newInstance(clazz, 0).javaClass }
            types.add(clazz)
            i = next
        }
        return types.toTypedArray()
    }
}
