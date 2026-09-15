import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AOP 调用开销拆解微基准（零第三方依赖，JDK 21）。
 *
 * 关键设计：**入参随迭代变化**（op.apply(i)）。否则常量输入会让 JIT 把
 * 匹配/建链/参数拷贝整段提升到循环外，测出「纯分发比直接调用还快」这种假数据。
 *
 * 运行（JDK 11+ 单文件启动，无需编译）：
 *     java docs/benchmark/AopCost.java
 *
 * 注意：本文件是**干净 JVM 里的同形态拆解**，用于定位成本来源与可达下界；
 * 真实服务端里同实现的端到端成本见 README「基准与压力测试」章节（111.6 ns/op）。
 */
public class AopCost {

    static final int WARM = 3_000_000;
    static final int OPS = 5_000_000;
    static final int ROUNDS = 3;
    static volatile long SINK;
    static volatile Object[] ESCAPE_BOX = new Object[1];
    static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    interface Op {
        long apply(int i) throws Throwable;
    }

    public interface Svc {
        int echo(int v);
    }

    public static class Impl implements Svc {
        volatile int seed = 1;

        public int echo(int v) {
            return v + seed;
        }
    }

    /** 模拟 @Aspect：@Around 接收 MethodInvocation；计数器用 CAS（与真实实现一致）。 */
    public static class Aspect {
        final AtomicInteger hits = new AtomicInteger();

        public Object around(Inv inv) {
            hits.incrementAndGet();
            return inv.proceed();
        }
    }

    public static class Inv {
        final Object target;
        final Method method;
        final Object[] args;

        public Inv(Object target, Method method, Object[] args) {
            this.target = target;
            this.method = method;
            this.args = args;
        }

        public Object proceed() {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** B) 手写子类代理 —— 等价于 CGLIB / 编译期字节码织入生成的形态（无反射、无每次分配）。 */
    public static class SubProxy extends Impl {
        private final Impl target;
        private final Aspect aspect;

        public SubProxy(Impl target, Aspect aspect) {
            this.target = target;
            this.aspect = aspect;
        }

        @Override
        public int echo(int v) {
            aspect.hits.incrementAndGet();
            return target.echo(v);
        }
    }

    static class SimpleAdvice {
        final String methodName;
        final Object inst;
        final Method adviceMethod;

        SimpleAdvice(String methodName, Object inst, Method adviceMethod) {
            this.methodName = methodName;
            this.inst = inst;
            this.adviceMethod = adviceMethod;
        }
    }

