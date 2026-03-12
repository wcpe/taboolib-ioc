package top.wcpe.taboolib.ioc.lifecycle

import top.wcpe.taboolib.ioc.bean.ContainerEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 容器事件总线。
 *
 * 允许注册监听器接收容器生命周期事件。
 */
class EventBus {

    private val listeners = CopyOnWriteArrayList<(ContainerEvent) -> Unit>()

    /**
     * 注册事件监听器
     */
    fun subscribe(listener: (ContainerEvent) -> Unit) {
        listeners.add(listener)
    }

    /**
     * 注册类型安全的事件监听器
     */
    inline fun <reified T : ContainerEvent> on(crossinline handler: (T) -> Unit) {
        subscribe { event ->
            if (event is T) handler(event)
        }
    }

    /**
     * 发布事件
     */
    fun publish(event: ContainerEvent) {
        listeners.forEach { it(event) }
    }

    /**
     * 清除所有监听器
     */
    fun clear() {
        listeners.clear()
    }
}
