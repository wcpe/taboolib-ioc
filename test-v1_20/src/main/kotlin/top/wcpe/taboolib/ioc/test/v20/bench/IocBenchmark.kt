package top.wcpe.taboolib.ioc.test.v20.bench

import com.sun.management.ThreadMXBean
import taboolib.common.platform.function.info
import top.wcpe.taboolib.ioc.bean.BeanContainer
import java.io.File
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

/**
 * IoC 容器基准 + 压力测试套件。
 *
 * 全部指标都测**容器对外 API 的稳态性能**（容器初始化完成之后）：
 *
 * | 阶段 | 测什么 |
 * |---|---|
 * | throughput | 单线程各 API 的 ops/s 与 ns/op（预热 + 取 3 轮最优） |
 * | concurrency | 1/2/4/8/16 线程下 `getBean` 的吞吐与加速比、并行效率 |
 * | latency | 单线程逐次 `getBean` 的延迟分位（p50/p90/p99/p99.9/max） |
 * | stress | 8 线程持续混合负载（getBean/containsBean/getBeansOfType），统计吞吐、错误数、GC 增量与负载下延迟分位 |
 * | memory | 压力前后（各连续 GC 三次）堆 / 非堆 / Metaspace / 已加载类 对比 |
 * | aop | 直接 new / 容器普通 Bean / 容器 JDK 代理 三条调用路径的 ns/op 对比，并给出切面命中次数 |
 *
 * 结果写成 JSON，便于脚本出图；同时在控制台打印摘要。
 *
 * 口径说明（写进 README 的诚实边界）：
 * - 用 `System.nanoTime()` 计时，**不含** JMH 的 fork/黑盒等严谨处理，绝对值仅供横向对比；
 * - 逐操作计时本身有约 20~30ns 开销，故"延迟分位"是**含测量开销**的上界；
 * - 防 JIT 消除：被调方法读 `@Volatile` 字段，累加结果写入 `Sink`（`@Volatile`）。
 */
object IocBenchmark {

    private const val CHEAP_WARMUP = 500_000
    private const val CHEAP_OPS = 2_000_000
    private const val ALLOC_WARMUP = 20_000
    private const val ALLOC_OPS = 100_000
    /** AOP 三路径对比用的迭代量（调用本身只有十几 ns，量大些才稳）。 */
    private const val AOP_WARMUP = 500_000
    private const val AOP_OPS = 2_000_000
    private const val ROUNDS = 3
    private val THREAD_STEPS = intArrayOf(1, 2, 4, 8, 16)
    /** 每线程操作数：必须足够大，否则线程启停/JIT 预热会主导结果（实测 25 万次仅 ~8ms，数据不可信）。 */
    private const val CONC_OPS_PER_THREAD = 4_000_000
    private const val CONC_ROUNDS = 2
    private const val STRESS_THREADS = 8
    private const val STRESS_MS = 5_000L
    private const val LATENCY_OPS = 200_000
    private const val LAT_SAMPLE_CAP = 300_000

    /** 防 JIT 把整段循环消掉。 */
    private object Sink {
        @Volatile
        var value: Long = 0
    }

