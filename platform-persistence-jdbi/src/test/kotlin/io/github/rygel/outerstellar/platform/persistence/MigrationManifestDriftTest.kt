package io.github.rygel.outerstellar.platform.persistence

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MigrationManifestDriftTest {

    @Test
    fun `migrations index lists all sql files in migration directory`() {
        val migrationDir = File("src/main/resources/db/migration/platform")
        assertTrue(migrationDir.isDirectory, "Migration directory must exist")

        val sqlFiles =
            migrationDir
                .listFiles()
                ?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") }
                ?.map { it.name.removeSuffix(".sql") }
                ?.sorted() ?: emptyList()

        assertTrue(sqlFiles.isNotEmpty(), "There should be at least one migration SQL file")

        val manifestFile = File(migrationDir, "migrations.index")
        assertTrue(manifestFile.isFile, "migrations.index must exist in migration directory")

        val manifestEntries = manifestFile.readLines().filter { it.isNotBlank() }.sorted()

        assertEquals(
            sqlFiles,
            manifestEntries,
            "migrations.index must list exactly the V*.sql files (without .sql extension). " +
                "Run: ./scripts/generate-migration-manifest.ps1 -MigrationDir src/main/resources/db/migration/platform " +
                "-OutputFile src/main/resources/db/migration/platform/migrations.index",
        )
    }

    /**
     * ADR-0004 guard: platform migrations must live under a namespaced subdirectory (db/migration/platform/) and the
     * shared db/migration root must contain no V*.sql. If a top-level migration reappears, Flyway — which scans a
     * classpath: location recursively — would pick up foreign migration trees under sibling db/migration/<owner>/
     * subtrees and collide on V1, breaking host-app boot (#601).
     */
    @Test
    fun `platform migrations are namespaced and the shared db_migration root has no sql files`() {
        val sharedRoot = File("src/main/resources/db/migration")
        assertTrue(sharedRoot.isDirectory, "Shared db/migration root must exist")

        val strayTopLevelMigrations =
            sharedRoot.listFiles()?.filter { it.isFile && it.name.endsWith(".sql") && it.name.startsWith("V") }
                ?: emptyList()

        assertTrue(
            strayTopLevelMigrations.isEmpty(),
            "db/migration/ must not contain top-level V*.sql (ADR-0004). Found: " +
                strayTopLevelMigrations.joinToString(", ") { it.name } +
                ". Platform migrations belong under db/migration/platform/.",
        )

        val namespacedDir = File(sharedRoot, "platform")
        assertTrue(namespacedDir.isDirectory, "Namespaced db/migration/platform/ directory must exist")
        val namespacedMigrations =
            namespacedDir.listFiles()?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") } ?: emptyList()
        assertTrue(namespacedMigrations.isNotEmpty(), "db/migration/platform/ must contain the platform's migrations")
    }
}
