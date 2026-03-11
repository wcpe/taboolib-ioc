package top.wcpe.taboolib.ioc.cycle

/**
 * 依赖图循环检测器。
 */
class CycleDetector {

    fun <T> findFirstCycle(
        nodes: Collection<T>,
        nameOf: (T) -> String,
        dependenciesOf: (T) -> Collection<T>
    ): List<String>? {
        return findCycles(nodes, nameOf, dependenciesOf).firstOrNull()
    }

    fun <T> findCycles(
        nodes: Collection<T>,
        nameOf: (T) -> String,
        dependenciesOf: (T) -> Collection<T>
    ): List<List<String>> {
        val visiting = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val cycles = linkedMapOf<String, List<String>>()

        fun visit(node: T) {
            val nodeName = nameOf(node)
            val visitingIndex = visiting.indexOf(nodeName)
            if (visitingIndex >= 0) {
                val cycle = visiting.subList(visitingIndex, visiting.size) + nodeName
                cycles[canonicalKey(cycle)] = normalizeCycle(cycle)
                return
            }
            if (!visited.add(nodeName)) {
                return
            }

            visiting += nodeName
            dependenciesOf(node).forEach(::visit)
            visiting.removeAt(visiting.lastIndex)
        }

        nodes.forEach(::visit)
        return cycles.values.toList()
    }

    private fun normalizeCycle(cycle: List<String>): List<String> {
        val normalized = normalizedCore(cycle)
        return normalized + normalized.first()
    }

    private fun canonicalKey(cycle: List<String>): String {
        return normalizedCore(cycle).joinToString("->")
    }

    private fun normalizedCore(cycle: List<String>): List<String> {
        val core = if (cycle.size > 1 && cycle.first() == cycle.last()) {
            cycle.dropLast(1)
        } else {
            cycle
        }
        if (core.isEmpty()) {
            return emptyList()
        }
        return core.indices
            .map { index -> core.drop(index) + core.take(index) }
            .minBy { it.joinToString("->") }
    }
}
