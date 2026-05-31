package io.github.rygel.outerstellar.platform.infra

import gg.jte.CodeResolver
import gg.jte.TemplateEngine
import gg.jte.html.OwaspHtmlTemplateOutput
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.resolve.ResourceCodeResolver
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

    val templateEngine =
        if (!isProduction) {
            val applicationClassLoader = Thread.currentThread().contextClassLoader
            val sourceDir =
                System.getProperty("jte.sourceDir") ?: System.getenv("JTE_SOURCE_DIR") ?: System.getProperty("user.dir")
            val projectDirectory = Path.of(sourceDir)
            val sourceTemplates = findSourceTemplateDirectory(projectDirectory)
            val generatedTemplateClasses = findGeneratedTemplateDirectory(projectDirectory, sourceTemplates)

            val resolver =
                if (sourceTemplates != null) {
                    val directoryResolver = DirectoryCodeResolver(sourceTemplates)
                    val classpathFallback = ResourceCodeResolver(".")
                    CompositeCodeResolver(directoryResolver, classpathFallback)
                } else {
                    ResourceCodeResolver(".")
                }

            TemplateEngine.create(
                resolver,
                generatedTemplateClasses,
                gg.jte.ContentType.Html,
                applicationClassLoader,
            ) to resolver
        } else {
            null
        }

    return if (isProduction) {
        renderUsingPrecompiledRegistry()
    } else {
        val (engine, resolver) = requireNotNull(templateEngine)
        renderUsingDevEngineWithPrecompiledFallback(engine, resolver)
    }
}

private fun findSourceTemplateDirectory(projectDirectory: Path): Path? =
    listOf(
            projectDirectory.resolve(Path.of("src", "main", "jte")),
            projectDirectory.resolve(Path.of("platform-web", "src", "main", "jte")),
            projectDirectory.resolve(Path.of("web", "src", "main", "jte")),
        )
        .firstOrNull(Files::isDirectory)

private fun findGeneratedTemplateDirectory(projectDirectory: Path, sourceTemplates: Path?): Path {
    val templateProjectDirectory = sourceTemplates?.parent?.parent?.parent
    return (templateProjectDirectory ?: projectDirectory).resolve(Path.of("target", "jte-classes"))
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

internal fun renderUsingDevEngineWithPrecompiledFallback(
    templateEngine: TemplateEngine,
    resolver: CodeResolver,
): TemplateRenderer = { viewModel: ViewModel ->
    val templateName = "${viewModel.template()}.kte"

    if (!resolver.exists(templateName) && JteClassRegistry.getTemplateClass(viewModel.template()) != null) {
        renderUsingPrecompiledRegistry()(viewModel)
    } else {
        renderUsing { templateEngine }(viewModel)
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
