package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.findAnnotation
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.hasAnnotation

// ── 顶层测试辅助类（避免嵌套 companion object 触发 Kotlin FIR 编译器 bug）──

interface CompanionSvcI {
    fun value(): String
}

@Component
class CompanionSvc : CompanionSvcI {
    override fun value() = "companion-service"
}

interface CompanionLabelI {
    fun label(): String
}

@Component("cmpSvcA")
class CompanionSvcA : CompanionLabelI {
    override fun label() = "A"
}

@Component("cmpSvcB")
class CompanionSvcB : CompanionLabelI {
    override fun label() = "B"
}

// @JvmField：backing field 在外部类静态字段，$annotations 在 Companion 类
@Component
class ClassWithJvmFieldCompanion {
    companion object {
        @Inject
        @JvmField
        var service: CompanionSvcI? = null
    }
}

@Component
class ClassWithNamedJvmFieldCompanion {
    companion object {
        @Inject
        @Named("cmpSvcA")
        @JvmField
        var serviceA: CompanionLabelI? = null
    }
}

// 非 @JvmField：backing field 也是外部类静态字段，$annotations 在 Companion 类
@Component
class ClassWithNonJvmFieldCompanion {
    companion object {
        @Inject
        var service: CompanionSvcI? = null
    }
}

/**
 * 验证 companion object 字段注入功能的**端到端**行为。
 *
 * Kotlin companion object 属性有两种 JVM 编译形式：
 * - @JvmField：backing field 是外部类静态字段，$annotations 在 Companion 类
 * - 普通属性：backing field 也在外部类静态字段，$annotations 也在 Companion 类
 *
 * ## 测试有效性说明（A-P0-06 重写）
 *
 * 旧版本测试体自己手写 `for (field in outerClass.declaredFields) { field.set(null, value) }`
 * 完成「注入」，**从不调用任何生产代码** —— 把生产侧的 companion 注入逻辑整体删除也依然全绿，
 * 属于典型的伪测试（假绿）。
 *
 * 本版本改为走**真实生产入口**：把带 companion 注入点的外部类注册为 `@Component`，
 * 由 `IocTestContext.initialize()` → `LifecycleManager` → `FieldInjector.injectFields`
 * 完成实例化与字段注入；注入点本身由生产代码 `ClassScanner.resolveInjectFields` +
 * `KotlinPropertyAnnotations.findAnnotationCarrierInCompanion` 从 Companion 类的
 * `xxx$annotations` 载体上解析。若生产侧 companion 注入（注解载体跨类查找 / 静态字段写回）
 * 任一环节被改坏，本测试即变红。
 */
class CompanionObjectInjectorTest {

    @BeforeEach
    fun cleanup() {
        // @JvmField：静态字段在外部类，动态清除所有可空静态字段
        ClassWithJvmFieldCompanion::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "Companion" }
            .forEach { it.isAccessible = true; it.set(null, null) }

