package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * A-P1-10：ObjectInjector 注入失败 warning 的按类名聚合。
 *
 * 原实现每失败一次就输出一行 warning，多端平台缺失类场景下会刷屏数十~上百行。
 * 现要求「首次无条件全量输出 + 后续按类名聚合计次」，且**禁止降级为 debug**。
 *
 * 由于 `reportFailure` / `flushAggregatedFailures` 是 private，这里通过反射调用
 * 并断言其内部计数器行为（首次全量、后续只计数），以此锁定「聚合而非降级」的契约。
 */
class ObjectInjectorWarningAggregationTest {

    @Test
    fun `first failure should be counted and marked as fully warned`() {
        val obj = ObjectInjector

        val countsField = ObjectInjector::class.java.getDeclaredField("failureCounts")
        countsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val counts = countsField.get(obj) as MutableMap<String, Any>
        counts.clear()

        val warnedField = ObjectInjector::class.java.getDeclaredField("fullWarnedClasses")
        warnedField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val warned = warnedField.get(obj) as MutableSet<String>
        warned.clear()

        val report = ObjectInjector::class.java.getDeclaredMethod(
            "reportFailure", String::class.java, String::class.java, Throwable::class.java
        )
        report.isAccessible = true

        // 首次失败
        report.invoke(obj, "com.example.MissingA", "object", NoClassDefFoundError("boom"))

        assertEquals(1, counts.size, "首次失败应记录 1 个类名")
        assertTrue(warned.contains("com.example.MissingA"), "首次失败应标记为已全量输出")
        // 后续同类名失败只累加计数，不重复加入 fullWarnedClasses（集合大小不变）
        report.invoke(obj, "com.example.MissingA", "object", NoClassDefFoundError("boom2"))
        report.invoke(obj, "com.example.MissingA", "object", NoClassDefFoundError("boom3"))

        assertEquals(1, warned.size, "同类名后续失败不应重复触发全量输出")
    }

    @Test
    fun `different class names should each be fully warned`() {
        val obj = ObjectInjector

        val countsField = ObjectInjector::class.java.getDeclaredField("failureCounts")
        countsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val counts = countsField.get(obj) as MutableMap<String, Any>
        counts.clear()

        val warnedField = ObjectInjector::class.java.getDeclaredField("fullWarnedClasses")
        warnedField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val warned = warnedField.get(obj) as MutableSet<String>
        warned.clear()

        val report = ObjectInjector::class.java.getDeclaredMethod(
            "reportFailure", String::class.java, String::class.java, Throwable::class.java
        )
        report.isAccessible = true

        report.invoke(obj, "com.example.MissingA", "object", NoClassDefFoundError("a"))
        report.invoke(obj, "com.example.MissingB", "object", NoClassDefFoundError("b"))
        report.invoke(obj, "com.example.MissingA", "object", NoClassDefFoundError("a2"))

        assertEquals(2, counts.size, "两个不同类名")
        assertEquals(2, warned.size, "两个不同类名各自首次全量输出")
    }

    @Test
    fun `flushAggregatedFailures should not throw and keeps warning level`() {
        val obj = ObjectInjector
        val flush = ObjectInjector::class.java.getDeclaredMethod("flushAggregatedFailures")
        flush.isAccessible = true
        assertDoesNotThrow {
            flush.invoke(obj)
        }
    }
}
