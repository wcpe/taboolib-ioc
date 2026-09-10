package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanContainer

/**
 * ObjectInjector 的 object 注入功能测试。
 *
 * ## 测试有效性说明（A-P0-07 重写）
 *
 * 旧版本测试体自己手写 `field.set(objInstance, dep)` 完成「注入」，
 * **从不调用 `ObjectInjector.injectObject`** —— 把生产侧的 object 注入整体删除也依然全绿
 * （文件名与注释还自承是「间接测试」），属典型伪测试。
 *
 * 本版本直接调用**真实生产入口** `ObjectInjector.injectObject(obj, clazz)`，
 * 并以真实 `BeanContainer` 单例提供依赖解析。若生产侧 object 注入逻辑被改坏
 * （例如 `injectObject` 提前 return / 不执行 `field.set`），本测试立即变红。
 *
 * 说明：`ObjectInjector.injectObject` 是 `ObjectInjector`（Kotlin object）上的公开方法，
 * 内部通过 `BeanContainer.getBean` 解析依赖，因此测试需初始化真实容器。
 */
class ObjectInjectorTest {

    @BeforeEach
    fun setUp() {
        BeanContainer.resetForTesting()
        TestObject.service = null
    }

    @AfterEach
    fun tearDown() {
        TestObject.service = null
        BeanContainer.resetForTesting()
    }

    @Test
    fun `injectObject injects @Inject field into object instance via real production code`() {
        // 真实容器：注册依赖并初始化
        registerScanned(ObjDepService::class.java)
        BeanContainer.initialize()

        assertNull(TestObject.service, "注入前 object 字段应为 null")

        // 真实生产入口：ObjectInjector.injectObject(obj, clazz)
        ObjectInjector.injectObject(TestObject, TestObject::class.java)

        assertNotNull(TestObject.service, "ObjectInjector.injectObject 应把依赖注入到 object 实例字段")
        assertEquals("obj-dep", TestObject.service!!.value())
    }

    @Test
    fun `injectObject respects @Named qualifier`() {
        registerScanned(NamedObjDepA::class.java)
        registerScanned(NamedObjDepB::class.java)
        BeanContainer.initialize()

        ObjectInjector.injectObject(NamedTestObject, NamedTestObject::class.java)

        assertNotNull(NamedTestObject.service, "@Named 限定的 object 字段应被注入")
        assertEquals("B", NamedTestObject.service!!.value(), "@Named(\"namedObjDepB\") 应解析到 B 实现")
    }

    /**
     * 判别性负向测试：容器中不存在匹配依赖时，`injectObject` 不得把字段置为某个错误值。
     *
     * 这里用 `required=false` 的可选注入点验证：缺失依赖时字段保持 null 且不抛异常，
     * 证明注入确实是「按类型解析真实 Bean」的结果，而非无条件写值。
     */
    @Test
    fun `injectObject leaves optional field null when dependency missing`() {
        // 只初始化空容器（不注册 MissingObjDep）
        BeanContainer.initialize()

        assertDoesNotThrow {
            ObjectInjector.injectObject(OptionalObjObject, OptionalObjObject::class.java)
        }
        assertNull(OptionalObjObject.missing, "依赖缺失时可选 object 字段应保持 null")
    }

    private fun registerScanned(clazz: Class<*>) {
        val definition = BeanContainer.getScanner().scan(clazz) ?: error("扫描失败: ${clazz.name}")
        BeanContainer.getRegistry().register(definition)
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

object NamedTestObject {
    @Inject
    @Named("namedObjDepB")
    var service: ObjDepI? = null
}

interface MissingObjDep

object OptionalObjObject {
    @Inject(required = false)
    var missing: MissingObjDep? = null
}

@Component
class OptionalObjComponent {
    @Inject(required = false)
    var missing: MissingObjDep? = null
}
