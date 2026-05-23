package io.github.rygel.outerstellar.platform.nativeimage

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NativeResourceDriftTest {

    private val metadataFile =
        File(
            "src/main/resources/META-INF/native-image/" +
                "io.github.rygel/outerstellar-platform-web/reachability-metadata.json"
        )

    private val mapper = ObjectMapper()

    private fun resourceGlobs(): Set<String> {
        assertTrue(metadataFile.isFile, "reachability-metadata.json must exist")
        val tree = mapper.readTree(metadataFile)
        val resources = tree["resources"]
        assertTrue(resources != null && resources.isArray, "resources array must exist")
        return resources.map { it["glob"]?.asText() ?: "" }.filter { it.isNotEmpty() }.toSet()
    }

    @Test
    fun `all migration sql files are listed in reachability metadata`() {
        val migrationDir = File("../platform-persistence-jdbi/src/main/resources/db/migration")
        assertTrue(migrationDir.isDirectory, "Migration directory must exist")

        val sqlFiles =
            migrationDir
                .listFiles()
                ?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") }
                ?.map { "db/migration/${it.name}" } ?: emptyList()

        assertTrue(sqlFiles.isNotEmpty(), "There should be at least one migration")

        val globs = resourceGlobs()
        val missing = sqlFiles.filter { it !in globs }

        assertTrue(
            missing.isEmpty(),
            "These migration files are not in reachability-metadata.json resources:\n" +
                missing.joinToString("\n  ") +
                "\nRun: scripts/generate-reachability-resources.ps1 to regenerate",
        )
    }

    @Test
    fun `i18n bundles are listed in reachability metadata`() {
        val messagesDir = File("../platform-core/src/main/resources")
        assertTrue(messagesDir.isDirectory, "platform-core resources must exist")

        val bundles =
            messagesDir.listFiles()?.filter { it.name.matches(Regex("messages.*\\.properties")) }?.map { it.name }
                ?: emptyList()

        assertTrue(bundles.isNotEmpty(), "There should be at least one messages bundle")

        val globs = resourceGlobs()
        val missing = bundles.filter { it !in globs }

        assertTrue(
            missing.isEmpty(),
            "These i18n bundles are not in reachability-metadata.json resources:\n" +
                missing.joinToString("\n  ") +
                "\nRun: scripts/generate-reachability-resources.ps1 to regenerate",
        )
    }

    @Test
    fun `logback xml is listed in reachability metadata`() {
        val globs = resourceGlobs()
        assertTrue("logback.xml" in globs, "logback.xml must be listed in reachability-metadata.json resources")
    }

    @Test
    fun `migration manifest is listed in reachability metadata`() {
        val globs = resourceGlobs()
        assertTrue(
            "db/migration/migrations.index" in globs,
            "db/migration/migrations.index must be listed in reachability-metadata.json resources",
        )
    }

    @Test
    fun `application yaml is listed in reachability metadata`() {
        val globs = resourceGlobs()
        assertTrue(
            "application.yaml" in globs,
            "application.yaml must be listed in reachability-metadata.json resources",
        )
    }

    @Test
    fun `no stale migration entries exist in reachability metadata`() {
        val migrationDir = File("../platform-persistence-jdbi/src/main/resources/db/migration")
        val actualSqlFiles =
            migrationDir
                .listFiles()
                ?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") }
                ?.map { "db/migration/${it.name}" }
                ?.toSet() ?: emptySet()

        val globs = resourceGlobs()
        val metadataMigrations = globs.filter { it.startsWith("db/migration/V") && it.endsWith(".sql") }

        val stale = metadataMigrations.filter { it !in actualSqlFiles }

        assertTrue(
            stale.isEmpty(),
            "These migration entries in reachability-metadata.json don't match actual files:\n" +
                stale.joinToString("\n  ") +
                "\nRemove them from reachability-metadata.json",
        )
    }
}
