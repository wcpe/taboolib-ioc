package top.wcpe.taboolib.ioc.bean

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.Component

/**
 * `BeanRegistry` 类型索引缓存的行为测试。
 *
 * `getByType` 处在 `getBean(Class)` 的最高频路径上，原先每次调用都要
 * `toList() + sortedBy { it.order }`；现在改为「写入侧重建不可变快照、读取侧直接命中」。
 * 缓存本身是引入复杂度的东西，因此这里逐条钉死它的语义：
 * **注册 / 移除 / 清空之后，快照必须立刻反映最新状态**（否则就是最难查的那种静默错）。
 */
class BeanRegistryTypeIndexCacheTest {

    @Test
    fun `getByType 应按 order 升序返回，且与注册顺序无关`() {
        val registry = BeanRegistry()
        registry.register(def("lateOrder", BrCacheShapeB::class.java, order = 900))
        registry.register(def("earlyOrder", BrCacheShapeA::class.java, order = 100))

        val names = registry.getByType(BrCacheShape::class.java).map { it.name }

        assertEquals(listOf("earlyOrder", "lateOrder"), names)
    }

    @Test
    fun `注册后应立刻反映到已缓存的类型索引`() {
        val registry = BeanRegistry()
        registry.register(def("first", BrCacheShapeA::class.java, order = 1))

        // 先读一次，把快照填进缓存
        assertEquals(1, registry.getByType(BrCacheShape::class.java).size)

        // 再注册，缓存必须失效
        registry.register(def("second", BrCacheShapeB::class.java, order = 2))

        val names = registry.getByType(BrCacheShape::class.java).map { it.name }
        assertEquals(listOf("first", "second"), names)
    }

    @Test
    fun `移除后应立刻从已缓存的类型索引中消失`() {
        val registry = BeanRegistry()
        registry.register(def("keep", BrCacheShapeA::class.java, order = 1))
        registry.register(def("drop", BrCacheShapeB::class.java, order = 2))

        // 先读一次，把快照填进缓存
        assertEquals(2, registry.getByType(BrCacheShape::class.java).size)

        registry.remove("drop")

        assertEquals(listOf("keep"), registry.getByType(BrCacheShape::class.java).map { it.name })
        // 名称索引与类型索引必须一致
        assertFalse(registry.contains("drop"))
        assertNull(registry.getByName("drop"))
    }

    @Test
    fun `clear 之后类型索引应为空`() {
        val registry = BeanRegistry()
        registry.register(def("a", BrCacheShapeA::class.java, order = 1))
        assertEquals(1, registry.getByType(BrCacheShape::class.java).size)

        registry.clear()

        assertTrue(registry.getByType(BrCacheShape::class.java).isEmpty())
        assertNull(registry.getPrimaryByType(BrCacheShape::class.java))
        assertTrue(registry.getNames().isEmpty())
    }

    @Test
    fun `同一注册状态下重复查询应命中同一个不可变快照`() {
        val registry = BeanRegistry()
        registry.register(def("only", BrCachePlain::class.java, order = 1))

        val first = registry.getByType(BrCachePlain::class.java)
        val second = registry.getByType(BrCachePlain::class.java)

        // 同一实例 = 缓存确实生效（若退化成每次重算，这里会失败）
        assertSame(first, second)

        // 且不可修改：外部误改不应能污染注册表内部的缓存
        @Suppress("UNCHECKED_CAST")
        val mutable = first as MutableList<BeanDefinition>
        assertThrows(UnsupportedOperationException::class.java) {
            mutable.add(def("intruder", BrCachePlain::class.java))
        }
        assertEquals(1, registry.getByType(BrCachePlain::class.java).size)
    }

