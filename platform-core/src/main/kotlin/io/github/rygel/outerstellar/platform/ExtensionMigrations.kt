package io.github.rygel.outerstellar.platform

const val DEFAULT_EXTENSION_MIGRATION_HISTORY_TABLE = "flyway_extension_history"

/**
 * Flyway migration metadata contributed by an extension.
 *
 * @property location classpath location containing the extension's Flyway migrations
 * @property historyTable no longer used — all migrations share flyway_schema_history
 * @property migrationNames optional explicit migration filenames, mainly used when native-image packaging needs the
 *   migration resources to be enumerated up front
 */
class ExtensionMigrations(
    val location: String,
    @Deprecated("Extensions now share the platform history table. This field is ignored.")
    val historyTable: String = DEFAULT_EXTENSION_MIGRATION_HISTORY_TABLE,
    migrationNames: List<String> = emptyList(),
) {
    private val migrationNamesBacking: List<String> = migrationNames.toList()
    val migrationNames: List<String>
        get() = migrationNamesBacking.toList()

    override fun equals(other: Any?): Boolean =
        other is ExtensionMigrations &&
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
        "ExtensionMigrations(location=$location, historyTable=$historyTable, migrationNames=$migrationNamesBacking)"
}
