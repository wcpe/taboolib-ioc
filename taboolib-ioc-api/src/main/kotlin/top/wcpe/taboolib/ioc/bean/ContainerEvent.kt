package top.wcpe.taboolib.ioc.bean

/**
 * IoC 容器事件基类
 */
abstract class ContainerEvent

/**
 * Bean 创建完成事件（依赖注入和 @PostConstruct 已执行）
 */
class BeanCreatedEvent(
    val beanName: String,
    val beanInstance: Any,
    val beanDefinition: BeanDefinition
) : ContainerEvent()

/**
 * Bean 销毁事件（@PreDestroy 已执行）
 */
class BeanDestroyedEvent(
    val beanName: String,
    val beanDefinition: BeanDefinition
) : ContainerEvent()

/**
 * 容器初始化完成事件
 */
class ContainerInitializedEvent(
    val beanCount: Int
) : ContainerEvent()

/**
 * 容器关闭完成事件
 */
class ContainerShutdownEvent : ContainerEvent()
