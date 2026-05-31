package io.github.rygel.outerstellar.platform

const val DEFAULT_PLUGIN_MIGRATION_HISTORY_TABLE = "flyway_plugin_history"

/**
 * Flyway migration metadata contributed by a hosted app.
 *
 * @property location classpath location containing the hosted app's Flyway migrations
 * @property historyTable dedicated Flyway history table for the hosted app; defaults to
 *   [DEFAULT_PLUGIN_MIGRATION_HISTORY_TABLE]
 * @property migrationNames optional explicit migration filenames, mainly used when native-image packaging needs the
 *   migration resources to be enumerated up front
 */
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
