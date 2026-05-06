package io.github.rygel.outerstellar.platform.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.migration.JavaMigration

fun createDataSource(jdbcUrl: String, jdbcUser: String, jdbcPassword: String): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = jdbcUser
            password = jdbcPassword
            maximumPoolSize = 20
            minimumIdle = 2
            idleTimeout = 300_000
            maxLifetime = 1_800_000
            connectionTimeout = 10_000
            leakDetectionThreshold = 60_000
            poolName = "outerstellar-pool"
        }
    )

fun migrate(dataSource: DataSource) {
    val loader = Thread.currentThread().contextClassLoader
    val migrationNames =
        listOf("V1__initial_schema", "V2__user_profile_enhancements", "V3__sessions_table", "V4__user_preferences")
    val javaMigrations =
        migrationNames.mapNotNull { name ->
            val sql =
                loader.getResourceAsStream("db/migration/$name.sql")?.bufferedReader()?.readText()
                    ?: return@mapNotNull null
            ClasspathSqlMigration(name, sql)
        }
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .javaMigrations(*javaMigrations.toTypedArray())
        .load()
        .migrate()
}

private class ClasspathSqlMigration(private val name: String, private val sql: String) : JavaMigration {
    override fun getVersion() =
        org.flywaydb.core.api.MigrationVersion.fromVersion(name.substringBefore("__").substringAfter("V"))

    override fun getDescription() = name.substringAfter("__").replace('_', ' ')

    override fun getChecksum() = sql.hashCode()

    override fun canExecuteInTransaction() = true

    override fun migrate(context: org.flywaydb.core.api.migration.Context) {
        context.connection.createStatement().execute(sql)
    }
}

fun migratePlugin(dataSource: DataSource, location: String, historyTable: String) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations(location)
        .table(historyTable)
        .baselineOnMigrate(true)
        .load()
        .migrate()
}
