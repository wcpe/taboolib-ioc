package top.wcpe.taboolib.ioc.inject

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.*
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.findAnnotation
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.hasAnnotation

/**
 * 注入器 - 协调实例化、属性装配与生命周期回调
 */
class Injector(
    private val fieldInjector: FieldInjector,
    private val beanProvider: (type: Class<*>, name: String?) -> Any?
) {

    constructor(
        registry: BeanRegistry,
        cycleResolver: CycleResolver,
        fieldInjector: FieldInjector
    ) : this(
        fieldInjector = fieldInjector,
        beanProvider = singletonOnlyBeanProvider(registry, cycleResolver)
    )

    /**
     * 创建 Bean 实例，但不执行字段/方法注入。
     * 支持构造函数实例化和 @Bean 工厂方法实例化。
     *
     * 当类存在**多个** `@Inject` 构造函数时，`ClassScanner` 在静态扫描期已按确定性顺序
     * 固化了一个首选构造器（见 [ConstructorResolver]）。但由于静态扫描期容器尚未装配完、
     * 依赖不一定可见，首选构造器的依赖可能无法解析。为避免「确定性排序」引入行为退化
     * （修复前 JVM 未定义顺序可能碰巧选到可解析的那个），这里在**实例化期**（此时
     * `beanProvider` 已可用）按同一确定性顺序**逐个尝试**候选构造器，取第一个可解析者；
     * 全部失败才抛异常，并在异常中列出全部候选及各自失败原因。
     */
    fun instantiate(definition: BeanDefinition): Any {
        if (definition.isFactoryBean()) {
            return instantiateByFactory(definition)
        }
        val constructor = definition.constructor
            ?: throw IllegalStateException("BeanDefinition '${definition.name}' 缺少构造函数且不是工厂 Bean")

        // 多 @Inject 构造器：实例化期按确定性顺序逐个尝试解析，避免静态期选择的退化
        val injectConstructors = definition.type.declaredConstructors
            .filter { it.isAnnotationPresent(Inject::class.java) }
        if (injectConstructors.size > 1) {
            return instantiateWithFallback(definition, injectConstructors)
        }

        val constructorArgs = resolveConstructorArgs(definition)
        return constructor.newInstance(*constructorArgs)
    }

    /**
     * 在多个 `@Inject` 构造器之间按确定性顺序逐个尝试实例化，返回第一个成功者。
     *
     * 尝试顺序与 [ConstructorResolver] 完全一致（参数多者优先 → 参数类型名升序 → toString 兜底），
     * 保证确定性。每个候选的失败原因被收集；全部失败时抛出一条汇总了全部候选与原因的异常。
     */
    private fun instantiateWithFallback(
        definition: BeanDefinition,
        injectConstructors: List<java.lang.reflect.Constructor<*>>
    ): Any {
        val ordered = injectConstructors.sortedWith(constructorComparator())
        val failures = mutableListOf<String>()

        for ((index, candidate) in ordered.withIndex()) {
            try {
                val args = resolveConstructorArgsFor(definition, candidate)
                val instance = candidate.newInstance(*args)
                if (index > 0) {
                    warning(
                        "[IoC] Bean ${definition.name} 的首选 @Inject 构造器依赖无法解析，" +
                            "已回退到第 ${index + 1} 个候选: " +
                            "(${candidate.parameterTypes.joinToString(", ") { it.simpleName }})。"
                    )
                }
                debug(
                    "[IoC] Bean ${definition.name} 选用 @Inject 构造器: " +
                        "(${candidate.parameterTypes.joinToString(", ") { it.simpleName }})"
                )
                return instance
            } catch (e: Exception) {
                failures += "  - (${candidate.parameterTypes.joinToString(", ") { it.simpleName }}): " +
                    "${e.message ?: e.javaClass.simpleName}"
            }
        }

        throw IllegalStateException(
            "[IoC] Bean '${definition.name}' 的所有 @Inject 构造函数均无法解析，已尝试 " +
                "${ordered.size} 个候选：\n${failures.joinToString("\n")}\n" +
                "请确保至少一个 @Inject 构造函数的依赖可被解析，或显式只保留一个 @Inject 构造函数。"
        )
    }

    /**
     * 解析指定构造器的实参（供多 @Inject 构造器回退尝试使用）。
     *
     * 与 [resolveConstructorArgs] 语义一致：`@Lazy` 参数创建延迟代理，其余立即解析；
     * 非 Lazy 参数解析为 null 时抛 [BeanInstantiationException]。
     */
    private fun resolveConstructorArgsFor(
        definition: BeanDefinition,
        constructor: java.lang.reflect.Constructor<*>
    ): Array<Any?> {
        if (constructor.parameterCount == 0) {
            return emptyArray()
        }
        return constructor.parameterTypes.mapIndexed { index, type ->
            val qualifier = ConstructorQualifierResolver.resolve(constructor, index)
            val lazy = constructor.parameters.getOrNull(index)
                ?.getAnnotation(Lazy::class.java)?.value == true
            val resolved = if (lazy) {
                fieldInjector.resolveLazyDependency(type, qualifier)
            } else {
                beanProvider(type, qualifier)
            }
            if (resolved == null && !lazy) {
                throw BeanInstantiationException.missingConstructorParameter(
                    beanName = definition.name,
                    beanType = definition.type,
                    parameterIndex = index,
                    parameterType = type,
                    nameQualifier = qualifier
                )
            }
            resolved
        }.toTypedArray()
    }

    /**
     * 构造器确定性排序比较器（与 [ConstructorResolver] 的规则保持一致）：
     * 参数多者优先 → 参数类型全限定名升序 → toString 兜底。
     */
    private fun constructorComparator(): Comparator<java.lang.reflect.Constructor<*>> {
        return compareByDescending<java.lang.reflect.Constructor<*>> { it.parameterCount }
            .thenBy { ctor -> ctor.parameterTypes.joinToString(",") { it.name } }
            .thenBy { it.toString() }
    }

    /**
     * 通过 @Bean 工厂方法创建实例。
     */
    private fun instantiateByFactory(definition: BeanDefinition): Any {
        val factoryBeanName = definition.factoryBeanName!!
        val factoryMethod = definition.factoryMethod!!

        val factoryInstance = beanProvider(Any::class.java, factoryBeanName)
            ?: throw IllegalStateException(
                "@Bean 方法 '${definition.name}' 的配置类 '$factoryBeanName' 尚未创建"
            )

        val args = resolveFactoryMethodArgs(definition)
        val result = factoryMethod.invoke(factoryInstance, *args)
        return result ?: throw IllegalStateException(
            "@Bean 方法 ${factoryInstance.javaClass.name}.${factoryMethod.name}() 返回了 null"
        )
    }

    /**
     * 解析工厂方法参数。
     */
    private fun resolveFactoryMethodArgs(definition: BeanDefinition): Array<Any?> {
        if (definition.constructorParameters.isEmpty()) {
            return emptyArray()
        }
        return definition.constructorParameters.mapIndexed { index, parameter ->
            val resolved = if (parameter.lazy) {
                fieldInjector.resolveLazyDependency(parameter.type, parameter.nameQualifier)
            } else {
                beanProvider(parameter.type, parameter.nameQualifier)
            }

            // 验证非 Lazy 参数不能为 null
            if (resolved == null && !parameter.lazy) {
                throw BeanInstantiationException.missingFactoryMethodParameter(
                    beanName = definition.name,
                    factoryBeanName = definition.factoryBeanName!!,
                    factoryMethodName = definition.factoryMethod!!.name,
                    parameterIndex = index,
                    parameterType = parameter.type,
                    nameQualifier = parameter.nameQualifier
                )
            }

            resolved
        }.toTypedArray()
    }

    /**
     * 执行字段与方法注入。
     * 对于 @Bean 工厂方法产物，如果实际实例类型与声明返回类型不同（如返回接口），
     * 会补充扫描实际类型上的 @Inject/@Value 字段、@Inject 方法，
     * 以及 @PostConstruct / @PostEnable / @PreDestroy 生命周期回调。
     */
    fun populate(instance: Any, definition: BeanDefinition) {
        fieldInjector.injectFields(instance, definition)
        fieldInjector.injectMethods(instance, definition)
        fieldInjector.injectValues(instance, definition)

        // 补充扫描：@Bean 返回接口类型时，实际实例可能有额外的注入点与生命周期回调
        if (definition.isFactoryBean()) {
            val actualClass = instance.javaClass
            if (actualClass != definition.type) {
                val extraFields = resolveInjectFields(actualClass)
                    .filter { extra -> definition.injectFields.none { it.field == extra.field } }
                val extraMethods = resolveInjectMethods(actualClass)
                    .filter { extra -> definition.injectMethods.none { it.method == extra.method } }
                val extraValues = resolveValueFields(actualClass)
                    .filter { extra -> definition.valueFields.none { it.field == extra.field } }

                // A-P1-06：补充扫描 @PostConstruct / @PostEnable / @PreDestroy。
                // 声明返回类型为接口时，这些回调不会出现在 definition 上，若此处漏扫会静默不执行。
                // 采用与 ClassScanner 一致的扫描方式（按注解过滤 declaredMethods）。
                val extraPostConstruct = resolveLifecycleMethods(actualClass, PostConstruct::class.java)
                    .filter { extra -> definition.postConstructMethods.none { it.sameSignatureAs(extra) } }
                val extraPostEnable = resolveLifecycleMethods(actualClass, PostEnable::class.java)
                    .filter { extra -> definition.postEnableMethods.none { it.sameSignatureAs(extra) } }
                val extraPreDestroy = resolveLifecycleMethods(actualClass, PreDestroy::class.java)
                    .filter { extra -> definition.preDestroyMethods.none { it.sameSignatureAs(extra) } }

                if (extraPostEnable.isNotEmpty()) {
                    debug(
                        "[IoC] @Bean 产物补充扫描到 @PostEnable: ${definition.name} -> " +
                            extraPostEnable.joinToString(", ") { it.name }
                    )
                }
                if (extraPreDestroy.isNotEmpty()) {
                    debug(
                        "[IoC] @Bean 产物补充扫描到 @PreDestroy: ${definition.name} -> " +
                            extraPreDestroy.joinToString(", ") { it.name }
                    )
                }
                if (extraPostConstruct.isNotEmpty()) {
                    debug(
                        "[IoC] @Bean 产物补充扫描到 @PostConstruct: ${definition.name} -> " +
                            extraPostConstruct.joinToString(", ") { it.name }
                    )
                }

                if (extraFields.isNotEmpty() || extraMethods.isNotEmpty() || extraValues.isNotEmpty()) {
                    val supplementDef = BeanDefinition(
                        name = definition.name,
                        type = actualClass,
                        constructor = null,
                        injectFields = extraFields,
                        injectMethods = extraMethods,
                        postConstruct = extraPostConstruct.firstOrNull(),
                        postEnable = extraPostEnable.firstOrNull(),
                        preDestroy = extraPreDestroy.firstOrNull(),
                        constructorParameters = emptyList(),
                        dependencies = emptyList(),
                        valueFields = extraValues,
                        postConstructMethods = definition.postConstructMethods + extraPostConstruct,
                        postEnableMethods = definition.postEnableMethods + extraPostEnable,
                        preDestroyMethods = definition.preDestroyMethods + extraPreDestroy
                    )
                    fieldInjector.injectFields(instance, supplementDef)
                    fieldInjector.injectMethods(instance, supplementDef)
                    fieldInjector.injectValues(instance, supplementDef)
                }
            }
        }
    }

    /**
     * 扫描指定类型上标有给定生命周期注解的方法（与 [top.wcpe.taboolib.ioc.scan.ClassScanner] 一致）。
     */
    private fun resolveLifecycleMethods(
        clazz: Class<*>,
        annotationClass: Class<out Annotation>
    ): List<java.lang.reflect.Method> {
        return clazz.declaredMethods.filter { it.isAnnotationPresent(annotationClass) }
    }

    /**
     * 判断两个方法是否为同一签名（名称 + 参数个数 + 参数类型），
     * 用于补充扫描时去重，避免重复执行生命周期回调。
     */
    private fun java.lang.reflect.Method.sameSignatureAs(other: java.lang.reflect.Method): Boolean {
        return this.name == other.name &&
            this.parameterCount == other.parameterCount &&
            this.parameterTypes.contentEquals(other.parameterTypes)
    }

    /**
     * 调用初始化回调。
     * 遍历所有 @PostConstruct 方法。
     * 对于 @Bean 工厂方法产物，如果实际类型与声明类型不同，补充扫描实际类型的回调。
     */
    fun invokePostConstruct(instance: Any, definition: BeanDefinition) {
        for (method in definition.postConstructMethods) {
            try {
                method.invoke(instance)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e
            }
        }
        // 补充扫描：@Bean 返回接口类型时，实际实例可能有额外的 @PostConstruct
        if (definition.isFactoryBean()) {
            val actualClass = instance.javaClass
            if (actualClass != definition.type) {
                val extraMethods = actualClass.declaredMethods.filter {
                    it.isAnnotationPresent(PostConstruct::class.java)
                }.filter { extra ->
                    definition.postConstructMethods.none { it.name == extra.name && it.parameterCount == extra.parameterCount }
                }
                for (method in extraMethods) {
                    method.isAccessible = true
                    try {
                        method.invoke(instance)
                    } catch (e: java.lang.reflect.InvocationTargetException) {
                        throw e.targetException ?: e
                    }
                }
            }
        }
    }

    /**
     * 解析构造函数参数。
     * 对标记了 @Lazy 的参数创建延迟代理，其余立即解析。
     */
    private fun resolveConstructorArgs(definition: BeanDefinition): Array<Any?> {
        if (definition.constructorParameters.isEmpty()) {
            return emptyArray()
        }

        return definition.constructorParameters.mapIndexed { index, parameter ->
            val resolved = if (parameter.lazy) {
                fieldInjector.resolveLazyDependency(parameter.type, parameter.nameQualifier)
            } else {
                beanProvider(parameter.type, parameter.nameQualifier)
            }

            // 验证非 Lazy 参数不能为 null
            if (resolved == null && !parameter.lazy) {
                throw BeanInstantiationException.missingConstructorParameter(
                    beanName = definition.name,
                    beanType = definition.type,
                    parameterIndex = index,
                    parameterType = parameter.type,
                    nameQualifier = parameter.nameQualifier
                )
            }

            resolved
        }.toTypedArray()
    }

    // ── 运行时补充扫描辅助方法 ──

    private fun resolveInjectFields(clazz: Class<*>): List<InjectField> {
        return clazz.declaredFields.mapNotNull { field ->
            val inject = field.hasAnnotation(Inject::class.java)
            val resource = field.findAnnotation(Resource::class.java)
            if (!inject && resource == null) return@mapNotNull null
            val named = field.findAnnotation(Named::class.java)
            val lazy = field.findAnnotation(Lazy::class.java)
            val injectAnnotation = field.findAnnotation(Inject::class.java)
            InjectField(
                field = field,
                requiredType = field.type,
                nameQualifier = resource?.name?.takeIf { it.isNotEmpty() }
                    ?: named?.value?.takeIf { it.isNotEmpty() },
                lazy = lazy?.value == true,
                required = injectAnnotation?.required ?: true
            )
        }
    }

    private fun resolveInjectMethods(clazz: Class<*>): List<InjectMethod> {
        return clazz.declaredMethods.filter { m ->
            !m.isSynthetic && !m.name.endsWith("\$annotations") &&
                (m.isAnnotationPresent(Inject::class.java) || m.isAnnotationPresent(Resource::class.java))
        }.map { m ->
            val resource = m.getAnnotation(Resource::class.java)
            val params = m.parameters.map { p ->
                val named = p.getAnnotation(Named::class.java)
                InjectParameter(
                    type = p.type,
                    nameQualifier = named?.value?.takeIf { it.isNotEmpty() }
                        ?: resource?.name?.takeIf { it.isNotEmpty() && m.parameterCount == 1 }
                )
            }
            InjectMethod(m, params)
        }
    }

    private fun resolveValueFields(clazz: Class<*>): List<ValueField> {
        return clazz.declaredFields.mapNotNull { field ->
            val value = field.getAnnotation(Value::class.java)
                ?: field.findAnnotation(Value::class.java)
                ?: return@mapNotNull null
            ValueField(field, value.value)
        }
    }

    companion object {

        private fun singletonOnlyBeanProvider(
            registry: BeanRegistry,
            cycleResolver: CycleResolver
        ): (Class<*>, String?) -> Any? {
            return { type, name ->
                if (name != null) {
                    val namedInstance = cycleResolver.getSingleton(name)
                    if (namedInstance != null && type.isInstance(namedInstance)) {
                        namedInstance
                    } else {
                        null
                    }
                } else {
                    null
                } ?: run {
                    val definition = if (name != null) {
                        registry.getByName(name)
                    } else {
                        registry.getPrimaryByType(type)
                    } ?: return@run null

                    cycleResolver.getSingleton(definition.name)?.takeIf(type::isInstance)
                }
            }
        }
    }
}
