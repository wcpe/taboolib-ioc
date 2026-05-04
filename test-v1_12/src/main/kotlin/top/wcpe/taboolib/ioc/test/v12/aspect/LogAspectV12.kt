package top.wcpe.taboolib.ioc.test.v12.aspect

import top.wcpe.taboolib.ioc.annotation.Around
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.bean.MethodInvocation

@Aspect
class LogAspectV12 {

    val records = mutableListOf<String>()

    @Around("execution(GreetingServiceV12.greet)")
    fun aroundGreet(invocation: MethodInvocation): Any? {
        records.add("before:${invocation.method.name}")
        val result = invocation.proceed()
        records.add("after:${invocation.method.name}=$result")
        return result
    }
}
