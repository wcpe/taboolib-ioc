package top.wcpe.taboolib.ioc.test.v20.bench

import java.lang.management.ManagementFactory
import java.util.Locale

/**
 * 基准结果模型与 JSON 序列化。
 *
 * 刻意不引入任何序列化依赖（模块只有 kotlin-stdlib + paper-api），
 * 手写 JSON 输出，便于下游用 python/jq 直接画图。
 */
internal object Json {
    fun escape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    fun num(v: Double): String = if (v.isNaN() || v.isInfinite()) "null" else String.format(Locale.ROOT, "%.4f", v)
}

/** 单线程吞吐结果（ns/op 越小越好，ops/s 越大越好）。 */
internal class ThroughputResult(
    val name: String,
    val ops: Long,
    val nanos: Long,
    val rounds: Int
) {
    val opsPerSec: Double = if (nanos <= 0) 0.0 else ops * 1_000_000_000.0 / nanos
    val nsPerOp: Double = if (ops <= 0) 0.0 else nanos.toDouble() / ops

    fun toJson(): String =
        """{"name":"${Json.escape(name)}","ops":$ops,"bestNanos":$nanos,"rounds":$rounds,""" +
            """"opsPerSec":${Json.num(opsPerSec)},"nsPerOp":${Json.num(nsPerOp)}}"""
}

/** 并发扩展性结果。 */
internal class ConcurrencyResult(
    val threads: Int,
    val ops: Long,
    val nanos: Long,
    val errors: Long
) {
    val opsPerSec: Double = if (nanos <= 0) 0.0 else ops * 1_000_000_000.0 / nanos
    var speedup: Double = 0.0
    var efficiency: Double = 0.0

    fun toJson(): String =
        """{"threads":$threads,"ops":$ops,"nanos":$nanos,"errors":$errors,""" +
            """"opsPerSec":${Json.num(opsPerSec)},"speedup":${Json.num(speedup)},""" +
            """"efficiency":${Json.num(efficiency)}}"""
}

/** 延迟分位（ns）。 */
internal class LatencyResult(
    val name: String,
    val samples: Int,
    val p50: Long,
    val p90: Long,
    val p99: Long,
    val p999: Long,
    val max: Long,
    val mean: Double
) {
    fun toJson(): String =
        """{"name":"${Json.escape(name)}","samples":$samples,"p50":$p50,"p90":$p90,""" +
            """"p99":$p99,"p999":$p999,"max":$max,"meanNs":${Json.num(mean)}}"""
}

/** 压力测试结果。 */
internal class StressResult(
    val threads: Int,
    val durationMs: Long,
    val totalOps: Long,
    val errors: Long,
    val gcCountBefore: Long,
    val gcCountAfter: Long,
    val gcTimeMsBefore: Long,
    val gcTimeMsAfter: Long
) {
    val opsPerSec: Double = if (durationMs <= 0) 0.0 else totalOps * 1000.0 / durationMs

    fun toJson(): String =
        """{"threads":$threads,"durationMs":$durationMs,"totalOps":$totalOps,"errors":$errors,""" +
            """"opsPerSec":${Json.num(opsPerSec)},"gcCountDelta":${gcCountAfter - gcCountBefore},""" +
            """"gcTimeMsDelta":${gcTimeMsAfter - gcTimeMsBefore}}"""
}

/** 内存快照（GC 后）。 */
internal class MemorySnapshot(
    val heapUsedBytes: Long,
    val nonHeapUsedBytes: Long,
    val metaspaceUsedBytes: Long,
    val loadedClasses: Long
) {
    companion object {
        fun capture(): MemorySnapshot {
            // 连续 GC 三次再读，尽量拿到活对象量
            repeat(3) {
                System.gc()
                Thread.sleep(60)
            }
            val memory = ManagementFactory.getMemoryMXBean()
            val heap = memory.heapMemoryUsage.used
            val nonHeap = memory.nonHeapMemoryUsage.used
            val metaPool = ManagementFactory.getMemoryPoolMXBeans()
                .firstOrNull { it.name.contains("Metaspace", ignoreCase = true) }
            val metaspace = metaPool?.usage?.used ?: 0L
            val classes = ManagementFactory.getClassLoadingMXBean().loadedClassCount.toLong()
            return MemorySnapshot(heap, nonHeap, metaspace, classes)
        }
    }

    fun toJson(prefix: String): String =
        """"${prefix}HeapUsed":$heapUsedBytes,"${prefix}NonHeapUsed":$nonHeapUsedBytes,""" +
            """"${prefix}MetaspaceUsed":$metaspaceUsedBytes,"${prefix}LoadedClasses":$loadedClasses"""
}

/** 一段 AOP 调用的开销。 */
internal class AopResult(
    val label: String,
    val ops: Long,
    val nanos: Long,
    val aspectHits: Long
) {
    val nsPerOp: Double = if (ops <= 0) 0.0 else nanos.toDouble() / ops

    fun toJson(): String =
        """{"label":"${Json.escape(label)}","ops":$ops,"nanos":$nanos,""" +
            """"nsPerOp":${Json.num(nsPerOp)},"aspectHits":$aspectHits}"""
}

/** 统计工具。 */
internal object Stats {

    /** 对已排序数组取分位。 */
    fun percentiles(sorted: LongArray): LongArray {
        if (sorted.isEmpty()) return longArrayOf(0, 0, 0, 0, 0)
        fun at(p: Double): Long {
            val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }
        return longArrayOf(at(0.50), at(0.90), at(0.99), at(0.999), sorted[sorted.size - 1])
    }

    fun mean(values: LongArray): Double {
        if (values.isEmpty()) return 0.0
        var sum = 0.0
        for (v in values) sum += v
        return sum / values.size
    }
}
