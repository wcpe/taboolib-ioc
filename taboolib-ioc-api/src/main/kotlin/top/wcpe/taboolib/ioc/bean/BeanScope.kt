package top.wcpe.taboolib.ioc.bean

/**
 * 自定义 Bean 作用域。
 *
 * 非标准作用域会在首次访问时通过 [creator] 创建实例，
 * 是否缓存以及缓存多久由具体实现自行决定。
 */
interface BeanScope {

    fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any

    fun clear() {
    }
}
