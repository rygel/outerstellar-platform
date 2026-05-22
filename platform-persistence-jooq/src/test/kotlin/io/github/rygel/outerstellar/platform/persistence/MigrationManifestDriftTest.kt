package io.github.rygel.outerstellar.platform.persistence

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MigrationManifestDriftTest {

    @Test
    fun `migrations index lists all sql files in migration directory`() {
        val migrationDir = File("src/main/resources/db/migration")
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
                "Run: ./scripts/generate-migration-manifest.ps1 -MigrationDir src/main/resources/db/migration " +
                "-OutputFile src/main/resources/db/migration/migrations.index",
        )
    }

    @Test
    fun `reachability metadata includes all migration resources`() {
        val migrationDir = File("src/main/resources/db/migration")
        val sqlFiles =
            migrationDir.listFiles()?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") }?.map { it.name }
                ?: emptyList()

        val metadataFile =
            File(
                "../platform-web/src/main/resources/META-INF/native-image/io.github.rygel/outerstellar-platform-web/reachability-metadata.json"
            )
        if (!metadataFile.isFile) return

        val metadata = metadataFile.readText()

        for (sqlFile in sqlFiles) {
            assertTrue(
                metadata.contains(sqlFile),
                "reachability-metadata.json must include db/migration/$sqlFile. " + "Add it to the resources section.",
            )
        }

        assertTrue(
            metadata.contains("migrations.index"),
            "reachability-metadata.json must include db/migration/migrations.index",
        )
    }
}
