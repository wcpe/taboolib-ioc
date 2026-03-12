package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ValueResolverTest {

    @AfterEach
    fun cleanup() {
        ValueResolver.clearProperties()
        System.clearProperty("vr.test.key")
        System.clearProperty("vr.test.int")
        System.clearProperty("vr.test.bool")
        System.clearProperty("vr.test.long")
        System.clearProperty("vr.test.double")
        System.clearProperty("vr.test.float")
        System.clearProperty("vr.test.short")
        System.clearProperty("vr.test.byte")
    }

    @Test
    fun `should resolve from system property`() {
        System.setProperty("vr.test.key", "sys-value")
        assertEquals("sys-value", ValueResolver.resolve("\${vr.test.key}", String::class.java))
    }

    @Test
    fun `should resolve from loaded property`() {
        ValueResolver.setProperty("vr.test.key", "loaded-value")
        assertEquals("loaded-value", ValueResolver.resolve("\${vr.test.key}", String::class.java))
    }

    @Test
    fun `loaded property should take priority over system property`() {
        System.setProperty("vr.test.key", "sys")
        ValueResolver.setProperty("vr.test.key", "loaded")
        assertEquals("loaded", ValueResolver.resolve("\${vr.test.key}", String::class.java))
    }

    @Test
    fun `should use default value when property missing`() {
        assertEquals("fallback", ValueResolver.resolve("\${missing.key:fallback}", String::class.java))
    }

    @Test
    fun `should return null when property missing and no default`() {
        assertNull(ValueResolver.resolve("\${missing.key}", String::class.java))
    }

    @Test
    fun `should return literal text as-is`() {
        assertEquals("hello world", ValueResolver.resolve("hello world", String::class.java))
    }

    @Test
    fun `should convert to Int`() {
        System.setProperty("vr.test.int", "42")
        assertEquals(42, ValueResolver.resolve("\${vr.test.int}", Int::class.java))
    }

    @Test
    fun `should convert to Long`() {
        System.setProperty("vr.test.long", "123456789")
        assertEquals(123456789L, ValueResolver.resolve("\${vr.test.long}", Long::class.java))
    }

    @Test
    fun `should convert to Double`() {
        System.setProperty("vr.test.double", "3.14")
        assertEquals(3.14, ValueResolver.resolve("\${vr.test.double}", Double::class.java))
    }

    @Test
    fun `should convert to Float`() {
        System.setProperty("vr.test.float", "2.5")
        assertEquals(2.5f, ValueResolver.resolve("\${vr.test.float}", Float::class.java))
    }

    @Test
    fun `should convert to Boolean`() {
        System.setProperty("vr.test.bool", "true")
        assertEquals(true, ValueResolver.resolve("\${vr.test.bool}", Boolean::class.java))
    }

    @Test
    fun `should convert to Short`() {
        System.setProperty("vr.test.short", "100")
        assertEquals(100.toShort(), ValueResolver.resolve("\${vr.test.short}", Short::class.java))
    }

    @Test
    fun `should convert to Byte`() {
        System.setProperty("vr.test.byte", "127")
        assertEquals(127.toByte(), ValueResolver.resolve("\${vr.test.byte}", Byte::class.java))
    }

    @Test
    fun `should return null for invalid number format`() {
        System.setProperty("vr.test.int", "not-a-number")
        assertNull(ValueResolver.resolve("\${vr.test.int}", Int::class.java))
    }

    @Test
    fun `should return null for unsupported type`() {
        System.setProperty("vr.test.key", "value")
        assertNull(ValueResolver.resolve("\${vr.test.key}", List::class.java))
    }

    @Test
    fun `should load properties from classpath`() {
        ValueResolver.loadProperties("test-config.properties")
        assertEquals("hello-from-file", ValueResolver.resolve("\${app.greeting}", String::class.java))
        assertEquals("1.0.0", ValueResolver.resolve("\${app.version}", String::class.java))
    }

    @Test
    fun `clearProperties should remove all loaded properties`() {
        ValueResolver.setProperty("temp.key", "temp-value")
        assertEquals("temp-value", ValueResolver.resolve("\${temp.key}", String::class.java))
        ValueResolver.clearProperties()
        assertNull(ValueResolver.resolve("\${temp.key}", String::class.java))
    }
}
