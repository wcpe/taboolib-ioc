package top.wcpe.taboolib.ioc.scan

import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.*
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.findAnnotation
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.hasAnnotation

/**
 * 配置类扫描器 — 解析 @Configuration 类中的 @Bean 方法，生成 BeanDefinition 列表。
 */
object ConfigurationScanner {

    /**
     * 扫描配置类中的 @Bean 方法。
     *
     * @param configClass 配置类
     * @param configBeanName 配置类自身的 Bean 名称
     * @return @Bean 方法对应的 BeanDefinition 列表
     */
    fun scan(configClass: Class<*>, configBeanName: String): List<BeanDefinition> {
        if (!configClass.isAnnotationPresent(Configuration::class.java)) {
            return emptyList()
        }

        return configClass.declaredMethods
            .filter { it.isAnnotationPresent(Bean::class.java) }
            .map { method ->
                val beanAnnotation = method.getAnnotation(Bean::class.java)
                val beanName = beanAnnotation.value.ifEmpty { method.name }
                val returnType = method.returnType

                require(returnType != Void.TYPE) {
                    "@Bean 方法 ${configClass.name}.${method.name}() 返回类型不能为 void"
                }

                // 解析工厂方法参数作为依赖
                val parameters = method.parameters.map { param ->
                    val named = param.getAnnotation(Named::class.java)
                    val lazy = param.getAnnotation(Lazy::class.java)?.value == true
                    InjectParameter(
                        type = param.type,
                        nameQualifier = named?.value?.takeIf { it.isNotEmpty() },
                        lazy = lazy
                    )
                }

                // 扫描返回类型上的生命周期回调
                val postConstruct = returnType.declaredMethods.firstOrNull {
                    it.isAnnotationPresent(PostConstruct::class.java)
                }
                val postEnable = returnType.declaredMethods.firstOrNull {
                    it.isAnnotationPresent(PostEnable::class.java)
                }
                val preDestroy = returnType.declaredMethods.firstOrNull {
                    it.isAnnotationPresent(PreDestroy::class.java)
                }
                val postConstructMethods = returnType.declaredMethods.filter {
                    it.isAnnotationPresent(PostConstruct::class.java)
                }
                val postEnableMethods = returnType.declaredMethods.filter {
                    it.isAnnotationPresent(PostEnable::class.java)
                }
                val preDestroyMethods = returnType.declaredMethods.filter {
                    it.isAnnotationPresent(PreDestroy::class.java)
                }

                // 扫描返回类型上的 @Inject 字段
                val injectFields = resolveInjectFields(returnType)
                // 扫描返回类型上的 @Inject 方法
                val injectMethods = resolveInjectMethods(returnType)
                // 扫描返回类型上的 @Value 字段
                val valueFields = resolveValueFields(returnType)

                val isPrimary = method.isAnnotationPresent(Primary::class.java)
                val order = method.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
                val lazyInit = method.getAnnotation(Lazy::class.java)?.value == true
                val scope = method.getAnnotation(Scope::class.java)?.value?.let { BeanScopes.normalize(it) }
                    ?: BeanScopes.SINGLETON

                BeanDefinition(
                    name = beanName,
                    type = returnType,
                    constructor = null,
                    injectFields = injectFields,
                    injectMethods = injectMethods,
                    postConstruct = postConstruct,
                    postEnable = postEnable,
                    preDestroy = preDestroy,
                    constructorParameters = parameters,
                    dependencies = parameters +
                        injectFields.map { InjectParameter(it.requiredType, it.nameQualifier, it.lazy) } +
                        injectMethods.flatMap { m -> m.parameters },
                    lazyInit = lazyInit,
                    scope = scope,
                    isPrimary = isPrimary,
                    order = order,
                    valueFields = valueFields,
                    factoryBeanName = configBeanName,
                    factoryMethod = method,
                    postConstructMethods = postConstructMethods,
                    postEnableMethods = postEnableMethods,
                    preDestroyMethods = preDestroyMethods
                )
            }
    }

    /** 解析类上的 @Inject/@Resource 字段 */
    private fun resolveInjectFields(clazz: Class<*>): List<InjectField> {
        return clazz.declaredFields.mapNotNull { field ->
            val inject = field.hasAnnotation(Inject::class.java)
            val resource = field.findAnnotation(Resource::class.java)
            val named = field.findAnnotation(Named::class.java)
            val lazy = field.findAnnotation(Lazy::class.java)

            if (!inject && resource == null) return@mapNotNull null

            val nameQualifier = when {
                resource != null && resource.name.isNotEmpty() -> resource.name
                named != null && named.value.isNotEmpty() -> named.value
                else -> null
            }

            val injectAnnotation = field.findAnnotation(Inject::class.java)
            val required = injectAnnotation?.required ?: true

            InjectField(
                field = field,
                requiredType = field.type,
                nameQualifier = nameQualifier,
                lazy = lazy?.value == true,
                required = required
            )
        }
    }

    /** 解析类上的 @Inject/@Resource 方法 */
    private fun resolveInjectMethods(clazz: Class<*>): List<InjectMethod> {
        return clazz.declaredMethods.filter { method ->
            !method.isSynthetic &&
                !method.name.endsWith("\$annotations") &&
                (method.isAnnotationPresent(Inject::class.java) ||
                    method.isAnnotationPresent(Resource::class.java))
        }.map { method ->
            val resource = method.getAnnotation(Resource::class.java)
            val params = method.parameters.map { param ->
                val named = param.getAnnotation(Named::class.java)
                InjectParameter(
                    type = param.type,
                    nameQualifier = named?.value?.takeIf { it.isNotEmpty() }
                        ?: resource?.name?.takeIf { it.isNotEmpty() && method.parameterCount == 1 }
                )
            }
            InjectMethod(method, params)
        }
    }

    /** 解析类上的 @Value 字段 */
    private fun resolveValueFields(clazz: Class<*>): List<ValueField> {
        return clazz.declaredFields.mapNotNull { field ->
            val value = field.getAnnotation(Value::class.java)
                ?: field.findAnnotation(Value::class.java)
                ?: return@mapNotNull null
            ValueField(field, value.value)
        }
    }
}
