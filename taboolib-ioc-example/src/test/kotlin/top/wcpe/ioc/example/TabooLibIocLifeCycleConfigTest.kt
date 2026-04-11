package top.wcpe.ioc.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import taboolib.common.LifeCycle
import taboolib.common.TabooLib
import top.wcpe.taboolib.ioc.test.TabooLibIocTest

@TabooLibIocTest(
    DemoRepo::class,
    DemoService::class,
    targetLifeCycle = LifeCycle.ENABLE,
    invokePostEnable = false
)
class TabooLibIocLifeCycleConfigTest {

    @Test
    fun testLifeCycleConfiguredToEnable() {
        assertTrue(TabooLib.isKotlinEnvironment())
        assertEquals(LifeCycle.ENABLE, TabooLib.getCurrentLifeCycle())
    }
}
