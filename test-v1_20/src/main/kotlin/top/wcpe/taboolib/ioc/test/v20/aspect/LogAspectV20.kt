package top.wcpe.taboolib.ioc.test.v20.aspect

import top.wcpe.taboolib.ioc.annotation.Around
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import java.util.concurrent.atomic.AtomicInteger

@Aspect
class LogAspectV20 {

    companion object {
        val callCount = AtomicInteger(0)
    }

    @Around("execution(GreetingService20.greet)")
    fun aroundGreet(invocation: MethodInvocation): Any? {
        callCount.incrementAndGet()
        return invocation.proceed()
    }
}
