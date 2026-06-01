package io.github.rygel.outerstellar.platform.infra

import gg.jte.TemplateEngine
import gg.jte.html.OwaspHtmlTemplateOutput
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.runtime.Template
import io.github.rygel.outerstellar.platform.RuntimeConfig
import io.github.rygel.outerstellar.platform.web.JteClassRegistry
import java.nio.file.Files
import java.nio.file.Path
import org.http4k.core.ContentType
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.ViewNotFound
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("JteInfra")

fun TemplateRenderer.render(viewModel: ViewModel, status: Status = Status.OK): Response =
    Response(status)
        .header("content-type", ContentType.TEXT_HTML.toHeaderValue() + "; charset=utf-8")
        .body(this(viewModel))

fun createRenderer(runtime: RuntimeConfig = RuntimeConfig()): TemplateRenderer {
    val isProduction = System.getProperty("jte.production") == "true" || System.getenv("JTE_PRODUCTION") == "true"
    val doPreload = isProduction || runtime.jtePreloadEnabled

    if (doPreload) {
        logger.info("Production mode: JteClassRegistry has {} template classes", JteClassRegistry.allClasses.size)
        ensureTemplateClassesLoaded()
    }

    if (isProduction) {
        return renderUsingPrecompiledRegistry()
    }

    val applicationClassLoader = Thread.currentThread().contextClassLoader
    val sourceDir =
        System.getProperty("jte.sourceDir") ?: System.getenv("JTE_SOURCE_DIR") ?: System.getProperty("user.dir")
    val projectDirectory = Path.of(sourceDir)
    val paths = resolveDevTemplatePaths(projectDirectory)
    logger.info("Development mode: loading JTE source templates from {}", paths.sourceTemplates)

    val templateEngine =
        TemplateEngine.create(
            DirectoryCodeResolver(paths.sourceTemplates),
            paths.generatedTemplateClasses,
            gg.jte.ContentType.Html,
            applicationClassLoader,
        )
    return renderUsing { templateEngine }
}

internal data class DevTemplatePaths(val sourceTemplates: Path, val generatedTemplateClasses: Path)

internal fun resolveDevTemplatePaths(baseDirectory: Path): DevTemplatePaths {
    val normalizedBase = baseDirectory.toAbsolutePath().normalize()
    val candidates =
        listOf(
            DevTemplatePaths(
                normalizedBase.resolve(Path.of("platform-web", "src", "main", "jte")),
                normalizedBase.resolve(Path.of("platform-web", "target", "jte-classes")),
            ),
            DevTemplatePaths(
                normalizedBase.resolve(Path.of("src", "main", "jte")),
                normalizedBase.resolve(Path.of("target", "jte-classes")),
            ),
        )

    return candidates.firstOrNull { Files.isDirectory(it.sourceTemplates) }
        ?: throw IllegalStateException(
            "JTE source templates directory not found for development rendering. " +
                "Base directory: $normalizedBase. Checked: " +
                candidates.joinToString { it.sourceTemplates.toString() }
        )
}

private fun ensureTemplateClassesLoaded() {
    var loaded = 0
    var failed = 0
    for (className in JteClassRegistry.allClasses.map { it.name }) {
        try {
            Class.forName(className)
            loaded++
        } catch (_: ClassNotFoundException) {
            failed++
        }
    }
    logger.info("Preloaded {} template classes, {} not found", loaded, failed)
}

private fun renderUsing(engineProvider: () -> TemplateEngine): TemplateRenderer = { viewModel: ViewModel ->
    val templateName = "${viewModel.template()}.kte"
    val templateEngine = engineProvider()

    try {
        StringOutput().also { templateEngine.render(templateName, viewModel, it) }.toString()
    } catch (e: IllegalArgumentException) {
        logger.error("JTE render failed for template {}: {}", templateName, e.message)
        throw ViewNotFound(viewModel)
    } catch (e: IllegalStateException) {
        logger.error("JTE render failed for template {}: {}", templateName, e.message)
        throw ViewNotFound(viewModel)
    }
}

private fun renderUsingPrecompiledRegistry(): TemplateRenderer = { viewModel: ViewModel ->
    val templateName = "${viewModel.template()}.kte"
    val templateClass = JteClassRegistry.getTemplateClass(viewModel.template())

    if (templateClass == null) {
        logger.error("Template {} not found in generated class registry", viewModel.template())
        throw ViewNotFound(viewModel)
    }

    try {
        val output = StringOutput()
        Template(templateName, templateClass).render(OwaspHtmlTemplateOutput(output), null, viewModel)
        output.toString()
    } catch (e: IllegalArgumentException) {
        logger.error("JTE registry render failed for {} (class {}): {}", templateName, templateClass.name, e.message)
        throw ViewNotFound(viewModel)
    } catch (e: IllegalStateException) {
        logger.error("JTE registry render failed for {} (class {}): {}", templateName, templateClass.name, e.message)
        throw ViewNotFound(viewModel)
    }
}
