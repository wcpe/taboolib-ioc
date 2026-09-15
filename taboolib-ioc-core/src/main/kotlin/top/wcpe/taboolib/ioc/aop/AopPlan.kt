package top.wcpe.taboolib.ioc.aop

import top.wcpe.taboolib.ioc.bean.AdviceType
import top.wcpe.taboolib.ioc.bean.Advisor
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import top.wcpe.taboolib.ioc.bean.MethodInvocationChain
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.ArrayDeque

/**
 * 预热后的通知调用器。
 *
 * 入参语义按通知类型约定：
 * - `@Around` → [MethodInvocation]
 * - `@Before` / `@After` → 目标方法参数数组（可为 null）
 * - `@AfterReturning` → 返回值
 * - `@AfterThrowing` → 异常对象
 */
internal fun interface AdviceInvoker {
    fun invoke(payload: Any?): Any?
}

/**
 * 预热后的目标调用器（**按调用传入 target**，因此可被「代理」与「编译期织入」两条路径共用）。
 *
 * 代理路径在准备阶段就把接收者绑定进 `MethodHandle`，忽略传入的 target；
 * 织入路径使用**未绑定**句柄，接收者取每次调用传入的实例。
 */
internal fun interface TargetInvoker {
    fun invoke(target: Any, args: Array<out Any?>?): Any?
}

/**
 * 通知与目标方法调用的**预热工厂**。
 *
 * 优先使用 `MethodHandle`（零反射）；遇到无法 unreflect 的情况（非 public 类/方法等）
 * 自动回退到 `Method.invoke`，保证与旧实现完全一致的兜底行为。
 */
internal object AopInvokers {

    private val LOOKUP: MethodHandles.Lookup = MethodHandles.lookup()
    private val OBJECT = Any::class.java
    private val ARGS = Array<Any?>::class.java
    private val EMPTY_ARGS = arrayOfNulls<Any?>(0)

    fun prepareAdvice(advisor: Advisor): AdviceInvoker {
        val method = advisor.adviceMethod
        val instance = advisor.aspectInstance
        val count = method.parameterCount
        val type = advisor.adviceType
        return try {
            val bound = LOOKUP.unreflect(method).bindTo(instance)
            when (type) {
                AdviceType.AROUND -> {
                    val h = bound.asType(MethodType.methodType(OBJECT, MethodInvocation::class.java))
                    AdviceInvoker { payload -> h.invoke(payload) }
                }

                AdviceType.BEFORE, AdviceType.AFTER -> if (count == 0) {
                    val h = bound.asType(MethodType.methodType(OBJECT))
                    AdviceInvoker { _ -> h.invoke() }
                } else {
                    val h = bound.asSpreader(ARGS, count).asType(MethodType.methodType(OBJECT, ARGS))
                    AdviceInvoker { payload -> h.invoke(payload ?: EMPTY_ARGS) }
                }

                AdviceType.AFTER_RETURNING, AdviceType.AFTER_THROWING -> when (count) {
                    0 -> {
                        val h = bound.asType(MethodType.methodType(OBJECT))
                        AdviceInvoker { _ -> h.invoke() }
                    }

                    1 -> {
                        val h = bound.asType(MethodType.methodType(OBJECT, OBJECT))
                        AdviceInvoker { payload -> h.invoke(payload) }
                    }

                    // 多参数签名在扫描期已被拒绝，这里仅作兜底（保持旧行为）
                    else -> reflectiveAdvice(advisor)
                }
            }
        } catch (t: Throwable) {
            reflectiveAdvice(advisor)
        }
    }

    /** 代理路径：接收者绑定进句柄。 */
    fun prepareTarget(target: Any, method: Method): TargetInvoker {
        return try {
            val h = LOOKUP.unreflect(method)
                .bindTo(target)
                .asSpreader(ARGS, method.parameterCount)
                .asType(MethodType.methodType(OBJECT, ARGS))
            TargetInvoker { _, args -> h.invoke(args ?: EMPTY_ARGS) }
        } catch (t: Throwable) {
            reflectiveTarget(target, method)
        }
    }

    /**
     * 织入路径：按「宿主类 + 方法名 + 描述符」准备**未绑定**句柄（接收者由调用方传入）。
     *
     * @param owner 宿主类
     * @param name 方法名（织入后原始方法体的名字，形如 `xxx$ioc$original`）
     * @param descriptor JVM 方法描述符，如 `(I)I`
     */
    fun prepareUnboundTarget(owner: Class<*>, name: String, descriptor: String): TargetInvoker {
        return try {
            val type = MethodType.fromMethodDescriptorString(descriptor, owner.classLoader)
            val h = LOOKUP.findVirtual(owner, name, type)
                .asSpreader(ARGS, type.parameterCount())
                .asType(MethodType.methodType(OBJECT, OBJECT, ARGS))
            TargetInvoker { target, args -> h.invoke(target, args ?: EMPTY_ARGS) }
        } catch (t: Throwable) {
            reflectiveUnboundTarget(owner, name, descriptor)
        }
    }

    /** 反射兜底：与改造前 `InterceptorChain` 的调用方式逐字对齐。 */
    private fun reflectiveAdvice(advisor: Advisor): AdviceInvoker {
        val method = advisor.adviceMethod
        val instance = advisor.aspectInstance
        val count = method.parameterCount
        return when (advisor.adviceType) {
            AdviceType.AROUND -> AdviceInvoker { payload -> method.invoke(instance, payload) }

            AdviceType.BEFORE, AdviceType.AFTER -> AdviceInvoker { payload ->
                if (count == 0) {
                    method.invoke(instance)
                } else {
                    method.invoke(instance, *(payload as Array<out Any?>? ?: EMPTY_ARGS))
                }
            }

            AdviceType.AFTER_RETURNING, AdviceType.AFTER_THROWING -> AdviceInvoker { payload ->
                if (count == 0) method.invoke(instance) else method.invoke(instance, payload)
            }
        }
    }

