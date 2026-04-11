package top.wcpe.ioc.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import taboolib.common.LifeCycle
import taboolib.common.TabooLib
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Repository
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.test.IocAutowired
import top.wcpe.taboolib.ioc.test.TabooLibIocTest
import top.wcpe.taboolib.ioc.test.TabooLibIocTestContext

@TabooLibIocTest(
    DemoRepo::class,
    DemoService::class,
    DemoFacade::class,
    targetLifeCycle = LifeCycle.ACTIVE,
    invokePostEnable = true
)
class TabooLibIocAnnotationTest {

    @IocAutowired
    lateinit var ctx: TabooLibIocTestContext

    @IocAutowired
    lateinit var facade: DemoFacade

    @Test
    fun testAutoBootstrapAndAutoInject() {
        assertTrue(TabooLib.isKotlinEnvironment())
        assertEquals(LifeCycle.ACTIVE, TabooLib.getCurrentLifeCycle())

        assertNotNull(facade)
        assertNotNull(facade.service)
        assertEquals("hello-annotation", facade.render("annotation"))
    }

    @Test
    fun testBootstrapWithoutPrimitiveDownload() {
        assertTrue(TabooLib.isKotlinEnvironment())
        assertNotNull(facade)
        assertEquals("hello-cache", facade.render("cache"))
    }
}

@Repository
class DemoRepo {

    fun prefix(): String {
        return "hello"
    }
}

@Service
class DemoService(private val repo: DemoRepo) {

    fun greet(name: String): String {
        return "${repo.prefix()}-$name"
    }
}

@Service
class DemoFacade {

    @Inject
    lateinit var service: DemoService

    fun render(name: String): String {
        return service.greet(name)
    }
}
