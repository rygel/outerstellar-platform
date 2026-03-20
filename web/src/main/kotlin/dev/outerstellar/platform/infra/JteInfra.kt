package dev.outerstellar.platform.infra

import gg.jte.CodeResolver
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

/**
 * Composite resolver that tries [primary] first, falling back to [fallback]. This lets downstream
 * projects resolve their own templates from disk while picking up shared platform templates from
 * the classpath JAR.
 */
class CompositeCodeResolver(private val primary: CodeResolver, private val fallback: CodeResolver) :
    CodeResolver {
    override fun resolve(name: String): String =
        if (primary.exists(name)) primary.resolve(name) else fallback.resolve(name)

    override fun exists(name: String): Boolean = primary.exists(name) || fallback.exists(name)

    override fun getLastModified(name: String): Long =
        if (primary.exists(name)) primary.getLastModified(name) else fallback.getLastModified(name)

    override fun resolveAllTemplateNames(): MutableList<String> {
        val names = LinkedHashSet(primary.resolveAllTemplateNames())
        names.addAll(fallback.resolveAllTemplateNames())
        return names.toMutableList()
    }
}

fun TemplateRenderer.render(viewModel: ViewModel, status: Status = Status.OK): Response =
    Response(status)
        .header("content-type", ContentType.TEXT_HTML.toHeaderValue() + "; charset=utf-8")
        .body(this(viewModel))

fun createRenderer(): TemplateRenderer {
    val isProduction =
        System.getProperty("jte.production") == "true" || System.getenv("JTE_PRODUCTION") == "true"
    val applicationClassLoader = Thread.currentThread().contextClassLoader

    val templateEngine =
        if (isProduction) {
            TemplateEngine.createPrecompiled(gg.jte.ContentType.Html)
        } else {
            val projectDirectory = Path.of(System.getProperty("user.dir"))
            val sourceTemplates = projectDirectory.resolve(Path.of("web", "src", "main", "jte"))
            val generatedTemplateClasses =
                projectDirectory.resolve(Path.of("web", "target", "jte-classes"))

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

private fun renderUsing(engineProvider: () -> TemplateEngine): TemplateRenderer =
    { viewModel: ViewModel ->
        val templateName = "${viewModel.template()}.kte"
        val templateEngine = engineProvider()

        if (templateEngine.hasTemplate(templateName)) {
            StringOutput().also { templateEngine.render(templateName, viewModel, it) }.toString()
        } else {
            throw ViewNotFound(viewModel)
        }
    }
