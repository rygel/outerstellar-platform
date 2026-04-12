package io.github.rygel.outerstellar.platform.infra

import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.resolve.ResourceCodeResolver
import java.nio.file.Files
import java.nio.file.Path
import org.http4k.core.ContentType
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.ViewNotFound

private const val PLATFORM_JTE_PACKAGE = "gg.jte.generated.precompiled.outerstellar"

private const val PLATFORM_JTE_PACKAGE = "gg.jte.generated.precompiled.outerstellar"

fun TemplateRenderer.render(viewModel: ViewModel, status: Status = Status.OK): Response =
    Response(status)
        .header("content-type", ContentType.TEXT_HTML.toHeaderValue() + "; charset=utf-8")
        .body(this(viewModel))

fun createRenderer(): TemplateRenderer {
    val isProduction = System.getProperty("jte.production") == "true" || System.getenv("JTE_PRODUCTION") == "true"
    val applicationClassLoader = Thread.currentThread().contextClassLoader

    val templateEngine =
        if (isProduction) {
            TemplateEngine.createPrecompiled(null, gg.jte.ContentType.Html, null, PLATFORM_JTE_PACKAGE)
        } else {
            val projectDirectory = Path.of(System.getProperty("user.dir"))
            val sourceTemplates = projectDirectory.resolve(Path.of("web", "src", "main", "jte"))
            val generatedTemplateClasses = projectDirectory.resolve(Path.of("web", "target", "jte-classes"))

            if (Files.isDirectory(sourceTemplates)) {
                val directoryResolver = DirectoryCodeResolver(sourceTemplates)
                val classpathFallback = ResourceCodeResolver(".")
                val resolver = CompositeCodeResolver(directoryResolver, classpathFallback)
                TemplateEngine.create(
                    resolver,
                    generatedTemplateClasses,
                    gg.jte.ContentType.Html,
                    applicationClassLoader,
                )
            } else {
                TemplateEngine.create(
                    ResourceCodeResolver("."),
                    generatedTemplateClasses,
                    gg.jte.ContentType.Html,
                    applicationClassLoader,
                )
            }
        }

    return renderUsing { templateEngine }
}

private fun renderUsing(engineProvider: () -> TemplateEngine): TemplateRenderer = { viewModel: ViewModel ->
    val templateName = "${viewModel.template()}.kte"
    val templateEngine = engineProvider()

    if (templateEngine.hasTemplate(templateName)) {
        StringOutput().also { templateEngine.render(templateName, viewModel, it) }.toString()
    } else {
        throw ViewNotFound(viewModel)
    }
}
