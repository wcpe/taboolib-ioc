package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import taboolib.common.LifeCycle
import taboolib.common.TabooLib
import top.wcpe.taboolib.ioc.annotation.Service

@TabooLibIocTest(
    DemoBootstrapService12::class,
    targetLifeCycle = LifeCycle.ACTIVE,
    invokePostEnable = true,
    observable = true
)
class TabooLibFullBootstrapTest {

    @IocAutowired
    lateinit var service: DemoBootstrapService12

    @Test
    fun `should bootstrap lifecycle and auto inject bean`() {
        assertEquals(LifeCycle.ACTIVE, TabooLib.getCurrentLifeCycle())
        assertNotNull(service)
        assertEquals("bootstrap-12", service.value())
    }

    @Test
    fun `should prepare primitive dependencies`() {
        assertTrue(TabooLib.isKotlinEnvironment())
        assertEquals("bootstrap-12", service.value())
    }
}

@Service
class DemoBootstrapService12 {

    fun value(): String {
        return "bootstrap-12"
    }
}