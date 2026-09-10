package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Proxy

/**
 * Bean 实际类型与请求类型不匹配时抛出。
 *
 * 最常见成因：Bean 被 AOP 包装为 JDK 动态代理（只实现接口），
 * 而注入点 / [BeanContainer.getBean] 按具体类请求。
 * 在此异常中给出可操作的修复指引，替代原先散落在调用点的
 * [ClassCastException] / [IllegalArgumentException]（后者与 IoC 毫无关联，极难定位）。
 */
class BeanNotOfRequiredTypeException(
    requiredType: Class<*>,
    actualType: Class<*>,
    beanName: String?
) : IllegalStateException(
    buildString {
        append("[IoC] Bean 类型不匹配: 需要 ${requiredType.name}，实际 ${actualType.name}")
        if (beanName != null) append(" (beanName=$beanName)")
        if (Proxy.isProxyClass(actualType)) {
            append(
                "。该实例是 JDK 动态代理（AOP 包装结果），只能赋值给接口类型。请二选一: " +
                    "(1) 把注入点声明为接口类型; " +
                    "(2) 调整切点表达式，使其不再命中 ${requiredType.simpleName} 的 public 方法"
            )
        }
    }
)
