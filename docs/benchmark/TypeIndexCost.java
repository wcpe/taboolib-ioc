import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 隔离测量：BeanRegistry 类型索引「旧实现 vs 新实现」的单次成本。
 *
 * 为什么要另起一个离线测量，而不是直接用容器级 IocBenchmark：
 * 后者每轮只有 2,000,000 次调用（约 10 ms），一次 young GC 落在最优轮里就能把 ns/op 抬高一倍。
 * 实测同一份代码连跑三轮，**一行都没改的** containsBean 在 4.83 / 7.85 / 7.08 ns/op 之间跳。
 * 拿这种仪器去分辨个位数 ns 的差异是不可能的，所以这里把被改的那段操作单独拎出来测。
 *
 * 防 JIT 干扰的两点：
 * 1. 被测类型的来源是一个 volatile 字段 —— volatile 读不可被提升到循环外，因此调用不会被外提；
 * 2. 累积返回值.size() 并最终写入 volatile sink，避免死代码消除。
 * 这两项对 old / new 是同等的，不影响两者对比。
 *
 * 运行：java TypeIndexCost.java   （JDK 21，无第三方依赖）
 */
public class TypeIndexCost {

    static final class Def {
        final String name;
        final int order;
        final boolean primary;

        Def(String name, int order, boolean primary) {
            this.name = name;
            this.order = order;
            this.primary = primary;
        }
    }

    static final Comparator<Def> BY_ORDER = Comparator.comparingInt(d -> d.order);

    // ─────────── 旧实现：CopyOnWriteArrayList + 每次调用 toList() + sortedBy ───────────
    static final Map<Class<?>, CopyOnWriteArrayList<Def>> OLD = new ConcurrentHashMap<>();

    static List<Def> oldGetByType(Class<?> type) {
        CopyOnWriteArrayList<Def> list = OLD.get(type);
        if (list == null) return Collections.emptyList();
        ArrayList<Def> copy = new ArrayList<>(list);   // Kotlin toList()
        copy.sort(BY_ORDER);                            // Kotlin sortedBy {}
        return copy;
    }

    // ─────────── 新实现：桶内缓存不可变快照，读路径一次 volatile 读 ───────────
    static final class Bucket {
        final ArrayList<Def> defs = new ArrayList<>();
        volatile List<Def> cache;

        List<Def> sorted() {
            List<Def> c = cache;
            if (c != null) return c;
            synchronized (this) {
                c = cache;
                if (c != null) return c;
                ArrayList<Def> copy = new ArrayList<>(defs);
                copy.sort(BY_ORDER);
                c = Collections.unmodifiableList(copy);
                cache = c;
                return c;
            }
        }
    }

    static final Map<Class<?>, Bucket> NEW = new ConcurrentHashMap<>();

    static List<Def> newGetByType(Class<?> type) {
        Bucket b = NEW.get(type);
        return b == null ? Collections.emptyList() : b.sorted();
    }

    static final Class<?> SINGLE = TypeIndexCost.class;
    static final Class<?> MULTI = Def.class;

    /** 被测类型的来源：volatile 保证循环内的调用不会被 JIT 外提 */
    static volatile Class<?> probeField = SINGLE;

    static volatile Object sink;

    static void setup() {
        CopyOnWriteArrayList<Def> one = new CopyOnWriteArrayList<>();
        one.add(new Def("showcase20", Integer.MAX_VALUE, false));
        OLD.put(SINGLE, one);
        Bucket b1 = new Bucket();
        b1.defs.add(new Def("showcase20", Integer.MAX_VALUE, false));
        NEW.put(SINGLE, b1);

        CopyOnWriteArrayList<Def> three = new CopyOnWriteArrayList<>();
        three.add(new Def("c", 30, false));
        three.add(new Def("a", 10, false));
        three.add(new Def("b", 20, false));
        OLD.put(MULTI, three);
        Bucket b3 = new Bucket();
        b3.defs.add(new Def("c", 30, false));
        b3.defs.add(new Def("a", 10, false));
        b3.defs.add(new Def("b", 20, false));
        NEW.put(MULTI, b3);
    }

