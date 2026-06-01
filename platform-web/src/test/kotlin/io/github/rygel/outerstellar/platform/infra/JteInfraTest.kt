package io.github.rygel.outerstellar.platform.infra

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
}
