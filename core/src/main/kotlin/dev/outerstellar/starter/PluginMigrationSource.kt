package dev.outerstellar.starter

/**
 * Implemented by plugins that ship their own Flyway migrations. The persistence module checks for
 * this in Koin and runs plugin migrations in a separate Flyway instance with its own history table.
 */
interface PluginMigrationSource {
    /**
     * Classpath location for plugin migrations, e.g. "classpath:db/migration/plugin". Null = no
     * migrations.
     */
    val migrationLocation: String?
        get() = null

    /** Flyway schema history table name. Override to avoid collisions with the host. */
    val migrationHistoryTable: String
        get() = "flyway_plugin_history"
}
