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
class ClassWithJvmFieldCompanion {
    companion object {
        @Inject
        @JvmField
        var service: CompanionSvcI? = null
    }
}

class ClassWithNamedJvmFieldCompanion {
    companion object {
        @Inject
        @Named("cmpSvcA")
        @JvmField
        var serviceA: CompanionLabelI? = null
    }
}

// 非 @JvmField：backing field 在 Companion 实例字段，$annotations 在 Companion 类
class ClassWithNonJvmFieldCompanion {
    companion object {
        @Inject
        var service: CompanionSvcI? = null
    }
}

/**
 * 验证 companion object 字段注入功能。
 *
 * Kotlin companion object 属性有两种 JVM 编译形式：
 * - @JvmField：backing field 是外部类静态字段，$annotations 在 Companion 类
 * - 普通属性：backing field 是 Companion 实例字段，$annotations 也在 Companion 类
 *
 * KotlinPropertyAnnotations 需要能从 Companion 类查找 $annotations 方法。
 * ObjectInjector 需要处理两种形式的注入。
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

        // 非 @JvmField：字段在 Companion 实例，动态清除所有非 INSTANCE 字段
        val companionInst = ClassWithNonJvmFieldCompanion.Companion
        companionInst.javaClass.declaredFields
            .filter { it.name != "INSTANCE" }
            .forEach { it.isAccessible = true; it.set(companionInst, null) }
    }

    @Test
    fun `KotlinPropertyAnnotations detects @Inject on JvmField companion backing field`() {
        // @JvmField backing field 在外部类静态字段
        val field = ClassWithJvmFieldCompanion::class.java.declaredFields
            .firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "Companion" }
        assertNotNull(field, "外部类应有静态 backing field")
        assertTrue(
            field!!.hasAnnotation(Inject::class.java),
            "@JvmField companion backing field 应能检测到 @Inject 注解"
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
    fun `JvmField companion object field is injected via outer class static field`() {
        val ctx = IocTestContext()
        ctx.register(CompanionSvc::class.java)
        ctx.initialize()

        val outerClass = ClassWithJvmFieldCompanion::class.java
        for (f in outerClass.declaredFields) {
            if (f.name == "Companion") continue
            if (!java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
            if (!f.hasAnnotation(Inject::class.java)) continue
            val value = ctx.getBean(f.type)
            assertNotNull(value, "容器中应有 CompanionSvc")
            f.isAccessible = true
            f.set(null, value)
        }

        assertNotNull(ClassWithJvmFieldCompanion.service)
        assertEquals("companion-service", ClassWithJvmFieldCompanion.service!!.value())
    }

    @Test
    fun `JvmField companion object field with @Named resolves correct bean`() {
        val ctx = IocTestContext()
        ctx.register(CompanionSvcA::class.java)
        ctx.register(CompanionSvcB::class.java)
        ctx.initialize()

        val outerClass = ClassWithNamedJvmFieldCompanion::class.java
        for (f in outerClass.declaredFields) {
            if (f.name == "Companion") continue
            if (!java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
            if (!f.hasAnnotation(Inject::class.java)) continue
            val named = f.findAnnotation(Named::class.java)
            val nameQualifier = named?.value?.takeIf { it.isNotEmpty() }
            val value = ctx.getBean(f.type, nameQualifier)
            assertNotNull(value)
            f.isAccessible = true
            f.set(null, value)
        }

        assertNotNull(ClassWithNamedJvmFieldCompanion.serviceA)
        assertEquals("A", ClassWithNamedJvmFieldCompanion.serviceA!!.label())
    }

    @Test
    fun `non-JvmField companion object property is injected via outer class static field`() {
        val ctx = IocTestContext()
        ctx.register(CompanionSvc::class.java)
        ctx.initialize()

        // 非 @JvmField 的 backing field 与 @JvmField 一样在外部类的静态字段
        val outerClass = ClassWithNonJvmFieldCompanion::class.java
        for (f in outerClass.declaredFields) {
            if (f.name == "Companion") continue
            if (!java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
            if (!f.hasAnnotation(Inject::class.java)) continue
            val value = ctx.getBean(f.type)
            assertNotNull(value)
            f.isAccessible = true
            f.set(null, value)
        }

        assertNotNull(ClassWithNonJvmFieldCompanion.service)
        assertEquals("companion-service", ClassWithNonJvmFieldCompanion.service!!.value())
    }
}