        ClassWithNamedJvmFieldCompanion::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "Companion" }
            .forEach { it.isAccessible = true; it.set(null, null) }

        // 非 @JvmField：字段也在外部类静态字段，动态清除所有非 Companion 静态字段
        ClassWithNonJvmFieldCompanion::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "Companion" }
            .forEach { it.isAccessible = true; it.set(null, null) }
    }

    @Test
    fun `KotlinPropertyAnnotations detects @Inject on JvmField companion backing field`() {
        // @JvmField backing field 在外部类静态字段
        val field = ClassWithJvmFieldCompanion::class.java.declaredFields
            .firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "Companion" }
        assertNotNull(field, "外部类应有静态 backing field")
        assertTrue(
            field!!.hasAnnotation(Inject::class.java),
            "@JvmField companion backing field 应能检测到 @Inject 注解（xxx${'$'}annotations 载体在 Companion 类）"
        )
    }

    @Test
    fun `KotlinPropertyAnnotations detects @Inject on non-JvmField companion backing field`() {
        // 非 @JvmField 的 backing field 同样在外部类的静态字段上
        val field = ClassWithNonJvmFieldCompanion::class.java.declaredFields
            .firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "Companion" }
        assertNotNull(field, "外部类应有静态 backing field")
        assertTrue(
            field!!.hasAnnotation(Inject::class.java),
            "非 @JvmField companion 静态 backing field 应能检测到 @Inject 注解"
        )
    }

    @Test
    fun `JvmField companion object field is injected by container via outer class static field`() {
        val ctx = IocTestContext()
        ctx.register(CompanionSvc::class.java)
        // 关键：注册外部类，由容器实例化 + 注入其 companion 字段（真实生产路径）
        ctx.register(ClassWithJvmFieldCompanion::class.java)
        ctx.initialize()

        // 外部类实例被容器创建，注入发生在构造/字段注入阶段，写回到外部类的静态 backing field
        assertNotNull(
            ctx.getBean(ClassWithJvmFieldCompanion::class.java),
            "含 companion 注入点的外部类应能作为 @Component 被容器创建"
        )
        assertNotNull(ClassWithJvmFieldCompanion.service, "companion 字段应被容器注入（非 null）")
        assertEquals("companion-service", ClassWithJvmFieldCompanion.service!!.value())
    }

    @Test
    fun `JvmField companion object field with @Named resolves correct bean`() {
        val ctx = IocTestContext()
        ctx.register(CompanionSvcA::class.java)
        ctx.register(CompanionSvcB::class.java)
        ctx.register(ClassWithNamedJvmFieldCompanion::class.java)
        ctx.initialize()

        assertNotNull(ctx.getBean(ClassWithNamedJvmFieldCompanion::class.java))
        assertNotNull(ClassWithNamedJvmFieldCompanion.serviceA, "带 @Named 的 companion 字段应被注入")
        assertEquals("A", ClassWithNamedJvmFieldCompanion.serviceA!!.label())
    }

    @Test
    fun `non-JvmField companion object property is injected by container`() {
        val ctx = IocTestContext()
        ctx.register(CompanionSvc::class.java)
        ctx.register(ClassWithNonJvmFieldCompanion::class.java)
        ctx.initialize()

        assertNotNull(ctx.getBean(ClassWithNonJvmFieldCompanion::class.java))
        assertNotNull(ClassWithNonJvmFieldCompanion.service, "非 @JvmField companion 字段应被容器注入")
        assertEquals("companion-service", ClassWithNonJvmFieldCompanion.service!!.value())
    }

    /**
     * 判别性负向测试：仅注册**依赖**、不注册外部类时，companion 字段不得被注入。
     *
     * 这锁定了「companion 注入是由容器对外部类执行注入时发生的」这一因果，
     * 防止测试退化为「字段恰好非空」的假绿。
     */
    @Test
    fun `companion field stays null when outer class is not registered`() {
        val ctx = IocTestContext()
        ctx.register(CompanionSvc::class.java)
        // 故意不注册 ClassWithJvmFieldCompanion
        ctx.initialize()

        assertNull(
            ClassWithJvmFieldCompanion.service,
            "外部类未被注册/实例化时，其 companion 字段不得被注入（否则测试不具判别性）"
        )
    }

    /**
     * 判别性负向测试：依赖缺失时，注册外部类必须先失败（required 语义），
     * 且不得把 companion 字段注入为任何错误值。
     *
     * 这锁定了「companion 注入是由容器驱动的依赖解析」这一因果：
     * 若生产侧不再对 companion 字段执行注入，则注入失败不会被触发，本测试即变红。
     */
    @Test
    fun `companion required field fails fast when dependency bean is absent`() {
        val ctx = IocTestContext()
        // 故意不注册 CompanionSvc，只注册外部类
        ctx.register(ClassWithJvmFieldCompanion::class.java)

        val error = assertThrows(IllegalStateException::class.java) { ctx.initialize() }
        assertTrue(
            error.message!!.contains("ClassWithJvmFieldCompanion.service"),
            "必需 companion 字段缺依赖时应显式失败并指明字段，实际: ${error.message}"
        )
        assertNull(
            ClassWithJvmFieldCompanion.service,
            "依赖 Bean 不存在时 companion 字段不得被注入（证明注入确实经过容器解析）"
        )
    }
}
