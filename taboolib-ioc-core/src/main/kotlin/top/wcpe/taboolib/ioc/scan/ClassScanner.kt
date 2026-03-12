package top.wcpe.taboolib.ioc.scan

import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.*
import top.wcpe.taboolib.ioc.inject.ConstructorResolver
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.findAnnotation
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.hasAnnotation

/**
 * 类扫描器 - 解析类的元数据并创建 BeanDefinition
 */
class ClassScanner(
    private val constructorResolver: ConstructorResolver
) {

    /**
     * 扫描类并创建 BeanDefinition
     */
    fun scan(clazz: Class<*>): BeanDefinition? {
        if (!isComponent(clazz)) return null

        val isAspect = clazz.isAnnotationPresent(Aspect::class.java)
        val annotation = findComponentAnnotation(clazz)
        // @Aspect 类不需要额外的 @Component 注解
        val name = if (annotation != null) {
            resolveBeanName(clazz, annotation)
        } else if (isAspect) {
            clazz.simpleName.replaceFirstChar { it.lowercase() }
        } else {
            return null
        }
        val constructor = constructorResolver.resolve(clazz)
        val constructorParameters = resolveConstructorParameters(constructor)
        val injectFields = resolveInjectFields(clazz)
        val injectMethods = resolveInjectMethods(clazz)
        val dependencies = constructorParameters +
            injectFields.map { InjectParameter(it.requiredType, it.nameQualifier, it.lazy) } +
            injectMethods.flatMap { method -> method.parameters }
        val postConstruct = findPostConstruct(clazz)
        val postEnable = findPostEnable(clazz)
        val preDestroy = findPreDestroy(clazz)
        val lazyInit = resolveLazyInit(clazz)
        val scope = resolveScope(clazz)
        val isPrimary = clazz.isAnnotationPresent(Primary::class.java)
        val order = clazz.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE

        return BeanDefinition(
            name = name,
            type = clazz,
            constructor = constructor,
            constructorParameters = constructorParameters,
            injectFields = injectFields,
            injectMethods = injectMethods,
            dependencies = dependencies,
            postConstruct = postConstruct,
            postEnable = postEnable,
            preDestroy = preDestroy,
            lazyInit = lazyInit,
            scope = scope,
            isAspect = isAspect,
            isPrimary = isPrimary,
            order = order
        )
    }

    /**
     * 检查类是否为组件
     */
    fun isComponent(clazz: Class<*>): Boolean {
        return clazz.isAnnotationPresent(Component::class.java) ||
            clazz.isAnnotationPresent(Service::class.java) ||
            clazz.isAnnotationPresent(Repository::class.java) ||
            clazz.isAnnotationPresent(Controller::class.java) ||
            clazz.isAnnotationPresent(Aspect::class.java)
    }

    /**
     * 查找组件注解
     */
    private fun findComponentAnnotation(clazz: Class<*>): Annotation? {
        return clazz.getAnnotation(Component::class.java) ?: clazz.getAnnotation(Service::class.java)
            ?: clazz.getAnnotation(Repository::class.java)
            ?: clazz.getAnnotation(Controller::class.java)
    }

    /**
     * 解析 Bean 名称
     */
    private fun resolveBeanName(clazz: Class<*>, annotation: Annotation): String {
        val value = when (annotation) {
            is Component -> annotation.value
            is Service -> annotation.value
            is Repository -> annotation.value
            is Controller -> annotation.value
            else -> ""
        }

        if (value.isNotEmpty()) return value

        val className = clazz.simpleName
        return className.replaceFirstChar { it.lowercase() }
    }

    private fun resolveLazyInit(clazz: Class<*>): Boolean {
        return clazz.getAnnotation(Lazy::class.java)?.value == true
    }

    private fun resolveScope(clazz: Class<*>): String {
        if (clazz.isAnnotationPresent(Prototype::class.java)) {
            return BeanScopes.PROTOTYPE
        }
        if (clazz.isAnnotationPresent(ThreadScope::class.java)) {
            return BeanScopes.THREAD
        }
        if (clazz.isAnnotationPresent(RefreshScope::class.java)) {
            return BeanScopes.REFRESH
        }
        return BeanScopes.normalize(clazz.getAnnotation(Scope::class.java)?.value)
    }

    /**
     * 解析构造函数参数依赖。
     */
    private fun resolveConstructorParameters(constructor: java.lang.reflect.Constructor<*>): List<InjectParameter> {
        if (constructor.parameterCount == 0) {
            return emptyList()
        }

        return constructor.parameterTypes.mapIndexed { index, type ->
            val lazy = constructor.parameters.getOrNull(index)
                ?.getAnnotation(Lazy::class.java)?.value == true
            InjectParameter(
                type = type,
                nameQualifier = ConstructorQualifierResolver.resolve(constructor, index),
                lazy = lazy
            )
        }
    }

    /**
     * 解析需要注入的字段
     */
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

            InjectField(
                field = field,
                requiredType = field.type,
                nameQualifier = nameQualifier,
                lazy = lazy?.value == true
            )
        }
    }

    /**
     * 解析需要注入的方法
     */
    private fun resolveInjectMethods(clazz: Class<*>): List<InjectMethod> {
        return clazz.declaredMethods.filter { method ->
            !method.isSynthetic &&
                !method.name.endsWith("\$annotations") &&
                (
                    method.isAnnotationPresent(Inject::class.java) ||
                        method.isAnnotationPresent(Resource::class.java)
                    )
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

    /**
     * 查找 @PostConstruct 方法
     */
    private fun findPostConstruct(clazz: Class<*>): java.lang.reflect.Method? {
        return clazz.declaredMethods.firstOrNull { it.isAnnotationPresent(PostConstruct::class.java) }
    }

    /**
     * 查找 @PostEnable 方法
     */
    private fun findPostEnable(clazz: Class<*>): java.lang.reflect.Method? {
        return clazz.declaredMethods.firstOrNull { it.isAnnotationPresent(PostEnable::class.java) }
    }

    /**
     * 查找 @PreDestroy 方法
     */
    private fun findPreDestroy(clazz: Class<*>): java.lang.reflect.Method? {
        return clazz.declaredMethods.firstOrNull { it.isAnnotationPresent(PreDestroy::class.java) }
    }
}


