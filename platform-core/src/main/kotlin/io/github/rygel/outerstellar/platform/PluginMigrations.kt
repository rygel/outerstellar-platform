package io.github.rygel.outerstellar.platform

const val DEFAULT_PLUGIN_MIGRATION_HISTORY_TABLE = "flyway_plugin_history"

class PluginMigrations(
    val location: String,
    val historyTable: String = DEFAULT_PLUGIN_MIGRATION_HISTORY_TABLE,
    migrationNames: List<String> = emptyList(),
) {
    private val migrationNamesBacking: List<String> = migrationNames.toList()
    val migrationNames: List<String>
        get() = migrationNamesBacking.toList()

    override fun equals(other: Any?): Boolean =
        other is PluginMigrations &&
            location == other.location &&
            historyTable == other.historyTable &&
            migrationNamesBacking == other.migrationNamesBacking

    override fun hashCode(): Int {
        var result = location.hashCode()
        result = 31 * result + historyTable.hashCode()
        result = 31 * result + migrationNamesBacking.hashCode()
        return result
    }

    override fun toString(): String =
        "PluginMigrations(location=$location, historyTable=$historyTable, migrationNames=$migrationNamesBacking)"
}
