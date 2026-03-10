package top.wcpe.taboolib.ioc.cycle

import java.util.concurrent.ConcurrentHashMap

/**
 * 循环依赖检测器 - 检测构造函数注入的循环依赖
 */
class CycleDetector {

    private val creating = ConcurrentHashMap<String, Unit>()
    private val dependencyChain = mutableListOf<String>()

    /**
     * 标记 Bean 开始创建
     * @throws CircularDependencyException 如果检测到构造函数循环依赖
     */
    fun beginCreation(beanName: String, isConstructorInjection: Boolean) {
        if (isConstructorInjection && creating.containsKey(beanName)) {
            throw CircularDependencyException(beanName, dependencyChain + beanName)
        }
        creating[beanName] = Unit
        dependencyChain.add(beanName)
    }

    /**
     * 标记 Bean 创建完成
     */
    fun endCreation(beanName: String) {
        creating.remove(beanName)
        dependencyChain.remove(beanName)
    }

    /**
     * 清空状态
     */
    fun clear() {
        creating.clear()
        dependencyChain.clear()
    }
}