    /** 跑完整套件并写结果文件；返回是否成功。 */
    fun run(scale: Double, out: File): Boolean {
        val t0 = System.currentTimeMillis()
        val cheapOps = (CHEAP_OPS * scale).toInt().coerceAtLeast(10_000)
        val cheapWarmup = (CHEAP_WARMUP * scale).toInt().coerceAtLeast(5_000)
        val allocOps = (ALLOC_OPS * scale).toInt().coerceAtLeast(2_000)
        val allocWarmup = (ALLOC_WARMUP * scale).toInt().coerceAtLeast(500)
        val concOps = (CONC_OPS_PER_THREAD * scale).toInt().coerceAtLeast(5_000)
        val stressMs = (STRESS_MS * scale).toLong().coerceAtLeast(500)
        val latencyOps = (LATENCY_OPS * scale).toInt().coerceAtLeast(5_000)

        val benchServiceType = BenchService::class.java
        val plainServiceType = PlainService::class.java
        val beanNames = BeanContainer.getBeanNames()
        if (beanNames.isEmpty()) {
            info("[IoC-Bench] 容器内没有 Bean，基准测试中止")
            return false
        }
        val anyName = beanNames.first()
        val beanCount = beanNames.size

        info("[IoC-Bench] 开始：beanCount=$beanCount, scale=$scale, out=${out.absolutePath}")

        // ---------- 1) 单线程吞吐 ----------
        val throughput = mutableListOf<ThroughputResult>()
        throughput += bench("getBean(Class)", cheapWarmup, cheapOps) {
            if (BeanContainer.getBean(benchServiceType) != null) 1L else 0L
        }
        throughput += bench("getBean(Class, name=$anyName)", cheapWarmup, cheapOps) {
            if (BeanContainer.getBean(benchServiceType, anyName) != null) 1L else 0L
        }
        throughput += bench("containsBean(name)", cheapWarmup, cheapOps) {
            if (BeanContainer.containsBean(anyName)) 1L else 0L
        }
        throughput += bench("getBean(未命中类型)", cheapWarmup, cheapOps) {
            if (BeanContainer.getBean(String::class.java) == null) 1L else 0L
        }
        throughput += bench("getBeansOfType(Class)", allocWarmup, allocOps) {
            BeanContainer.getBeansOfType(benchServiceType).size.toLong()
        }
        throughput += bench("getBeanNames()", allocWarmup, allocOps) {
            BeanContainer.getBeanNames().size.toLong()
        }
        throughput.forEach { info("[IoC-Bench]   ${it.name}: ${fmt(it.opsPerSec)} ops/s, ${fmt(it.nsPerOp)} ns/op") }

        // ---------- 1.5) 确定性仪器：每次调用分配多少字节 ----------
        // 计时仪器在 ns 级差异上不可信（同一份代码连跑多轮，未改动路径的 ns/op 也能飘 ±15%），
        // 所以另测「每次调用分配的字节数」：它由代码路径决定，不受线程调度与 GC 时机影响。
        // 用途是判定某个优化到底有没有真的省掉分配 —— 若 JIT 已用逃逸分析把临时对象消掉，
        // 那么「少建一个 ArrayList」这类改动就只停留在纸面上，真机不会有收益。
        val bytesPerOp = mutableListOf<Pair<String, Double>>()
        val allocProbe = (CHEAP_OPS / 4).coerceAtLeast(200_000)
        bytesPerOp += "getBean(Class)" to bytesPerOp(allocProbe) { if (BeanContainer.getBean(benchServiceType) != null) 1L else 0L }
        bytesPerOp += "containsBean(name)" to bytesPerOp(allocProbe) { if (BeanContainer.containsBean(anyName)) 1L else 0L }
        bytesPerOp += "getBeansOfType(Class)" to bytesPerOp(allocProbe) { BeanContainer.getBeansOfType(benchServiceType).size.toLong() }
        bytesPerOp += "getBeanNames()" to bytesPerOp(allocProbe) { BeanContainer.getBeanNames().size.toLong() }
        bytesPerOp.forEach { (name, b) -> info("[IoC-Bench]   [分配] $name: ${fmt(b)} B/op") }

        // ---------- 2) 并发扩展性 ----------
        // 预热一轮，避免首个配置被 JIT/类加载吃掉
        concurrency(CONC_OPS_PER_THREAD / 4, 2)
        concurrency(CONC_OPS_PER_THREAD / 4, 16)
        val concurrency = mutableListOf<ConcurrencyResult>()
        for (threads in THREAD_STEPS) {
            // 每个配置跑多轮取吞吐最高的一轮，压低线程启停/GC 抖动
            var best: ConcurrencyResult? = null
            repeat(CONC_ROUNDS) {
                val r = concurrency(concOps, threads)
                if (best == null || r.opsPerSec > best!!.opsPerSec) best = r
            }
            concurrency += best!!
        }
        val base = concurrency.first { it.threads == 1 }.opsPerSec
        concurrency.forEach {
            it.speedup = if (base > 0) it.opsPerSec / base else 0.0
            it.efficiency = if (it.threads > 0) it.speedup / it.threads else 0.0
            info(
                "[IoC-Bench]   线程=${it.threads}: ${fmt(it.opsPerSec)} ops/s, " +
                    "加速比=${fmt(it.speedup)}x, 并行效率=${fmt(it.efficiency * 100)}%"
            )
        }

        // ---------- 3) 单线程延迟分位 ----------
        val latency = latency("getBean(Class) 单线程", latencyOps, benchServiceType)

        // ---------- 4) 内存（压力前） ----------
        val memBefore = MemorySnapshot.capture()

        // ---------- 5) 压力测试 ----------
        val stress = stress(stressMs, anyName, benchServiceType)
        info(
            "[IoC-Bench]   压力: ${stress.threads} 线程 × ${stress.durationMs}ms, " +
                "总 ops=${stress.totalOps}, 吞吐=${fmt(stress.opsPerSec)} ops/s, 错误=${stress.errors}, " +
                "GC 次数增量=${stress.gcCountAfter - stress.gcCountBefore}"
        )

        // ---------- 6) 内存（压力后） ----------
        val memAfter = MemorySnapshot.capture()

        // ---------- 7) AOP 三条路径 ----------
        val aop = mutableListOf<AopResult>()
        val direct = BenchServiceImpl()
        val plainBean = BeanContainer.getBean(plainServiceType)
        val advisedBean = BeanContainer.getBean(benchServiceType)
        var advisedClassName = "unknown"
        if (plainBean != null) {
            aop += benchAop("直接 new（无容器无代理）", AOP_WARMUP, AOP_OPS) { direct.echo(it) }
        }
        if (plainBean != null) {
            aop += benchAop("容器普通 Bean（无切面）", AOP_WARMUP, AOP_OPS) { plainBean.echo(it) }
        }
        if (advisedBean != null) {
            advisedClassName = advisedBean.javaClass.name
            val hitsBefore = BenchAspect.hits.get()
            aop += benchAop("容器 Bean（JDK 动态代理 + @Around）", AOP_WARMUP, AOP_OPS) { advisedBean.echo(it) }
            val hitsAfter = BenchAspect.hits.get()
            info("[IoC-Bench]   切面命中次数（本次基准窗口内）=${hitsAfter - hitsBefore}, 代理类=$advisedClassName")
        }
        aop.forEach { info("[IoC-Bench]   ${it.label}: ${fmt(it.nsPerOp)} ns/op") }

        // ---------- 汇总 ----------
        val json = buildJson(
            scale = scale,
            beanCount = beanCount,
            anyName = anyName,
            throughput = throughput,
            concurrency = concurrency,
            latency = latency,
            stress = stress,
            memBefore = memBefore,
            memAfter = memAfter,
            aop = aop,
            advisedClassName = advisedClassName,
            elapsedMs = System.currentTimeMillis() - t0
        )
        return try {
            out.parentFile?.mkdirs()
            out.writeText(json, Charsets.UTF_8)
            info("[IoC-Bench] 完成，用时 ${(System.currentTimeMillis() - t0)}ms，结果已写入 ${out.absolutePath}")
            true
        } catch (e: Throwable) {
            info("[IoC-Bench] 写结果失败: ${e.message}")
            false
        }
    }

