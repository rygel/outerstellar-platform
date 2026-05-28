package io.github.rygel.outerstellar.platform.composition

class RouteRegistry {
    private val entries = mutableListOf<RegisteredRoute>()
    private val excludedPageSets = mutableListOf<String>()

    fun register(entry: RegisteredRoute) {
        entries.add(entry)
    }

    fun registerAll(entries: List<RegisteredRoute>) {
        this.entries.addAll(entries)
    }

    fun registerExcludedPageSet(id: String) {
        excludedPageSets.add(id)
    }

    fun all(): List<RegisteredRoute> = entries.toList()

    fun excludedPageSets(): List<String> = excludedPageSets.toList()

    fun byGroup(group: RouteGroup): List<RegisteredRoute> = entries.filter { it.group == group }

    fun byOwner(owner: RouteOwner): List<RegisteredRoute> = entries.filter { it.owner == owner }

    fun conflicts(): List<RouteConflict> {
        val conflicts = mutableListOf<RouteConflict>()
        val seen = mutableMapOf<Pair<String, String>, RegisteredRoute>()
        for (entry in entries) {
            val key = entry.pathPattern to entry.method
            val existing = seen[key]
            if (existing != null && existing.owner != entry.owner) {
                conflicts.add(RouteConflict(existing, entry))
            } else {
                seen[key] = entry
            }
        }
        return conflicts
    }

    fun requireNoConflicts() {
        val conflicts = conflicts()
        require(conflicts.isEmpty()) {
            val details = conflicts.joinToString("\n\n") { it.formatForFailure() }
            "Route conflicts detected:\n$details\n\nRemediation: move the hosted app route into its manifest-owned " +
                "prefix, change the HTTP method, or explicitly exclude the colliding platform page set."
        }
    }

    fun formatTable(): String {
        val sb = StringBuilder()
        sb.appendLine("Platform Route Table (${entries.size} routes):")
        entries.sortedWith(compareBy({ it.pathPattern }, { it.method })).forEach { entry ->
            sb.appendLine(
                "  ${entry.method.padEnd(6)} ${entry.pathPattern.padEnd(30)} ${entry.owner.name.padEnd(20)} [${entry.group.name}]"
            )
        }
        val conflicts = conflicts()
        if (conflicts.isEmpty()) {
            sb.appendLine("No conflicts detected.")
        } else {
            sb.appendLine("${conflicts.size} conflict(s) detected!")
        }
        if (excludedPageSets.isNotEmpty()) {
            sb.appendLine("Excluded page sets: ${excludedPageSets.distinct().sorted().joinToString(", ")}")
        }
        return sb.toString()
    }
}

private fun RouteConflict.formatForFailure(): String {
    val existingDetails = existingRoute?.formatDetails() ?: existing.name
    val challengerDetails = challengerRoute?.formatDetails() ?: challenger.name
    return """
      ${method} ${pathPattern}
        existing: $existingDetails
        challenger: $challengerDetails
    """
        .trimIndent()
}

private fun RegisteredRoute.formatDetails(): String =
    "${owner.name} [${group.name}] ${description.ifBlank { "(no description)" }}"
