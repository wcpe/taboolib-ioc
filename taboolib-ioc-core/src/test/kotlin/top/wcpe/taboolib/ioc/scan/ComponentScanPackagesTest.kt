package top.wcpe.taboolib.ioc.scan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.ComponentScan

class ComponentScanPackagesTest {

    @Test
    fun `component scan should default to declaring package`() {
        val packages = ComponentScanPackages.resolve(listOf(DefaultScanConfig::class.java))

        assertEquals(setOf("top.wcpe.taboolib.ioc.scan"), packages)
        assertTrue(ComponentScanPackages.matches(DefaultScanConfig::class.java, packages))
        assertFalse(ComponentScanPackages.matches(String::class.java, packages))
    }

    @Test
    fun `component scan should merge explicit packages and base package classes`() {
        val packages = ComponentScanPackages.resolve(listOf(ExplicitScanConfig::class.java))

        assertEquals(
            setOf("java.util", "top.wcpe.taboolib.ioc.annotation"),
            packages
        )
        assertTrue(ComponentScanPackages.matches(java.util.ArrayList::class.java, packages))
        assertTrue(ComponentScanPackages.matches(Component::class.java, packages))
        assertFalse(ComponentScanPackages.matches(String::class.java, packages))
    }
}

@ComponentScan
private class DefaultScanConfig

@ComponentScan(
    value = ["java.util.*"],
    basePackageClasses = [Component::class]
)
private class ExplicitScanConfig
