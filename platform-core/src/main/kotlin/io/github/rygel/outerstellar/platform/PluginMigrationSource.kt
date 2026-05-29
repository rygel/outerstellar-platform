package io.github.rygel.outerstellar.platform

/**
 * Implemented by plugins that ship their own Flyway migrations. The persistence module checks for this in Koin and runs
 * plugin migrations in a separate Flyway instance with its own history table.
 */
@Deprecated(
    message = "Use PluginMigrations values instead of implementing PluginMigrationSource directly.",
    replaceWith = ReplaceWith("PluginMigrations"),
)
interface PluginMigrationSource {
    /** Classpath location for plugin migrations, e.g. "classpath:db/migration/plugin". Null = no migrations. */
    val migrationLocation: String?
        get() = null

    /** Flyway schema history table name. Override to avoid collisions with the host. */
    val migrationHistoryTable: String
        get() = DEFAULT_PLUGIN_MIGRATION_HISTORY_TABLE

    /**
     * Migration filenames (without .sql extension) for native image support. In GraalVM native images, Flyway cannot
     * scan the classpath, so migrations must be explicitly listed so they can be extracted to a temp directory. Only
     * required if [migrationLocation] starts with "classpath:".
     */
    val migrationNames: List<String>
        get() = emptyList()
}

@Deprecated(
    message = "Use PluginMigrations values directly instead of adapting PluginMigrationSource.",
    replaceWith =
        ReplaceWith(
            "PluginMigrations(location = migrationLocation, historyTable = migrationHistoryTable, migrationNames = migrationNames)"
        ),
)
fun PluginMigrationSource.toPluginMigrations(): PluginMigrations? = migrationLocation?.let { location ->
    PluginMigrations(location = location, historyTable = migrationHistoryTable, migrationNames = migrationNames)
}
