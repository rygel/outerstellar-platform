package io.github.rygel.stellar.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class H2StellarTest {

    protected lateinit var jdbi: Jdbi
    private lateinit var dataSource: com.zaxxer.hikari.HikariDataSource

    @BeforeEach
    fun setupDatabase() {
        val dbName = "${javaClass.simpleName.lowercase()}_${System.nanoTime()}"
        val jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE"
        dataSource = createDataSource(jdbcUrl, "sa", "")
        migratePlatform(dataSource)
        migrateStellar(dataSource)
        jdbi = Jdbi.create(dataSource)
    }

    /**
     * Runs platform migrations only (not stellar sub-directory).
     *
     * The default `migrate(dataSource)` from DatabaseInfra scans `classpath:db/migration` which recursively picks up
     * `db/migration/stellar/V1__...` causing a version clash with the platform `V1__initial_schema.sql`. We read and
     * execute the platform SQL directly to avoid this.
     */
    private fun migratePlatform(dataSource: DataSource) {
        val sql =
            javaClass.classLoader
                .getResourceAsStream("db/migration/V1__initial_schema.sql")!!
                .bufferedReader()
                .readText()
        dataSource.connection.use { conn -> conn.createStatement().use { stmt -> stmt.execute(sql) } }
    }

    /**
     * Runs stellar plugin migrations via Flyway with a separate history table. We cannot use [migratePlugin] here
     * because it sets `baselineOnMigrate(true)` which skips V1 on a fresh database.
     */
    private fun migrateStellar(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/stellar")
            .table("flyway_stellar_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()
    }

    @AfterEach
    fun cleanDatabase() {
        if (::dataSource.isInitialized) {
            if (::jdbi.isInitialized) {
                jdbi.useHandle<Exception> { handle -> handle.execute("DROP ALL OBJECTS") }
            }
            dataSource.close()
        }
    }

    protected fun createTestUser(username: String): UUID {
        val userId = UUID.randomUUID()
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO plt_users (id, username, email, password_hash, role, enabled, created_at)
                    VALUES (:id, :username, :email, :passwordHash, 'USER', true, :createdAt)
                    """
                )
                .bind("id", userId)
                .bind("username", username)
                .bind("email", "$username@test.com")
                .bind("passwordHash", "hash_$username")
                .bind("createdAt", OffsetDateTime.now(ZoneOffset.UTC))
                .execute()
        }
        return userId
    }
}
