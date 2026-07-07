package io.github.rygel.outerstellar.platform

const val DEFAULT_EXTENSION_MIGRATION_HISTORY_TABLE = "flyway_extension_history"

/**
 * Flyway migration metadata contributed by an extension.
 *
 * Extension migrations run in a **separate Flyway pass** from the platform's own migrations, against the extension's
 * own history table, so the two migration sets can each start at V1 without colliding (the platform's
 * `flyway_schema_history` is untouched by the extension pass). See [DatabaseInfra.migrate] (#611).
 *
 * @property location classpath location containing the extension's Flyway migrations
 * @property historyTable Flyway schema-history table the extension's migrations are tracked in. Each extension MUST use
 *   a distinct table to avoid colliding with the platform's `flyway_schema_history` and with other extensions; the
 *   default (`flyway_extension_history`) is shared, so multi-extension hosts should override with a namespaced name
 *   such as `flyway_<extension>_history`.
 * @property migrationNames optional explicit migration filenames, mainly used when native-image packaging needs the
 *   migration resources to be enumerated up front
 */
class ExtensionMigrations(
    val location: String,
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