    // ------------------------------------------------------------------ 各阶段

    /**
     * 测量 [block] 每次调用在线程内**分配多少字节**（确定性指标，不受调度/GC 波动影响）。
     *
     * 先跑一段预热让 JIT 完成编译（否则会把解释执行期的分配也算进来），再取两次
     * `getThreadAllocatedBytes` 的差值除以调用次数。
     */
    private fun bytesPerOp(ops: Int, block: (Int) -> Long): Double {
        val mx = ManagementFactory.getThreadMXBean() as ThreadMXBean
        if (!mx.isThreadAllocatedMemorySupported) return -1.0

        var acc = 0L
        for (i in 0 until ops) acc += block(i)          // 预热，让 JIT 有机会做逃逸分析
        val threadId = Thread.currentThread().id
        val before = mx.getThreadAllocatedBytes(threadId)
        for (i in 0 until ops) acc += block(i)
        val after = mx.getThreadAllocatedBytes(threadId)
        Sink.value = acc
        return (after - before).toDouble() / ops
    }

    private fun bench(name: String, warmup: Int, ops: Int, block: (Int) -> Long): ThroughputResult {
        var acc = 0L
        for (i in 0 until warmup) acc += block(i)
        var best = Long.MAX_VALUE
        for (r in 0 until ROUNDS) {
            val t0 = System.nanoTime()
            for (i in 0 until ops) acc += block(i)
            val dt = System.nanoTime() - t0
            if (dt < best) best = dt
        }
        Sink.value = acc
        return ThroughputResult(name, ops.toLong(), best, ROUNDS)
    }

