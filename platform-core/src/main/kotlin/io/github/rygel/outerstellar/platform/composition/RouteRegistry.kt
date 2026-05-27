package io.github.rygel.outerstellar.platform.composition

class RouteRegistry {
    private val entries = mutableListOf<RegisteredRoute>()

    fun register(entry: RegisteredRoute) {
        entries.add(entry)
    }

    fun registerAll(entries: List<RegisteredRoute>) {
        this.entries.addAll(entries)
    }

    fun all(): List<RegisteredRoute> = entries.toList()

    fun byGroup(group: RouteGroup): List<RegisteredRoute> = entries.filter { it.group == group }

    fun byOwner(owner: RouteOwner): List<RegisteredRoute> = entries.filter { it.owner == owner }

    fun conflicts(): List<RouteConflict> {
        val conflicts = mutableListOf<RouteConflict>()
        val seen = mutableMapOf<Pair<String, String>, RouteOwner>()
        for (entry in entries) {
            val key = entry.pathPattern to entry.method
            val existing = seen[key]
            if (existing != null && existing != entry.owner) {
                conflicts.add(RouteConflict(entry.pathPattern, entry.method, existing, entry.owner))
            } else {
                seen[key] = entry.owner
            }
        }
        return conflicts
    }

    fun requireNoConflicts() {
        val conflicts = conflicts()
        require(conflicts.isEmpty()) {
            val details =
                conflicts.joinToString("\n") { c -> "  ${c.method} ${c.pathPattern}: ${c.existing} vs ${c.challenger}" }
            "Route conflicts detected:\n$details"
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
        return sb.toString()
    }
}
