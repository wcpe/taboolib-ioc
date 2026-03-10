package top.wcpe.taboolib.ioc.inject

import top.wcpe.taboolib.ioc.annotation.Inject
import java.lang.reflect.Constructor

/**
 * 构造函数解析器 - 决定使用哪个构造函数
 */
class ConstructorResolver {

    /**
     * 解析类要使用的构造函数
     *
     * 优先级：
     * 1. 被 @Inject 标记的构造函数
     * 2. 唯一构造函数
     * 3. 无参构造函数
     */
    fun resolve(clazz: Class<*>): Constructor<*> {
        val constructors = clazz.declaredConstructors

        // 1. 查找 @Inject 标记的构造函数
        constructors.firstOrNull { it.isAnnotationPresent(Inject::class.java) }?.let {
            return it
        }

        // 2. 只有一个构造函数时直接使用
        if (constructors.size == 1) {
            return constructors[0]
        }

        // 3. 查找无参构造函数
        constructors.firstOrNull { it.parameterCount == 0 }?.let {
            return it
        }

        throw NoSuchMethodException("No suitable constructor found for ${clazz.name}, please mark one with @Inject")
    }

    /**
     * 判断是否为构造函数注入（有参数）
     */
    fun isConstructorInjection(constructor: Constructor<*>): Boolean {
        return constructor.parameterCount > 0
    }
}