    static long timeOld(int ops) {
        long start = System.nanoTime();
        int acc = 0;
        for (int i = 0; i < ops; i++) acc += oldGetByType(probeField).size();
        long elapsed = System.nanoTime() - start;
        if (acc == Integer.MIN_VALUE) sink = null;
        return elapsed;
    }

    static long timeNew(int ops) {
        long start = System.nanoTime();
        int acc = 0;
        for (int i = 0; i < ops; i++) acc += newGetByType(probeField).size();
        long elapsed = System.nanoTime() - start;
        if (acc == Integer.MIN_VALUE) sink = null;
        return elapsed;
    }

    /** 只做一次 ConcurrentHashMap 查找 —— 读路径的理论下限 */
    static long timeMapOnly(int ops) {
        long start = System.nanoTime();
        int acc = 0;
        for (int i = 0; i < ops; i++) acc += OLD.get(probeField) == null ? 0 : 1;
        long elapsed = System.nanoTime() - start;
        if (acc == Integer.MIN_VALUE) sink = null;
        return elapsed;
    }

    /** 迭代结果列表（模拟 getBeansOfType 的 map）：检查不可变包装是否带来额外开销 */
    static long timeIterate(List<Def> list, int ops) {
        long start = System.nanoTime();
        int acc = 0;
        for (int i = 0; i < ops; i++) {
            for (Def d : list) acc += d.order;
        }
        long elapsed = System.nanoTime() - start;
        if (acc == Integer.MIN_VALUE) sink = null;
        return elapsed;
    }

    /** BeanScopes.normalize：getBean 路径上每次都会调用到 */
    static String normalize(String scope) {
        if (scope == null) return "singleton";
        String s = scope.trim();
        if (s.isEmpty()) return "singleton";
        return s.toLowerCase(Locale.ROOT);
    }

    static long timeNormalize(int ops) {
        long start = System.nanoTime();
        int acc = 0;
        for (int i = 0; i < ops; i++) acc += normalize("singleton").length();
        long elapsed = System.nanoTime() - start;
        if (acc == Integer.MIN_VALUE) sink = null;
        return elapsed;
    }

    static final List<Def> UNMOD = Collections.unmodifiableList(new ArrayList<>(List.of(
            new Def("a", 1, false), new Def("b", 2, false), new Def("c", 3, false))));
    static final List<Def> PLAIN = new ArrayList<>(List.of(
            new Def("a", 1, false), new Def("b", 2, false), new Def("c", 3, false)));

    // ─────────── 确定性仪器：数「每次调用分配多少字节」 ───────────
    // 计时仪器的噪声在 ±15%（见 README 说明），但分配次数是确定的，
    // 不受线程调度 / GC 时机影响，因此用它来判定「旧实现是不是真的每次都在建两个 ArrayList」。

    static com.sun.management.ThreadMXBean threadMx;