    /** C) 纯分发：handler 只回传入参，量 JDK 代理本身。 */
    static class RawHandler implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args) {
            return args[0];
        }
    }

    /** D) JDK 代理 + Method.invoke 转发。 */
    static class ReflectionHandler implements InvocationHandler {
        private final Impl target;

        ReflectionHandler(Impl target) {
            this.target = target;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return method.invoke(target, args);
        }
    }

    /** E) JDK 代理 + 缓存的 MethodHandle 转发（含返回值装箱）。 */
    static class MethodHandleHandler implements InvocationHandler {
        private final Impl target;
        private final MethodHandle targetMh;

        MethodHandleHandler(Impl target) throws Throwable {
            this.target = target;
            this.targetMh = LOOKUP.findVirtual(Impl.class, "echo", MethodType.methodType(int.class, int.class));
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            int r = (int) targetMh.invoke(target, (int) (Integer) args[0]);
            return r;
        }
    }

    /** F) 当前实现形态：每次调用重做切点匹配 + 建 5 个子链 + 参数拷贝 + 2 次反射。 */
    static class CurrentStyleHandler implements InvocationHandler {
        private final Impl target;
        private final List<SimpleAdvice> advisors = new ArrayList<>();

        CurrentStyleHandler(Impl target, Aspect aspect) throws Throwable {
            this.target = target;
            advisors.add(new SimpleAdvice("echo", aspect, Aspect.class.getMethod("around", Inv.class)));
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            List<SimpleAdvice> matched = new ArrayList<>(1);
            for (SimpleAdvice ad : advisors) {
                if (ad.methodName.equals(method.getName())) {
                    matched.add(ad);
                }
            }
            if (matched.isEmpty()) {
                return method.invoke(target, args);
            }
            List<SimpleAdvice> before = new ArrayList<>(0);
            List<SimpleAdvice> after = new ArrayList<>(0);
            List<SimpleAdvice> around = new ArrayList<>(matched.size());
            List<SimpleAdvice> afterReturning = new ArrayList<>(0);
            List<SimpleAdvice> afterThrowing = new ArrayList<>(0);
            for (SimpleAdvice ad : matched) {
                around.add(ad);
            }
            Object[] cargs = (args == null) ? new Object[0] : Arrays.copyOf(args, args.length);
            Inv inv = new Inv(target, method, cargs);
            return around.get(0).adviceMethod.invoke(around.get(0).inst, inv);
        }
    }

    /** 零分配复用的 Invocation（显式绑定句柄）。 */
    static class MutableInv extends Inv {
        int value;
        MethodHandle handle;
        Object holder;

        MutableInv() {
            super(null, null, null);
        }

        void bind(MethodHandle handle, Object holder, int value) {
            this.handle = handle;
            this.holder = holder;
            this.value = value;
        }

        @Override
        public Object proceed() {
            try {
                return (int) handle.invoke(holder, value);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    /** G) 优化形态：匹配结果按 Method 缓存 + 零分配（ThreadLocal 复用 Invocation）+ MethodHandle。 */
    static class OptimizedHandler implements InvocationHandler {
        private final Impl target;
        private final Aspect aspect;
        private final MethodHandle targetMh;
        private final MethodHandle adviceMh;
        private final Map<Method, Boolean> matchedCache = new ConcurrentHashMap<>();
        private final ThreadLocal<MutableInv> invHolder = ThreadLocal.withInitial(MutableInv::new);

        OptimizedHandler(Impl target, Aspect aspect) throws Throwable {
            this.target = target;
            this.aspect = aspect;
            this.targetMh = LOOKUP.findVirtual(Impl.class, "echo", MethodType.methodType(int.class, int.class));
            this.adviceMh = LOOKUP.unreflect(Aspect.class.getMethod("around", Inv.class));
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (matchedCache.get(method) == null) {
                matchedCache.put(method, Boolean.TRUE);
            }
            MutableInv inv = invHolder.get();
            inv.bind(targetMh, target, (int) (Integer) args[0]);
            return adviceMh.invoke(aspect, inv);
        }
    }

    static long bench(String name, Op op) throws Throwable {
        long sink = 0;
        for (int i = 0; i < WARM; i++) {
            sink += op.apply(i);
        }
        long best = Long.MAX_VALUE;
        for (int r = 0; r < ROUNDS; r++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < OPS; i++) {
                sink += op.apply(i);
            }
            long dt = System.nanoTime() - t0;
            if (dt < best) {
                best = dt;
            }
        }
        SINK = sink;
        System.out.printf("  %-44s %9.2f ns/op%n", name, (double) best / OPS);
        return best;
    }

    public static void main(String[] args) throws Throwable {
        System.out.println("== AOP 调用开销拆解（JDK " + System.getProperty("java.version")
                + "，warmup=" + WARM + "，ops=" + OPS + "，best-of-" + ROUNDS + "，入参随迭代变化）==");
        Impl impl = new Impl();
        Aspect aspect = new Aspect();
        Svc rawProxy = (Svc) Proxy.newProxyInstance(AopCost.class.getClassLoader(), new Class<?>[]{Svc.class}, new RawHandler());
        Svc reflProxy = (Svc) Proxy.newProxyInstance(AopCost.class.getClassLoader(), new Class<?>[]{Svc.class}, new ReflectionHandler(impl));
        Svc mhProxy = (Svc) Proxy.newProxyInstance(AopCost.class.getClassLoader(), new Class<?>[]{Svc.class}, new MethodHandleHandler(impl));
        Svc currentProxy = (Svc) Proxy.newProxyInstance(AopCost.class.getClassLoader(), new Class<?>[]{Svc.class}, new CurrentStyleHandler(impl, aspect));
        Svc optProxy = (Svc) Proxy.newProxyInstance(AopCost.class.getClassLoader(), new Class<?>[]{Svc.class}, new OptimizedHandler(impl, aspect));
        SubProxy subProxy = new SubProxy(impl, aspect);

        List<Long> r = new ArrayList<>();
        r.add(bench("A 直接调用（基线）", i -> impl.echo(i)));
        r.add(bench("B 子类代理（等价 CGLIB / 字节码织入）", i -> subProxy.echo(i)));
        r.add(bench("C JDK 代理 + 纯分发", i -> rawProxy.echo(i)));
        r.add(bench("D JDK 代理 + Method.invoke 转发", i -> reflProxy.echo(i)));
        r.add(bench("E JDK 代理 + MethodHandle 转发", i -> mhProxy.echo(i)));
        r.add(bench("F 当前实现形态（匹配+建链+拷贝+2反射）", i -> currentProxy.echo(i)));
        r.add(bench("G 优化形态（缓存+零分配+MethodHandle）", i -> optProxy.echo(i)));

        Class<?> targetClass = Impl.class;
        String pattern = "Impl";
        r.add(bench("H 仅切点匹配本身（name/simpleName 比较 ×2）", i -> {
            boolean m = targetClass.getName().equals(pattern) || targetClass.getSimpleName().equals(pattern);
            return m ? 1 : 0;
        }));
        r.add(bench("I 仅每次调用的分配（5 子链 + 拷贝 + Invocation）", i -> {
            List<Object> a1 = new ArrayList<>(1);
            List<Object> a2 = new ArrayList<>(0);
            List<Object> a3 = new ArrayList<>(1);
            List<Object> a4 = new ArrayList<>(0);
            List<Object> a5 = new ArrayList<>(0);
            Object[] copy = Arrays.copyOf(new Object[]{i}, 1);
            Inv inv = new Inv(impl, null, copy);
            a3.add(inv);
            ESCAPE_BOX[0] = inv;
            return a1.size() + a2.size() + a3.size() + a4.size() + a5.size();
        }));

        System.out.println();
        StringBuilder rel = new StringBuilder();
        String[] names = {"B", "C", "D", "E", "F", "G", "H", "I"};
        for (int k = 1; k < r.size(); k++) {
            rel.append(String.format("%s=%.1fx  ", names[k - 1], r.get(k) / (double) r.get(0)));
        }
        System.out.println("  相对基线倍数：" + rel);
        System.out.printf("  切面命中数：%d（证明通知确实被执行）%n", aspect.hits.get());
        System.out.println("  SINK=" + SINK);
    }
}
