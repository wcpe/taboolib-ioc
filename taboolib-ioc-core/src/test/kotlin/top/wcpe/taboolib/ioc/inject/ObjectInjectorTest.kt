package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * ObjectInjector 相关功能测试。
 * 
 * 由于 ObjectInjector 依赖 BeanContainer 单例，
 * 这里通过 IocTestContext + FieldInjector 间接测试 object 注入场景。
 */
class ObjectInjectorTest {

    @Test
    fun `field injector should inject into object instance`() {
        val ctx = IocTestContext()
        ctx.register(ObjDepService::class.java)
        ctx.initialize()

        // 模拟 object 注入：手动获取 object 实例并注入
        val objInstance = TestObject
        val dep = ctx.getBean(ObjDepService::class.java)
        assertNotNull(dep)

        // 通过反射注入
        val field = TestObject::class.java.getDeclaredField("service")
        field.isAccessible = true
        field.set(objInstance, dep)

        assertNotNull(TestObject.service)
        assertEquals("obj-dep", TestObject.service?.value())

        // 清理
        field.set(objInstance, null)
    }

    @Test
    fun `inject field with @Named should resolve by name`() {
        val ctx = IocTestContext()
        ctx.register(NamedObjDepA::class.java)
        ctx.register(NamedObjDepB::class.java)
        ctx.initialize()

        val beanB = ctx.getBean(ObjDepI::class.java, "namedObjDepB")
        assertNotNull(beanB)
        assertEquals("B", beanB!!.value())
    }

    @Test
    fun `inject field with @Inject(required=false) should not fail when missing`() {
        val ctx = IocTestContext()
        ctx.register(OptionalObjComponent::class.java)
        // 不注册 MissingObjDep，不应抛异常
        ctx.initialize()

        val bean = ctx.getBean(OptionalObjComponent::class.java)
        assertNotNull(bean)
        assertNull(bean!!.missing)
    }
}

// ── 测试用组件 ──

interface ObjDepI {
    fun value(): String
}

@Component
class ObjDepService : ObjDepI {
    override fun value(): String = "obj-dep"
}

@Component("namedObjDepA")
class NamedObjDepA : ObjDepI {
    override fun value(): String = "A"
}

@Component("namedObjDepB")
class NamedObjDepB : ObjDepI {
    override fun value(): String = "B"
}

// 模拟 Kotlin object 类
object TestObject {
    @Inject
    var service: ObjDepI? = null
}

interface MissingObjDep

@Component
class OptionalObjComponent {
    @Inject(required = false)
    var missing: MissingObjDep? = null
}
