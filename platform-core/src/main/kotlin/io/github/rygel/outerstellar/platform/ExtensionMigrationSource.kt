package io.github.rygel.outerstellar.platform

/**
 * Implemented by extensions that ship their own Flyway migrations. The persistence module checks for this in Koin and
 * runs extension migrations in a separate Flyway instance with its own history table.
 */
@Deprecated(
    message = "Use ExtensionMigrations values instead of implementing ExtensionMigrationSource directly.",
    replaceWith = ReplaceWith("ExtensionMigrations"),
)
interface ExtensionMigrationSource {
    /** Classpath location for extension migrations, e.g. "classpath:db/migration/extension". Null = no migrations. */
    val migrationLocation: String?
        get() = null

    /** Flyway schema history table name. Override to avoid collisions with the host. */
    val migrationHistoryTable: String
        get() = DEFAULT_EXTENSION_MIGRATION_HISTORY_TABLE

    /**
     * Migration filenames (without .sql extension) for native image support. In GraalVM native images, Flyway cannot
     * scan the classpath, so migrations must be explicitly listed so they can be extracted to a temp directory. Only
     * required if [migrationLocation] starts with "classpath:".
     */
    val migrationNames: List<String>
        get() = emptyList()
}

@Deprecated(
    message = "Use ExtensionMigrations values directly instead of adapting ExtensionMigrationSource.",
    replaceWith =
        ReplaceWith(
            "ExtensionMigrations(location = migrationLocation, historyTable = migrationHistoryTable, migrationNames = migrationNames)"
        ),
)
fun ExtensionMigrationSource.toExtensionMigrations(): ExtensionMigrations? = migrationLocation?.let { location ->
    ExtensionMigrations(location = location, historyTable = migrationHistoryTable, migrationNames = migrationNames)
}
