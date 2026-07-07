package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.ExtensionMigrations
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.testing.SharedPostgres
import io.github.rygel.outerstellar.platform.testing.sanitizeDbName
import java.nio.file.Files
import javax.sql.DataSource
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Regression for #611: extension migrations that declare their own V1 must not collide with the platform's V1.
 *
 * `DatabaseInfra.migrate()` runs platform and extension migrations in two isolated Flyway passes with separate history
 * tables, so a platform `V1` and an extension `V1` never share a `CompositeMigrationResolver`. This test seeds an
 * extension migration (with its own `V1__ext_initial.sql`) on the filesystem, runs `migrate()` with an
 * `ExtensionMigrations`, and asserts both history tables exist and each recorded its V1 — i.e. neither pass collided.
 *
 * Uses [SharedPostgres] to create a fresh, empty database (not the auto-migrating testkit `TestDatabase.dataSource`,
 * which would pre-apply platform migrations via its own Flyway call) so this test controls the full two-pass run.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtensionMigrationIsolationTest {
    private val testDb = SharedPostgres.createDatabase(sanitizeDbName(this::class.simpleName!!))

    private val dataSource: DataSource =
        com.zaxxer.hikari.HikariDataSource(
            com.zaxxer.hikari.HikariConfig().apply {
                jdbcUrl = testDb.jdbcUrl
                username = testDb.jdbcUser
                password = testDb.jdbcPassword
            }
        )

    @AfterAll
    fun tearDown() {
        (dataSource as? AutoCloseable)?.close()
        SharedPostgres.dropDatabase(testDb.dbName)
    }

    @Test
    fun `extension V1 and platform V1 do not collide when extension uses its own history table`() {
        // Arrange: write an extension migration that ships its own V1 to a filesystem location.
        val extDir = Files.createTempDirectory("ext-migrations")
        extDir.toFile().deleteOnExit()
        Files.writeString(extDir.resolve("V1__ext_initial.sql"), "CREATE TABLE ext_marker (id integer PRIMARY KEY);")

        val extension =
            ExtensionMigrations(
                location = "filesystem:${extDir.toAbsolutePath()}",
                historyTable = "flyway_ext_test_history",
            )

        // Act: the full two-pass migrate (platform classpath pass + extension pass against its own table).
        // Before #611 this threw FlywayException: Found more than one migration with version 1.
        migrate(dataSource, extension = extension)

        // Assert: platform history table recorded its V1 against the default table.
        dataSource.connection.use { conn ->
            val platformHasV1 =
                conn
                    .prepareStatement(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE"
                    )
                    .use { stmt -> stmt.executeQuery().use { rs -> rs.next() && rs.getInt(1) >= 1 } }
            assertTrue(platformHasV1, "Platform history table must contain a successful V1 row")

            // Extension history table recorded its OWN V1 against the extension-declared table.
            val extHasV1 =
                conn
                    .prepareStatement(
                        "SELECT COUNT(*) FROM flyway_ext_test_history WHERE version = '1' AND success = TRUE"
                    )
                    .use { stmt -> stmt.executeQuery().use { rs -> rs.next() && rs.getInt(1) >= 1 } }
            assertTrue(extHasV1, "Extension history table must contain a successful V1 row")

            // The extension migration actually ran (its table exists).
            val extTableExists = conn.metaData.getTables(null, null, "ext_marker", null).use { rs -> rs.next() }
            assertTrue(extTableExists, "Extension migration must have created the ext_marker table")
        }
    }
}