    private fun benchAop(label: String, warmup: Int, ops: Int, block: (Int) -> Int): AopResult {
        val hitsBefore = BenchAspect.hits.get()
        var acc = 0L
        for (i in 0 until warmup) acc += block(i)
        var best = Long.MAX_VALUE
        for (r in 0 until ROUNDS) {
            val t0 = System.nanoTime()
            for (i in 0 until ops) acc += block(i)
            val dt = System.nanoTime() - t0
            if (dt < best) best = dt
        }
        Sink.value = acc
        return AopResult(label, ops.toLong(), best, (BenchAspect.hits.get() - hitsBefore).toLong())
    }

    private fun concurrency(opsPerThread: Int, threads: Int): ConcurrencyResult {
        val latch = CountDownLatch(1)
        val errors = AtomicLong(0)
        val workers = (0 until threads).map { idx ->
            Thread({
                try {
                    latch.await()
                    var acc = 0L
                    for (i in 0 until opsPerThread) {
                        if (BeanContainer.getBean(BenchService::class.java) != null) acc++
                    }
                    Sink.value = acc
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                }
            }, "ioc-bench-conc-$idx").apply { isDaemon = true }
        }
        workers.forEach { it.start() }
        val t0 = System.nanoTime()
        latch.countDown()
        workers.forEach { it.join() }
        val dt = System.nanoTime() - t0
        return ConcurrencyResult(threads, opsPerThread.toLong() * threads, dt, errors.get())
    }

    private fun latency(label: String, ops: Int, type: Class<*>): LatencyResult {
        // 先预热，再逐次计时
        var acc = 0L
        for (i in 0 until 50_000) {
            if (BeanContainer.getBean(type) != null) acc++
        }
        val samples = LongArray(ops)
        for (i in 0 until ops) {
            val t0 = System.nanoTime()
            val bean = BeanContainer.getBean(type)
            val t1 = System.nanoTime()
            samples[i] = t1 - t0
            if (bean != null) acc++
        }
        Sink.value = acc
        val sorted = samples.clone()
        sorted.sort()
        val p = Stats.percentiles(sorted)
        val r = LatencyResult(label, ops, p[0], p[1], p[2], p[3], p[4], Stats.mean(samples))
        info(
            "[IoC-Bench]   ${label}: p50=${fmt(r.p50.toDouble())}ns, p90=${fmt(r.p90.toDouble())}ns, " +
                "p99=${fmt(r.p99.toDouble())}ns, p99.9=${fmt(r.p999.toDouble())}ns, max=${r.max}ns"
        )
        return r
    }

    private fun stress(durationMs: Long, name: String, type: Class<*>): StressResult {
        val gcBefore = gcCount()
        val gcTimeBefore = gcTimeMs()
        val errors = AtomicLong(0)
        val totalOps = AtomicLong(0)
        val samples = arrayOfNulls<LongArray>(STRESS_THREADS)
        val counts = IntArray(STRESS_THREADS)
        for (t in 0 until STRESS_THREADS) samples[t] = LongArray(LAT_SAMPLE_CAP)
        val latch = CountDownLatch(1)
        val workers = (0 until STRESS_THREADS).map { idx ->
            Thread({
                val local = samples[idx]!!
                var n = 0
                var ops = 0L
                var errs = 0L
                var i = 0
                try {
                    latch.await()
                    val deadline = System.nanoTime() + durationMs * 1_000_000L
                    while (System.nanoTime() < deadline) {
                        val t0 = System.nanoTime()
                        try {
                            when (i % 10) {
                                in 0..6 -> if (BeanContainer.getBean(type) == null) errs++
                                7, 8 -> if (!BeanContainer.containsBean(name)) errs++
                                else -> if (BeanContainer.getBeansOfType(type).isEmpty()) errs++
                            }
                        } catch (e: Throwable) {
                            errs++
                        }
                        val t1 = System.nanoTime()
                        if (n < local.size) local[n++] = t1 - t0
                        ops++
                        i++
                    }
                } catch (e: Throwable) {
                    errs++
                }
                errors.addAndGet(errs)
                totalOps.addAndGet(ops)
                counts[idx] = n
                Sink.value = n.toLong()
            }, "ioc-bench-stress-$idx").apply { isDaemon = true; priority = Thread.NORM_PRIORITY }
        }
        workers.forEach { it.start() }
        latch.countDown()
        workers.forEach { it.join() }
        val gcAfter = gcCount()
        val gcTimeAfter = gcTimeMs()

        // 合并采样（只取每个线程真正写入的前缀）
        var size = 0
        for (t in 0 until STRESS_THREADS) size += counts[t]
        val merged = LongArray(size)
        var pos = 0
        for (t in 0 until STRESS_THREADS) {
            val s = samples[t] ?: continue
            System.arraycopy(s, 0, merged, pos, counts[t])
            pos += counts[t]
        }
        merged.sort()
        val p = Stats.percentiles(merged)
        val lat = LatencyResult("压力下混合负载（$STRESS_THREADS 线程）", merged.size, p[0], p[1], p[2], p[3], p[4], Stats.mean(merged))
        info(
            "[IoC-Bench]   压力下延迟: p50=${fmt(lat.p50.toDouble())}ns, p99=${fmt(lat.p99.toDouble())}ns, " +
                "p99.9=${fmt(lat.p999.toDouble())}ns, max=${lat.max}ns"
        )
        // 把压力下的延迟分位塞进 JSON 的 latency 段
        lastStressLatency = lat
        return StressResult(
            STRESS_THREADS, durationMs, totalOps.get(), errors.get(),
            gcBefore, gcAfter, gcTimeBefore, gcTimeAfter
        )
    }