    @Test
    fun `注册新 Bean 后旧快照应保持不变而不是被就地改写`() {
        val registry = BeanRegistry()
        registry.register(def("a", BrCacheShapeA::class.java, order = 1))
        val before = registry.getByType(BrCacheShape::class.java)

        registry.register(def("b", BrCacheShapeB::class.java, order = 2))
        val after = registry.getByType(BrCacheShape::class.java)

        // 写侧是「换一个新列表」，不是原地改 —— 已交出去的快照必须还是当时那个视图
        assertNotSame(before, after)
        assertEquals(listOf("a"), before.map { it.name })
        assertEquals(listOf("a", "b"), after.map { it.name })
    }

    @Test
    fun `多候选时应优先返回唯一的 Primary`() {
        val registry = BeanRegistry()
        registry.register(def("lowOrder", BrCacheShapeA::class.java, order = 1))
        registry.register(def("primary", BrCacheShapeB::class.java, order = 500, primary = true))

        assertEquals("primary", registry.getPrimaryByType(BrCacheShape::class.java)?.name)
    }

    @Test
    fun `多候选无 Primary 时应返回 order 最小者`() {
        val registry = BeanRegistry()
        registry.register(def("later", BrCacheShapeA::class.java, order = 700))
        registry.register(def("earlier", BrCacheShapeB::class.java, order = 3))

        assertEquals("earlier", registry.getPrimaryByType(BrCacheShape::class.java)?.name)
    }

    @Test
    fun `多个 Primary 时应抛出并列出全部冲突名称`() {
        val registry = BeanRegistry()
        registry.register(def("p1", BrCacheShapeA::class.java, order = 1, primary = true))
        registry.register(def("p2", BrCacheShapeB::class.java, order = 2, primary = true))

        val error = assertThrows(IllegalStateException::class.java) {
            registry.getPrimaryByType(BrCacheShape::class.java)
        }

        assertTrue(error.message!!.contains("多个 @Primary"), error.message!!)
        assertTrue(error.message!!.contains("p1"), error.message!!)
        assertTrue(error.message!!.contains("p2"), error.message!!)
    }

    @Test
    fun `按接口注册的 Bean 在接口类型与自身类型下都应可查到`() {
        val registry = BeanRegistry()
        registry.register(def("implA", BrCacheShapeA::class.java, order = 1))

        // resolveAssignableTypes 会为接口 / 父类都建桶
        assertEquals(listOf("implA"), registry.getByType(BrCacheShape::class.java).map { it.name })
        assertEquals(listOf("implA"), registry.getByType(BrCacheShapeA::class.java).map { it.name })
    }

    @Test
    fun `移除时应同时失效接口桶与自身类型桶`() {
        val registry = BeanRegistry()
        registry.register(def("implA", BrCacheShapeA::class.java, order = 1))
        assertEquals(1, registry.getByType(BrCacheShape::class.java).size)
        assertEquals(1, registry.getByType(BrCacheShapeA::class.java).size)

        registry.remove("implA")

        assertTrue(registry.getByType(BrCacheShape::class.java).isEmpty())
        assertTrue(registry.getByType(BrCacheShapeA::class.java).isEmpty())
    }

    @Test
    fun `未注册类型应返回空而不是 null`() {
        val registry = BeanRegistry()
        assertTrue(registry.getByType(BrCachePlain::class.java).isEmpty())
        assertNull(registry.getPrimaryByType(BrCachePlain::class.java))
    }

    private fun def(
        name: String,
        type: Class<*>,
        order: Int = Int.MAX_VALUE,
        primary: Boolean = false
    ) = BeanDefinition(
        name = name,
        type = type,
        constructor = type.getDeclaredConstructor(),
        injectFields = emptyList(),
        injectMethods = emptyList(),
        postConstruct = null,
        postEnable = null,
        preDestroy = null,
        constructorParameters = emptyList(),
        dependencies = emptyList(),
        isPrimary = primary,
        order = order
    )
}

interface BrCacheShape

@Component
class BrCacheShapeA : BrCacheShape

@Component
class BrCacheShapeB : BrCacheShape

@Component
class BrCachePlain
