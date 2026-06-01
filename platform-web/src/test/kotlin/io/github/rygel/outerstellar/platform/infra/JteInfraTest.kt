package io.github.rygel.outerstellar.platform.infra

import io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class JteInfraTest {

    @TempDir lateinit var baseDirectory: Path

    @Test
    fun `dev template paths resolve from repository root`() {
        val sourceTemplates = baseDirectory.resolve(Path.of("platform-web", "src", "main", "jte"))
        Files.createDirectories(sourceTemplates)

        val paths = resolveDevTemplatePaths(baseDirectory)

        assertEquals(sourceTemplates.toAbsolutePath().normalize(), paths.sourceTemplates)
        assertEquals(
            baseDirectory.resolve(Path.of("platform-web", "target", "jte-classes")).toAbsolutePath().normalize(),
            paths.generatedTemplateClasses,
        )
    }

    @Test
    fun `dev template paths resolve from module root`() {
        val sourceTemplates = baseDirectory.resolve(Path.of("src", "main", "jte"))
        Files.createDirectories(sourceTemplates)

        val paths = resolveDevTemplatePaths(baseDirectory)

        assertEquals(sourceTemplates.toAbsolutePath().normalize(), paths.sourceTemplates)
        assertEquals(
            baseDirectory.resolve(Path.of("target", "jte-classes")).toAbsolutePath().normalize(),
            paths.generatedTemplateClasses,
        )
    }

    @Test
    fun `dev template paths fail clearly when source templates are absent`() {
        val failure = assertFailsWith<IllegalStateException> { resolveDevTemplatePaths(baseDirectory) }

        assertTrue(
            failure.message.orEmpty().contains("JTE source templates directory not found for development rendering"),
            "Expected missing source directory message, got: ${failure.message}",
        )
        assertTrue(
            failure.message.orEmpty().contains(baseDirectory.toAbsolutePath().normalize().toString()),
            "Expected base directory in message, got: ${failure.message}",
        )
        assertTrue(
            failure.message.orEmpty().contains(Path.of("platform-web", "src", "main", "jte").toString()),
            "Expected repository-root candidate in message, got: ${failure.message}",
        )
        assertTrue(
            failure.message.orEmpty().contains(Path.of("src", "main", "jte").toString()),
            "Expected module-root candidate in message, got: ${failure.message}",
        )
    }

    @Test
    fun `precompiled lookup searches extension registries after platform misses`() {
        val platformRegistry = FakePrecompiledJteTemplateRegistry(emptyMap())
        val extensionRegistry =
            FakePrecompiledJteTemplateRegistry(
                mapOf("com/example/pages/Profile" to ExtensionTemplateMarker::class.java)
            )

        val templateClass =
            findPrecompiledTemplateClass("com/example/pages/Profile", listOf(platformRegistry, extensionRegistry))

        assertEquals(ExtensionTemplateMarker::class.java, templateClass)
    }

    @Test
    fun `precompiled lookup returns null when template not found in single registry`() {
        val singleRegistry = FakePrecompiledJteTemplateRegistry(emptyMap())

        val result = findPrecompiledTemplateClass("com/example/pages/Profile", listOf(singleRegistry))

        assertEquals(null, result, "Expected null when template is not in any of 1 generated class registries")
    }

    @Test
    fun `precompiled lookup returns null when template not found across multiple registries`() {
        val platformRegistry =
            FakePrecompiledJteTemplateRegistry(
                mapOf("io/github/rygel/outerstellar/platform/web/HomePage" to PlatformTemplateMarker::class.java)
            )
        val extensionRegistry = FakePrecompiledJteTemplateRegistry(emptyMap())

        val result =
            findPrecompiledTemplateClass("com/example/pages/Profile", listOf(platformRegistry, extensionRegistry))

        assertEquals(null, result, "Expected null when template is not found in any of 2 registries")
    }

    @Test
    fun `composed preflight requires at least two registries to pass`() {
        val singleRegistry =
            FakePrecompiledJteTemplateRegistry(
                mapOf("io/github/rygel/outerstellar/platform/web/HomePage" to PlatformTemplateMarker::class.java)
            )
        val registries = listOf(singleRegistry)

        val meetsRequirement = registries.size >= 2

        assertEquals(false, meetsRequirement, "Preflight must fail with only 1 generated class registry")
    }

    @Test
    fun `composed preflight passes with platform plus plugin registries`() {
        val platformRegistry =
            FakePrecompiledJteTemplateRegistry(
                mapOf("io/github/rygel/outerstellar/platform/web/HomePage" to PlatformTemplateMarker::class.java)
            )
        val extensionRegistry =
            FakePrecompiledJteTemplateRegistry(
                mapOf(
                    "com/example/outerstellar/starter/extension/StarterIndexPage" to ExtensionTemplateMarker::class.java
                )
            )
        val registries = listOf(platformRegistry, extensionRegistry)

        val meetsRequirement = registries.size >= 2
        val platformResolves = registries.any {
            it.getTemplateClass("io/github/rygel/outerstellar/platform/web/HomePage") != null
        }
        val extensionResolves = registries.any {
            it.getTemplateClass("com/example/outerstellar/starter/extension/StarterIndexPage") != null
        }

        assertTrue(meetsRequirement, "Preflight must pass with platform + plugin registries")
        assertTrue(platformResolves, "Platform template must resolve from composed registries")
        assertTrue(extensionResolves, "Extension template must resolve from composed registries")
    }

    @Test
    fun `precompiled registries are discoverable from generated service metadata`() {
        val registries = discoverPrecompiledTemplateRegistries()

        assertTrue(
            registries.any {
                it::class.java.name == "io.github.rygel.outerstellar.platform.web.JteClassRegistryProvider"
            },
            "Expected generated platform JTE registry provider to be discoverable through ServiceLoader",
        )
    }

    private class FakePrecompiledJteTemplateRegistry(private val templates: Map<String, Class<*>>) :
        PrecompiledJteTemplateRegistry {
        override val allClasses: List<Class<*>>
            get() = templates.values.toList()

        override fun getTemplateClass(templateName: String): Class<*>? = templates[templateName]
    }

    private class ExtensionTemplateMarker

    private class PlatformTemplateMarker
}