    /** 反射兜底：目标方法，并按旧语义拆掉 [InvocationTargetException]。 */
    private fun reflectiveTarget(target: Any, method: Method): TargetInvoker {
        return TargetInvoker { _, args ->
            try {
                method.invoke(target, *(args ?: EMPTY_ARGS))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    /** 反射兜底：织入路径的原始方法体。 */
    private fun reflectiveUnboundTarget(owner: Class<*>, name: String, descriptor: String): TargetInvoker {
        val method = runCatching {
            MethodType.fromMethodDescriptorString(descriptor, owner.classLoader)
            owner.getDeclaredMethod(
                name,
                *MethodType.fromMethodDescriptorString(descriptor, owner.classLoader).parameterArray()
            )
        }.getOrNull()
        return TargetInvoker { target, args ->
            if (method == null) {
                throw IllegalStateException("[IoC] 无法定位织入的原始方法 $name$descriptor (${owner.name})")
            }
            method.isAccessible = true
            try {
                method.invoke(target, *(args ?: EMPTY_ARGS))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }
}

/**
 * 一个被切面命中的方法对应的**预热调用计划**：通知已按类型分好组、目标方法已绑定句柄，
 * 运行期不再做切点匹配、不再建链、不再反射。
 *
 * 同一个计划可被**代理**路径（每个 Bean 一份）与**编译期织入**路径（每个类一份）共用。
 */
internal class AopPlan(
    private val before: Array<AdviceInvoker>,
    private val around: Array<AdviceInvoker>,
    private val after: Array<AdviceInvoker>,
    private val afterReturning: Array<AdviceInvoker>,
    private val afterThrowing: Array<AdviceInvoker>,
    private val targetInvoker: TargetInvoker,
    val method: Method
) {

    fun execute(target: Any, args: Array<out Any?>?): Any? {
        val invocation = INVOCATIONS.acquire()
        try {
            invocation.bind(this, target, args)
            for (i in before.indices) {
                before[i].invoke(args)
            }
            var result: Any? = null
            var thrown: Throwable? = null
            try {
                result = if (around.isNotEmpty()) invocation.proceed() else invokeTarget(target, args)
            } catch (t: Throwable) {
                thrown = t
            } finally {
                for (i in after.indices) {
                    runCatching { after[i].invoke(args) }
                }
            }
            if (thrown != null) {
                for (i in afterThrowing.indices) {
                    runCatching { afterThrowing[i].invoke(thrown) }
                }
                throw thrown
            }
            for (i in afterReturning.indices) {
                runCatching { afterReturning[i].invoke(result) }
            }
            return result
        } finally {
            INVOCATIONS.release(invocation)
        }
    }

    /** 由 [ReusableInvocation.proceed] 驱动：依次走完 @Around 链，最后落到目标方法。 */
    internal fun proceedFrom(invocation: ReusableInvocation): Any? {
        val index = invocation.cursor
        if (index < around.size) {
            invocation.cursor = index + 1
            return around[index].invoke(invocation)
        }
        return invokeTarget(invocation.target, invocation.arguments)
    }

    private fun invokeTarget(target: Any, args: Array<out Any?>?): Any? = targetInvoker.invoke(target, args)
}

/**
 * 可**重复绑定**的 [MethodInvocation] 实现 —— 热路径上不再每次新建对象。
 *
 * 复用是安全的：实例按线程池化（`acquire`/`release`），嵌套的受切调用会各自拿到
 * 独立实例；绑定后 `target` / `method` / `arguments` 始终对应当前这次调用。
 */
internal class ReusableInvocation : MethodInvocation(PLACEHOLDER_TARGET, PLACEHOLDER_METHOD, null, PLACEHOLDER_CHAIN) {

    private var plan: AopPlan? = null

    var cursor: Int = 0

    fun bind(plan: AopPlan, target: Any, args: Array<out Any?>?) {
        this.plan = plan
        this.cursor = 0
        this.target = target
        this.method = plan.method
        this.arguments = args
    }

    override fun proceed(): Any? {
        val current = plan ?: error("MethodInvocation 未绑定调用计划")
        return current.proceedFrom(this)
    }

    companion object {
        private val PLACEHOLDER_TARGET: Any = Any()
        private val PLACEHOLDER_METHOD: Method = Any::class.java.getDeclaredMethod("hashCode")
        private val PLACEHOLDER_CHAIN = object : MethodInvocationChain {
            override fun proceed(invocation: MethodInvocation): Any? =
                error("占位调用链不应被调用")
        }
    }
}

/** 线程局部的小对象池（按需增长、有上限，避免异常路径把池撑爆）。 */
private object INVOCATIONS {

    private const val MAX_POOLED = 8

    private val pool = ThreadLocal.withInitial { ArrayDeque<ReusableInvocation>(2) }

    fun acquire(): ReusableInvocation = pool.get().pollLast() ?: ReusableInvocation()

    fun release(invocation: ReusableInvocation) {
        val deque = pool.get()
        if (deque.size < MAX_POOLED) {
            deque.addLast(invocation)
        }
    }
}
