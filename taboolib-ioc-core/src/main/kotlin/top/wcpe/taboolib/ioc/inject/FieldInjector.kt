package top.wcpe.taboolib.ioc.inject

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanNotOfRequiredTypeException
import top.wcpe.taboolib.ioc.bean.BeanRegistry

/**
 * 字段注入器 - 处理字段和方法的依赖注入
 *
 * @property registry 容器注册表，用于按名称查找 Bean 定义
 * @property beanProvider 统一的 Bean 解析出口（通常是 BeanResolver.getBean）
 */
class FieldInjector(
    private val registry: BeanRegistry,
    private val beanProvider: (type: Class<*>, name: String?) -> Any?
) {

    /**
     * 注入字段依赖。
     *
     * 对所有字段（含 `@Lazy` 回退路径）统一遵循与普通 `@Inject` 相同的 `required` 语义：
     * - 依赖解析成功 → 直接赋值
     * - 依赖缺失且 required → 抛 [IllegalStateException]
     * - 依赖缺失且非 required → 记 warning 日志，字段保持原值
     */
    fun injectFields(instance: Any, definition: BeanDefinition) {
        for (injectField in definition.injectFields) {
            if (injectField.lazy) {
                // 延迟注入：创建代理；若类型不可代理则回退到立即注入
                // （回退路径同样遵循 injectField.required 语义，避免依赖缺失被静默吞掉）
                injectLazyField(
                    instance,
                    injectField.requiredType,
                    injectField.nameQualifier,
                    injectField.field,
                    injectField.required
                )
            } else {
                val value = resolveDependencySafely(
                    injectField.requiredType,
                    injectField.nameQualifier,
                    instance,
                    injectField.field.name,
                    injectField.required
                )
                if (value != null) {
                    injectField.field.isAccessible = true
                    injectField.field.set(instance, value)
                } else if (injectField.required) {
                    throw requiredFieldMissing(instance, injectField)
                } else {
                    warning(
                        "[IoC] 可选字段注入未找到匹配 Bean: ${instance.javaClass.simpleName}.${injectField.field.name}" +
                            " (类型=${injectField.requiredType.simpleName}" +
                            "${if (injectField.nameQualifier != null) ", 名称=${injectField.nameQualifier}" else ""})"
                    )
                }
            }
        }
    }

    /**
     * 为 object 类注入单个延迟字段（供 ObjectInjector 调用）。
     *
     * `@Lazy` 仅对接口类型生效（JDK 动态代理只能基于接口）。当字段类型是非接口的**具体类**时，
     * 本方法回退到立即注入，并复用与普通 `@Inject` 字段一致的 [required] 语义：
     * - 依赖缺失且 [required] → 抛出带修复指引的异常
     * - 依赖缺失且非 [required] → 记 warning 日志
     *
     * 此外针对 A-P0-04：若具体类字段的目标 Bean 已被 AOP 包装为 JDK 动态代理，
     * 出口类型校验会抛 [BeanNotOfRequiredTypeException]。此时不再让该异常原样冒泡，
     * 而是转换为一条明确指向 `@Lazy` 用法的指引信息（接口声明或去掉 `@Lazy`）。
     *
     * @param instance 目标实例
     * @param type 字段的声明类型
     * @param nameQualifier 名称限定符（来自 `@Named` / `@Resource`）
     * @param field 目标字段
     * @param required 是否必须注入成功，默认 true（与 `@Inject` 默认值一致）
     */
    fun injectLazyField(
        instance: Any,
        type: Class<*>,
        nameQualifier: String?,
        field: java.lang.reflect.Field,
        required: Boolean = true
    ) {
        if (!LazyProxyFactory.canProxy(type)) {
            debug(
                "[IoC] @Lazy 仅支持接口类型，${type.name} 不是接口，回退到立即注入" +
                    " (${instance.javaClass.simpleName}.${field.name})"
            )
            // 使用带 @Lazy 指引的解析：目标被 AOP 代理（只实现接口）时不会原样抛
            // BeanNotOfRequiredTypeException，而是转换为「@Lazy 仅支持接口类型」的修复指引。
            val value = resolveDependencyWithLazyGuidance(type, nameQualifier)
            if (value != null) {
                field.isAccessible = true
                field.set(instance, value)
                debug("[IoC] @Lazy 字段回退立即注入成功: ${instance.javaClass.simpleName}.${field.name}")
            } else if (required) {
                throw IllegalStateException(
                    "[IoC] @Lazy 字段回退立即注入失败: ${instance.javaClass.simpleName}.${field.name}" +
                        " (类型=${type.simpleName}" +
                        "${if (nameQualifier != null) ", 名称=$nameQualifier" else ""})" +
                        " — @Lazy 仅支持接口类型，此处为非接口的具体类、" +
                        "回退到立即注入后仍未找到匹配的 Bean。" +
                        "请确认依赖已注册，或使用 @Inject(required=false) 标记为可选"
                )
            } else {
                warning(
                    "[IoC] @Lazy 可选字段回退立即注入未找到匹配 Bean: ${instance.javaClass.simpleName}.${field.name}" +
                        " (类型=${type.simpleName}" +
                        "${if (nameQualifier != null) ", 名称=$nameQualifier" else ""})"
                )
            }
            return
        }

        val proxy = LazyProxyFactory.createProxy(type) {
            resolveDependency(type, nameQualifier)
        }
        field.isAccessible = true
        field.set(instance, proxy)
        debug("[IoC] @Lazy 代理已注入字段: ${instance.javaClass.simpleName}.${field.name}")
    }

    /**
     * 解析延迟代理（供构造函数参数使用）。
     *
     * 与 [injectLazyField] 一致：非接口类型回退到立即解析；
     * 若目标被 AOP 代理导致出口类型校验失败，转换为明确的 `@Lazy` 使用指引。
     */
    fun resolveLazyDependency(type: Class<*>, nameQualifier: String?): Any? {
        if (!LazyProxyFactory.canProxy(type)) {
            debug("[IoC] @Lazy 仅支持接口类型，${type.name} 不是接口，回退到立即解析")
            return resolveDependencyWithLazyGuidance(type, nameQualifier)
        }

        return LazyProxyFactory.createProxy(type) {
            resolveDependency(type, nameQualifier)
        }
    }

    /**
     * 注入方法依赖（setter 注入）。
     *
     * ## required 语义约定（A-P1-08）
     *
     * [top.wcpe.taboolib.ioc.bean.InjectMethod] / [top.wcpe.taboolib.ioc.bean.InjectParameter]
     * 当前不携带 `required` 字段。为避免修改 annotation 模块（属于其他修复分片），
     * 这里约定：**方法注入的 required 语义取自方法上的 `@Inject` 注解**
     * （`@Inject` 默认 `required = true`；`@Resource` 方法按 JSR-250 视为 required）。
     * - required 方法任一参数解析失败 → 抛 [IllegalStateException]
     * - `@Inject(required=false)` 方法任一参数解析失败 → 记 warning 并**跳过该方法调用**
     *   （方法注入无法“部分注入”，缺失参数时跳过整个调用是最小惊扰的选择）
     */
    fun injectMethods(instance: Any, definition: BeanDefinition) {
        for (injectMethod in definition.injectMethods) {
            val method = injectMethod.method
            val required = resolveMethodRequired(method)
            val resolvedArgs = ArrayList<Any?>(injectMethod.parameters.size)
            var missingParameter = false

            for ((index, param) in injectMethod.parameters.withIndex()) {
                val value = try {
                    resolveDependency(param.type, param.nameQualifier)
                } catch (e: BeanNotOfRequiredTypeException) {
                    // AOP 代理只实现接口，按具体类解析必然失败 —— 给出可操作指引
                    throw IllegalStateException(
                        "[IoC] 方法注入参数类型不匹配: ${instance.javaClass.simpleName}.${method.name}()" +
                            " 参数[$index]。${e.message}"
                    )
                }
                if (value == null) {
                    missingParameter = true
                    if (required) {
                        throw IllegalStateException(
                            "[IoC] 方法注入参数解析失败: ${instance.javaClass.simpleName}.${method.name}()" +
                                " 参数[$index] (类型=${param.type.simpleName}" +
                                "${if (param.nameQualifier != null) ", 名称=${param.nameQualifier}" else ""})" +
                                " — 未找到匹配的 Bean。请确保依赖已注册或使用 @Named 指定正确的 Bean 名称；" +
                                "若该方法是可选的，请使用 @Inject(required=false)"
                        )
                    }
                    break
                }
                resolvedArgs.add(value)
            }

            if (missingParameter) {
                warning(
                    "[IoC] 可选方法注入跳过（@Inject(required=false) 但存在未解析参数）: " +
                        "${instance.javaClass.simpleName}.${method.name}()"
                )
                continue
            }

            method.isAccessible = true
            method.invoke(instance, *resolvedArgs.toTypedArray())
        }
    }

    /**
     * 注入 @Value 属性值
     */
    fun injectValues(instance: Any, definition: BeanDefinition) {
        for (valueField in definition.valueFields) {
            val value = ValueResolver.resolve(valueField.expression, valueField.field.type)
            if (value != null) {
                valueField.field.set(instance, value)
            } else {
                warning(
                    "[IoC] @Value 注入失败: ${instance.javaClass.simpleName}.${valueField.field.name}" +
                        " (表达式=${valueField.expression})"
                )
            }
        }
    }

    /**
     * 解析依赖值。
     *
     * 名称限定分支（C-P2-07 注释修正）：当 `registry` 命中名称但类型不兼容
     * （`type` 不是定义类型的父类型/接口），同样回退到 `beanProvider`，
     * 而非仅「registry 中未找到」才回退 —— 这两条路径都会落到 else 分支。
     */
    internal fun resolveDependency(type: Class<*>, nameQualifier: String?): Any? {
        // 如果有名称限定，按名称查找
        if (!nameQualifier.isNullOrEmpty()) {
            val definition = registry.getByName(nameQualifier)
            if (definition != null && type.isAssignableFrom(definition.type)) {
                return beanProvider(type, nameQualifier)
            }
            // 以下两种情况统一回退到 beanProvider（可能是手动注册的 Bean，或类型需由出口校验兜底）：
            //   1) registry 中未找到该名称
            //   2) 找到了但类型不兼容（type 无法接收 definition.type）
            return beanProvider(type, nameQualifier)
        }

        // 按类型查找 — 统一委托给 beanProvider，由 BeanResolver 通过 @Primary 逻辑选择
        return beanProvider(type, null)
    }

    /**
     * 在普通字段注入路径上解析依赖，并统一处理 AOP 代理导致的类型不匹配。
     *
     * 当请求的类型是非接口的具体类、而目标 Bean 被包装为 JDK 动态代理时，
     * `beanProvider` 会抛 [BeanNotOfRequiredTypeException]。此处捕获并转换为
     * 一条明确的、带修复指引的异常，避免业务侧看到难以定位的类型错误。
     * 返回 null 表示依赖确实不存在（由调用方按 `required` 语义处理）。
     */
    private fun resolveDependencySafely(
        type: Class<*>,
        nameQualifier: String?,
        instance: Any,
        fieldName: String,
        required: Boolean
    ): Any? {
        return try {
            resolveDependency(type, nameQualifier)
        } catch (e: BeanNotOfRequiredTypeException) {
            throw IllegalStateException(
                "[IoC] 字段注入类型不匹配: ${instance.javaClass.simpleName}.$fieldName" +
                    " (类型=${type.simpleName}" +
                    "${if (nameQualifier != null) ", 名称=$nameQualifier" else ""})" +
                    " — ${e.message}" +
                    "${if (!required) "（该字段标记为 @Inject(required=false)，但类型不匹配无法跳过）" else ""}"
            )
        }
    }

    /**
     * 回退立即解析路径专用：捕获 AOP 代理导致的类型不匹配，并给出 `@Lazy` 专属修复指引。
     */
    private fun resolveDependencyWithLazyGuidance(type: Class<*>, nameQualifier: String?): Any? {
        return try {
            resolveDependency(type, nameQualifier)
        } catch (e: BeanNotOfRequiredTypeException) {
            throw IllegalStateException(
                "[IoC] @Lazy 注入失败: ${type.name} 是非接口的具体类、" +
                    "且该类型存在 AOP 通知（其 Bean 被包装为 JDK 动态代理，只能赋值给接口）。" +
                    "修复指引：@Lazy 仅支持接口类型 —— 请把注入点声明为接口类型，" +
                    "或去掉该字段上的 @Lazy（具体类请直接立即注入）。" +
                    "${if (nameQualifier != null) " (名称=$nameQualifier)" else ""}" +
                    " 原始错误: ${e.message}"
            )
        }
    }

    /**
     * 从方法上的 `@Inject` 注解解析 required 语义。
     *
     * `@Resource` 方法遵循 JSR-250：视为必需（返回 true）。
     * 无注解或 `@Inject` 无显式参数时，取 `@Inject.required` 的默认值（true）。
     */
    private fun resolveMethodRequired(method: java.lang.reflect.Method): Boolean {
        val inject = method.getAnnotation(Inject::class.java) ?: return true
        return inject.required
    }

    /**
     * 构造必需的字段缺失异常（与普通 `@Inject` 路径保持一致的文案与指引）。
     */
    private fun requiredFieldMissing(
        instance: Any,
        injectField: top.wcpe.taboolib.ioc.bean.InjectField
    ): IllegalStateException {
        return IllegalStateException(
            "[IoC] 必需的字段注入失败: ${instance.javaClass.simpleName}.${injectField.field.name}" +
                " (类型=${injectField.requiredType.simpleName}" +
                "${if (injectField.nameQualifier != null) ", 名称=${injectField.nameQualifier}" else ""})" +
                " — 未找到匹配的 Bean。如果该依赖是可选的，请使用 @Inject(required=false)"
        )
    }
}
