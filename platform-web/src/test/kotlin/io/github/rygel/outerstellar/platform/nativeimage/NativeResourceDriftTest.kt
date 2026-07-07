package io.github.rygel.outerstellar.platform.nativeimage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NativeResourceDriftTest {

    private val metadataFile =
        File(
            "src/main/resources/META-INF/native-image/" +
                "io.github.rygel/outerstellar-platform-web/reachability-metadata.json"
        )

    private val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    private val dependencyGlobs =
        listOf(
            "META-INF/org/http4k/core/mime.types",
            "META-INF/services/ch.qos.logback.classic.spi.Configurator",
            "META-INF/services/io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry",
            "META-INF/services/io.opentelemetry.context.ContextStorageProvider",
            "META-INF/services/java.net.spi.InetAddressResolverProvider",
            "META-INF/services/java.net.spi.URLStreamHandlerProvider",
            "META-INF/services/java.nio.channels.spi.SelectorProvider",
            "META-INF/services/java.sql.Driver",
            "META-INF/services/java.time.zone.ZoneRulesProvider",
            "META-INF/services/javax.xml.parsers.SAXParserFactory",
            "META-INF/services/org.flywaydb.core.extensibility.Extension",
            "META-INF/services/org.slf4j.spi.SLF4JServiceProvider",
            "ch/qos/logback/classic/logback-classic-version.properties",
            "ch/qos/logback/core/logback-core-version.properties",
            "org/flywaydb/core/internal/version.txt",
            "org/postgresql/driverconfig.properties",
            "prometheus.properties",
        )

    private fun scanProjectGlobs(): Set<String> {
        val globs = sortedSetOf<String>()
        scanMigrations(globs)
        scanI18nBundles(globs)
        scanConfigFiles(globs)
        scanStaticAssets(globs)
        scanLogback(globs)
        return globs
    }

    private fun scanMigrations(globs: MutableSet<String>) {
        // ADR-0004: platform migrations are namespaced under db/migration/platform/ so Flyway never scans a
        // shared parent and collides on V1 with host/sibling migration trees (#601).
        globs.add("db/migration/platform")
        globs.add("db/migration/platform/migrations.index")
        val dir = File("../platform-persistence-jdbi/src/main/resources/db/migration/platform")
        assertTrue(dir.isDirectory, "Migration directory must exist")
        dir.listFiles()
            ?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") }
            ?.forEach { globs.add("db/migration/platform/${it.name}") }
    }

    private fun scanI18nBundles(globs: MutableSet<String>) {
        val dir = File("../platform-core/src/main/resources")
        assertTrue(dir.isDirectory, "platform-core resources must exist")
        dir.listFiles()?.filter { it.name.matches(Regex("messages.*\\.properties")) }?.forEach { globs.add(it.name) }
    }

    private fun scanConfigFiles(globs: MutableSet<String>) {
        globs.add("application.yaml")
        globs.add("application-*.yaml")
    }

    private fun scanStaticAssets(globs: MutableSet<String>) {
        val staticDir = File("src/main/resources/static")
        if (!staticDir.isDirectory) return
        scanDirectory(staticDir, "static", globs)
    }

    private fun scanDirectory(dir: File, prefix: String, globs: MutableSet<String>) {
        dir.listFiles()
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val path = "$prefix/${file.name}"
                if (file.isDirectory) {
                    scanDirectory(file, path, globs)
                } else {
                    globs.add(path)
                }
            }
    }

    private fun scanLogback(globs: MutableSet<String>) {
        if (File("../platform-core/src/main/resources/logback.xml").isFile) {
            globs.add("logback.xml")
        }
    }

    private fun buildExpectedResources(): ArrayNode {
        val resources = mapper.createArrayNode()

        for (glob in scanProjectGlobs()) {
            resources.add(mapper.createObjectNode().put("glob", glob))
        }

        for (glob in dependencyGlobs.sorted()) {
            resources.add(mapper.createObjectNode().put("glob", glob))
        }

        resources.add(
            mapper
                .createObjectNode()
                .put("module", "java.logging")
                .put("glob", "sun/util/logging/resources/logging_en.properties")
        )
        resources.add(
            mapper
                .createObjectNode()
                .put("module", "java.logging")
                .put("glob", "sun/util/logging/resources/logging_en_GB.properties")
        )
        resources.add(
            mapper.createObjectNode().put("module", "jdk.jfr").put("glob", "jdk/jfr/internal/types/metadata.bin")
        )
        resources.add(mapper.createObjectNode().put("bundle", "sun.util.logging.resources.logging"))

        return resources
    }

    @Test
    fun `resources section matches classpath`() {
        assertTrue(metadataFile.isFile, "reachability-metadata.json must exist")

        val expected = buildExpectedResources()
        val tree = mapper.readTree(metadataFile)
        val current = tree["resources"]
        assertTrue(current != null && current.isArray, "resources array must exist")

        val expectedSet = expected.map { it.toString() }.toSet()
        val currentSet = current.map { it.toString() }.toSet()

        if (expectedSet != currentSet) {
            val fixed = mapper.readTree(metadataFile) as ObjectNode
            fixed.set<JsonNode>("resources", expected)
            mapper.writeValue(metadataFile, fixed)
            assertTrue(false, "Resource entries regenerated — review diff and commit")
        }
    }

    @Test
    fun `flyway jackson-copied extensions are registered for reflection`() {
        assertTrue(metadataFile.isFile, "reachability-metadata.json must exist")

        val reflection = mapper.readTree(metadataFile)["reflection"]
        assertTrue(reflection != null && reflection.isArray, "reflection array must exist")

        val reflectionByType = reflection.filter { it["type"]?.isTextual == true }.associateBy { it["type"].asText() }
        val requiredTypes =
            setOf(
                "org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension",
                "org.flywaydb.core.internal.command.clean.CleanModeConfigurationExtension",
                "org.flywaydb.core.internal.command.clean.CleanModel",
                "org.flywaydb.core.internal.command.clean.SchemaModel",
                "org.flywaydb.core.internal.publishing.PublishingConfigurationExtension",
                "org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension",
            )

        val missing = requiredTypes - reflectionByType.keys
        assertTrue(missing.isEmpty(), "Flyway configuration extensions missing native reflection: $missing")

        val jacksonCopiedTypes =
            setOf(
                "org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension",
                "org.flywaydb.core.internal.command.clean.CleanModeConfigurationExtension",
                "org.flywaydb.core.internal.command.clean.CleanModel",
                "org.flywaydb.core.internal.command.clean.SchemaModel",
                "org.flywaydb.core.internal.publishing.PublishingConfigurationExtension",
                "org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension",
                "org.flywaydb.database.postgresql.TransactionalModel",
            )
        val notEnumerable = jacksonCopiedTypes.filterNot {
            reflectionByType[it]?.get("allPublicMethods")?.asBoolean(false) == true
        }
        assertTrue(
            notEnumerable.isEmpty(),
            "Flyway Jackson DTOs must expose public methods in native image: $notEnumerable",
        )
    }
}