    static long allocatedBytes() {
        return threadMx.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    static double bytesPerCallOld(int ops) {
        for (int i = 0; i < 200_000; i++) sink = oldGetByType(probeField);   // 预热
        long before = allocatedBytes();
        int acc = 0;
        for (int i = 0; i < ops; i++) acc += oldGetByType(probeField).size();
        long after = allocatedBytes();
        if (acc == Integer.MIN_VALUE) sink = null;
        return (double) (after - before) / ops;
    }

    static double bytesPerCallNew(int ops) {
        for (int i = 0; i < 200_000; i++) sink = newGetByType(probeField);
        long before = allocatedBytes();
        int acc = 0;
        for (int i = 0; i < ops; i++) acc += newGetByType(probeField).size();
        long after = allocatedBytes();
        if (acc == Integer.MIN_VALUE) sink = null;
        return (double) (after - before) / ops;
    }

    public static void main(String[] args) throws Exception {
        setup();
        threadMx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();

        final int OPS = 20_000_000;
        final int ROUNDS = 5;
        final int WARMUP = 3_000_000;

        System.out.printf("每轮 %,d 次调用 x %d 轮，取最优；每轮开始前 System.gc()%n%n", OPS, ROUNDS);

        for (int i = 0; i < 3; i++) {
            timeOld(WARMUP); timeNew(WARMUP); timeMapOnly(WARMUP);
        }

        long bestOld1 = Long.MAX_VALUE, bestNew1 = Long.MAX_VALUE;
        long bestOld3 = Long.MAX_VALUE, bestNew3 = Long.MAX_VALUE;
        long bestMap = Long.MAX_VALUE;
        long bestIterUnmod = Long.MAX_VALUE, bestIterPlain = Long.MAX_VALUE;
        long bestNormalize = Long.MAX_VALUE;
        StringBuilder rawOld = new StringBuilder();
        StringBuilder rawNew = new StringBuilder();

        for (int r = 0; r < ROUNDS; r++) {
            System.gc();
            Thread.sleep(300);

            probeField = SINGLE;
            long o1 = timeOld(OPS);
            long n1 = timeNew(OPS);
            long m = timeMapOnly(OPS);

            probeField = MULTI;
            long o3 = timeOld(OPS);
            long n3 = timeNew(OPS);

            bestOld1 = Math.min(bestOld1, o1);
            bestNew1 = Math.min(bestNew1, n1);
            bestOld3 = Math.min(bestOld3, o3);
            bestNew3 = Math.min(bestNew3, n3);
            bestMap = Math.min(bestMap, m);

            bestIterUnmod = Math.min(bestIterUnmod, timeIterate(UNMOD, OPS));
            bestIterPlain = Math.min(bestIterPlain, timeIterate(PLAIN, OPS));
            bestNormalize = Math.min(bestNormalize, timeNormalize(OPS));

            rawOld.append(String.format("%.2f ", ns(o1, OPS)));
            rawNew.append(String.format("%.2f ", ns(n1, OPS)));

            System.out.printf("轮 %d: 1候选 old=%6.2f new=%6.2f   3候选 old=%6.2f new=%6.2f   (ns/op)%n",
                    r + 1, ns(o1, OPS), ns(n1, OPS), ns(o3, OPS), ns(n3, OPS));
        }

        System.out.println();
        System.out.printf("%-46s %9s %9s %9s%n", "形态", "旧 ns/op", "新 ns/op", "降幅");
        System.out.printf("%-46s %9.2f %9s %9s%n", "[下限] 仅 ConcurrentHashMap 查找", ns(bestMap, OPS), "-", "-");
        System.out.printf("%-46s %9.2f %9.2f %8.1f%%%n", "1 个候选: toList()+sortedBy → 缓存", ns(bestOld1, OPS), ns(bestNew1, OPS), cut(bestOld1, bestNew1));
        System.out.printf("%-46s %9.2f %9.2f %8.1f%%%n", "3 个候选: toList()+sortedBy → 缓存", ns(bestOld3, OPS), ns(bestNew3, OPS), cut(bestOld3, bestNew3));
        System.out.println();
        System.out.printf("%-46s %9.2f%n", "迭代普通 ArrayList（3 元素）", ns(bestIterPlain, OPS));
        System.out.printf("%-46s %9.2f%n", "迭代 unmodifiableList（3 元素）", ns(bestIterUnmod, OPS));
        System.out.printf("%-46s %9.2f%n", "BeanScopes.normalize(\"singleton\")", ns(bestNormalize, OPS));
        System.out.println();
        System.out.println("1 候选各轮原始值（观察波动）:");
        System.out.println("  旧: " + rawOld);
        System.out.println("  新: " + rawNew);

        System.out.println();
        System.out.println("== 确定性仪器：每次调用分配的字节数 ==");
        probeField = SINGLE;
        double bOld = bytesPerCallOld(5_000_000);
        double bNew = bytesPerCallNew(5_000_000);
        System.out.printf("  1 候选  旧实现 %.2f B/次   新实现 %.2f B/次%n", bOld, bNew);
        probeField = MULTI;
        double bOld3 = bytesPerCallOld(5_000_000);
        double bNew3 = bytesPerCallNew(5_000_000);
        System.out.printf("  3 候选  旧实现 %.2f B/次   新实现 %.2f B/次%n", bOld3, bNew3);
        System.out.println();
        System.out.println("说明：新实现应为 0 —— 说明读路径的 ArrayList 确实被 JIT 消除（或根本没建）；");
        System.out.println("     旧实现若明显 > 0，说明它每次都真的在分配，计时测到的差值不是假的。");
    }

    static double ns(long nanos, int ops) {
        return (double) nanos / ops;
    }

    static double cut(long before, long after) {
        return (before - after) * 100.0 / before;
    }
}
