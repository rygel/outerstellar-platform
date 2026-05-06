package io.github.rygel.outerstellar.platform.infra

import gg.jte.TemplateEngine
import gg.jte.html.OwaspHtmlTemplateOutput
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.resolve.ResourceCodeResolver
import gg.jte.runtime.Template
import java.nio.file.Files
import java.nio.file.Path
import org.http4k.core.ContentType
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.ViewNotFound

private const val PLATFORM_JTE_PACKAGE = "gg.jte.generated.precompiled.outerstellar"

fun TemplateRenderer.render(viewModel: ViewModel, status: Status = Status.OK): Response =
    Response(status)
        .header("content-type", ContentType.TEXT_HTML.toHeaderValue() + "; charset=utf-8")
        .body(this(viewModel))

fun createRenderer(): TemplateRenderer {
    val isProduction = System.getProperty("jte.production") == "true" || System.getenv("JTE_PRODUCTION") == "true"

    if (isProduction) {
        System.err.println(
            "JTE: production mode, JteClassRegistry has ${JteClassRegistry.allClasses.size} template classes"
        )
        ensureTemplateClassesLoaded()
    }

    val templateEngine =
        if (!isProduction) {
            val applicationClassLoader = Thread.currentThread().contextClassLoader
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
        } else {
            null
        }

    return if (isProduction) renderUsingPrecompiledRegistry() else renderUsing { requireNotNull(templateEngine) }
}

private fun ensureTemplateClassesLoaded() {
    val loader = Thread.currentThread().contextClassLoader
    val resource =
        loader.getResource("META-INF/native-image/io.github.rygel/outerstellar-platform-web/reachability-metadata.json")
    if (resource != null) {
        System.err.println("JTE: reachability-metadata found at ${resource.protocol}")
    } else {
        System.err.println("JTE: reachability-metadata NOT FOUND on classpath")
    }

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
    System.err.println("JTE: preloaded $loaded template classes, $failed not found")
}

private fun renderUsing(engineProvider: () -> TemplateEngine): TemplateRenderer = { viewModel: ViewModel ->
    val templateName = "${viewModel.template()}.kte"
    val templateEngine = engineProvider()

    JteClassRegistry.getTemplateClass(viewModel.template())?.let { cls ->
        System.err.println("JTE: Template ${viewModel.template()} resolved to class ${cls.name}")
    }

    try {
        StringOutput().also { templateEngine.render(templateName, viewModel, it) }.toString()
    } catch (e: Exception) {
        val className = "$PLATFORM_JTE_PACKAGE.${viewModel.template().replace('/', '.')}"
        val fullClassName = className.replace(".kte", "") + "Generated"
        System.err.println("=== JTE DIAGNOSTIC ===")
        System.err.println("  templateName: $templateName")
        System.err.println("  expected class: $fullClassName")
        System.err.println("  allKnownClasses: ${JteClassRegistry.allClasses.map { it.simpleName }}")
        try {
            val cls = Class.forName(fullClassName)
            System.err.println("  Class.forName: OK (${cls.name})")
            val field = cls.getField("JTE_NAME")
            System.err.println("  JTE_NAME field: ${field.get(null)}")
        } catch (cf: ClassNotFoundException) {
            System.err.println("  Class.forName: NOT FOUND")
        } catch (cf: NoSuchFieldException) {
            System.err.println("  JTE_NAME field: NOT FOUND")
        } catch (cf: Exception) {
            System.err.println("  Class.forName error: ${cf.javaClass.name}: ${cf.message}")
        }
        try {
            val loader = Thread.currentThread().contextClassLoader
            System.err.println("  classLoader: ${loader.javaClass.name}")
            val loaded = loader.loadClass(fullClassName)
            System.err.println("  loader.loadClass: OK (${loaded.name})")
        } catch (lc: Exception) {
            System.err.println("  loader.loadClass error: ${lc.javaClass.name}: ${lc.message}")
        }
        System.err.println("  original error: ${e.javaClass.name}: ${e.message}")
        System.err.println("=== END DIAGNOSTIC ===")
        throw ViewNotFound(viewModel)
    }
}

private fun renderUsingPrecompiledRegistry(): TemplateRenderer = { viewModel: ViewModel ->
    val templateName = "${viewModel.template()}.kte"
    val templateClass = JteClassRegistry.getTemplateClass(viewModel.template())

    if (templateClass == null) {
        System.err.println("JTE: Template ${viewModel.template()} not found in generated class registry")
        throw ViewNotFound(viewModel)
    }

    try {
        val output = StringOutput()
        Template(templateName, templateClass).render(OwaspHtmlTemplateOutput(output), null, viewModel)
        output.toString()
    } catch (e: Exception) {
        System.err.println("=== JTE REGISTRY DIAGNOSTIC ===")
        System.err.println("  templateName: $templateName")
        System.err.println("  resolved class: ${templateClass.name}")
        System.err.println("  original error: ${e.javaClass.name}: ${e.message}")
        System.err.println("=== END DIAGNOSTIC ===")
        throw ViewNotFound(viewModel)
    }
}
