package top.wcpe.taboolib.ioc.scan

import top.wcpe.taboolib.ioc.annotation.ComponentScan

internal object ComponentScanPackages {

    fun resolve(classes: Iterable<Class<*>>): Set<String> {
        val packages = linkedSetOf<String>()
        classes.forEach { clazz ->
            val annotation = clazz.getAnnotation(ComponentScan::class.java) ?: return@forEach
            packages += resolve(annotation, clazz)
        }
        return packages
    }

    fun matches(clazz: Class<*>, scanPackages: Set<String>): Boolean {
        if (scanPackages.isEmpty()) {
            return true
        }

        val packageName = clazz.`package`?.name.orEmpty()
        return scanPackages.any { scanPackage ->
            scanPackage.isEmpty() || packageName == scanPackage || packageName.startsWith("$scanPackage.")
        }
    }

    private fun resolve(annotation: ComponentScan, declaringClass: Class<*>): Set<String> {
        val packages = linkedSetOf<String>()
        packages += annotation.value.mapNotNull(::normalizePackage)
        packages += annotation.basePackages.mapNotNull(::normalizePackage)
        packages += annotation.basePackageClasses
            .mapNotNull { it.java.`package`?.name }
            .mapNotNull(::normalizePackage)

        if (packages.isNotEmpty()) {
            return packages
        }

        return setOf(declaringClass.`package`?.name.orEmpty())
    }

    private fun normalizePackage(packageName: String?): String? {
        return packageName
            ?.trim()
            ?.removeSuffix(".*")
            ?.removeSuffix(".")
    }
}
