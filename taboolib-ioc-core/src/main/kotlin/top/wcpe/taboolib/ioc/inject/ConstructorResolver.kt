package top.wcpe.taboolib.ioc.inject

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.Inject
import java.lang.reflect.Constructor

/**
 * 构造函数解析器 - 决定使用哪个构造函数
 */
class ConstructorResolver {

    /**
     * 解析类要使用的构造函数
     *
     * 优先级：
     * 1. 被 @Inject 标记的构造函数（若有多个，按**确定性顺序**选择，见 [selectInjectConstructor]）
     * 2. 唯一构造函数
     * 3. 无参构造函数
     *
     * @throws NoSuchMethodException 缺少可用构造函数且未标记 @Inject
     */
    fun resolve(clazz: Class<*>): Constructor<*> {
        val constructors = clazz.declaredConstructors

        // 1. 查找 @Inject 标记的构造函数
        val injectConstructors = constructors.filter { it.isAnnotationPresent(Inject::class.java) }
        if (injectConstructors.isNotEmpty()) {
            return selectInjectConstructor(clazz, injectConstructors)
        }

        // 2. 只有一个构造函数时直接使用
        if (constructors.size == 1) {
            return constructors[0]
        }

        // 3. 查找无参构造函数
        constructors.firstOrNull { it.parameterCount == 0 }?.let {
            return it
        }

        throw NoSuchMethodException("No suitable constructor found for ${clazz.name}, please mark one with @Inject")
    }

    /**
     * 在多个 @Inject 构造函数之间做确定性选择（C-P2 收尾项）。
     *
     * `Class.getDeclaredConstructors()` 的返回顺序在规范中未定义（取决于 JVM 实现），
     * 若直接取第一个会导致「同一份代码在不同 JVM/不同构建下选到不同构造函数」的不可复现行为。
     *
     * 选择规则（全序，保证确定性）：
     * 1. 参数更多者优先（更符合 DI 的「注入最多依赖」直觉）
     * 2. 参数个数相同时，按参数类型全限定名字典序升序
     * 3. 仍相同（理论上不会，因签名唯一）时按 `toString()` 兜底
     *
     * ## 关于「逐个尝试回退」为何不在此处实现
     *
     * 本方法在 [top.wcpe.taboolib.ioc.scan.ClassScanner] 的**静态扫描期**被调用，
     * 此刻容器尚未装配完成、依赖不一定可见，且本类不持有 registry/beanProvider，
     * 无法在扫描期做可靠的「可解析性试探测」（依赖未注册会被误判为不可解析）。
     * 因此此处只负责**给出确定性的首选顺序**（写回 `definition.constructor`）；
     * 真正的「按同一顺序逐个尝试、失败回退」交给运行期
     * `Injector.instantiate`（此时 `beanProvider` 已可用）执行，
     * 从而在消除 JVM 顺序不确定性的同时不牺牲可用性。
     *
     * 多候选时输出 warning，提示用户显式消除歧义。
     */
    private fun selectInjectConstructor(
        clazz: Class<*>,
        injectConstructors: List<Constructor<*>>
    ): Constructor<*> {
        if (injectConstructors.size == 1) {
            return injectConstructors[0]
        }

        val sorted = injectConstructors.sortedWith(
            compareByDescending<Constructor<*>> { it.parameterCount }
                .thenBy { ctor -> ctor.parameterTypes.joinToString(",") { it.name } }
                .thenBy { it.toString() }
        )
        val selected = sorted.first()

        warning(
            "[IoC] 类 ${clazz.name} 存在 ${injectConstructors.size} 个 @Inject 构造函数，" +
                "已按确定性规则选择参数最多者: " +
                "(${selected.parameterTypes.joinToString(", ") { it.simpleName }})。" +
                "建议只保留一个 @Inject 构造函数以消除歧义。"
        )
        debug("[IoC] @Inject 构造函数候选: ${injectConstructors.map { it.toString() }}，选中: $selected")
        return selected
    }
}