    @Volatile
    private var lastStressLatency: LatencyResult? = null

    private fun gcCount(): Long =
        java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionCount.coerceAtLeast(0) }

    private fun gcTimeMs(): Long =
        java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0) }

    // ------------------------------------------------------------------ JSON

    private fun buildJson(
        scale: Double,
        beanCount: Int,
        anyName: String,
        throughput: List<ThroughputResult>,
        concurrency: List<ConcurrencyResult>,
        latency: LatencyResult,
        stress: StressResult,
        memBefore: MemorySnapshot,
        memAfter: MemorySnapshot,
        aop: List<AopResult>,
        advisedClassName: String,
        elapsedMs: Long
    ): String {
        val sb = StringBuilder(4096)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val stressLat = lastStressLatency
        sb.append("{\n")
        sb.append("  \"meta\": {")
        sb.append("\"timestamp\":\"${Json.escape(ts)}\",")
        sb.append("\"pluginVersion\":\"${Json.escape(pluginVersion())}\",")
        sb.append("\"javaVersion\":\"${Json.escape(System.getProperty("java.version") ?: "?")}\",")
        sb.append("\"javaVendor\":\"${Json.escape(System.getProperty("java.vendor") ?: "?")}\",")
        sb.append("\"os\":\"${Json.escape(System.getProperty("os.name") ?: "?")}\",")
        sb.append("\"osArch\":\"${Json.escape(System.getProperty("os.arch") ?: "?")}\",")
        sb.append("\"availableProcessors\":${Runtime.getRuntime().availableProcessors()},")
        sb.append("\"beanCount\":$beanCount,")
        sb.append("\"sampleBeanName\":\"${Json.escape(anyName)}\",")
        sb.append("\"iocPackage\":\"${Json.escape(BeanContainer::class.java.name)}\",")
        sb.append("\"advisedProxyClass\":\"${Json.escape(advisedClassName)}\",")
        sb.append("\"scale\":${Json.num(scale)},")
        sb.append("\"elapsedMs\":$elapsedMs")
        sb.append("},\n")

        sb.append("  \"throughput\": [\n")
        throughput.forEachIndexed { i, r ->
            sb.append("    ").append(r.toJson())
            if (i < throughput.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ],\n")

        sb.append("  \"concurrency\": [\n")
        concurrency.forEachIndexed { i, r ->
            sb.append("    ").append(r.toJson())
            if (i < concurrency.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ],\n")

        sb.append("  \"latency\": [")
        sb.append(latency.toJson())
        if (stressLat != null) sb.append(',').append(stressLat.toJson())
        sb.append("],\n")

        sb.append("  \"stress\": ").append(stress.toJson()).append(",\n")

        sb.append("  \"memory\": {")
        sb.append(memBefore.toJson("before")).append(',')
        sb.append(memAfter.toJson("after"))
        sb.append("},\n")

        sb.append("  \"aop\": [\n")
        aop.forEachIndexed { i, r ->
            sb.append("    ").append(r.toJson())
            if (i < aop.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun pluginVersion(): String {
        return try {
            val stream = IocBenchmark::class.java.classLoader.getResourceAsStream("plugin.yml") ?: return "unknown"
            val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
            Regex("(?m)^version:\\s*[\"']?([^\"'\\s]+)").find(text)?.groupValues?.get(1) ?: "unknown"
        } catch (e: Throwable) {
            "unknown"
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.ROOT, "%,.2f", v)
}
