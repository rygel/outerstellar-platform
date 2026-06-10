package io.github.rygel.outerstellar.platform.infra

import gg.jte.TemplateEngine
import gg.jte.html.OwaspHtmlTemplateOutput
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.runtime.Template
import io.github.rygel.outerstellar.platform.RuntimeConfig
import io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry
import io.github.rygel.outerstellar.platform.web.JteClassRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
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
    val precompiledRegistries by lazy { discoverPrecompiledTemplateRegistries() }

    if (doPreload) {
        if (precompiledRegistries.isEmpty()) {
            logger.warn(
                "No PrecompiledJteTemplateRegistry implementations found via ServiceLoader. " +
                    "Ensure the JTE Maven plugin includes JteClassRegistryExtension and the " +
                    "META-INF/services file is generated. Templates will not resolve in production mode."
            )
        }
        logger.info(
            "Production mode: discovered {} JTE registries with {} template classes",
            precompiledRegistries.size,
            precompiledRegistries.sumOf { it.allClasses.size },
        )
        ensureTemplateClassesLoaded(precompiledRegistries)
    }

    if (isProduction) {
        return renderUsingPrecompiledRegistry(precompiledRegistries)
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

private object PlatformPrecompiledJteTemplateRegistry : PrecompiledJteTemplateRegistry {
    override val allClasses: List<Class<*>>
        get() = JteClassRegistry.allClasses

    override fun getTemplateClass(templateName: String): Class<*>? = JteClassRegistry.getTemplateClass(templateName)
}

internal fun discoverPrecompiledTemplateRegistries(
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader
): List<PrecompiledJteTemplateRegistry> {
    val discovered = ServiceLoader.load(PrecompiledJteTemplateRegistry::class.java, classLoader).toList()
    val discoveredPlatformRegistry = discovered.any { registry ->
        registry::class.java.name == "io.github.rygel.outerstellar.platform.web.JteClassRegistryProvider"
    }
    return if (discoveredPlatformRegistry) {
        discovered
    } else {
        listOf(PlatformPrecompiledJteTemplateRegistry) + discovered
    }
}

private fun ensureTemplateClassesLoaded(registries: List<PrecompiledJteTemplateRegistry>) {
    var loaded = 0
    var failed = 0
    for (className in registries.flatMap { it.allClasses }.map { it.name }.distinct()) {
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

internal fun findPrecompiledTemplateClass(
    templateName: String,
    registries: List<PrecompiledJteTemplateRegistry>,
): Class<*>? = registries.firstNotNullOfOrNull { registry -> registry.getTemplateClass(templateName) }

private fun renderUsingPrecompiledRegistry(registries: List<PrecompiledJteTemplateRegistry>): TemplateRenderer =
    { viewModel: ViewModel ->
        val templateName = "${viewModel.template()}.kte"
        val templateClass = findPrecompiledTemplateClass(viewModel.template(), registries)

        if (templateClass == null) {
            val availableTemplates = registries.flatMap { it.allClasses }.map { it.name }.sorted()
            logger.error(
                "Template {} not found in {} generated class registries ({} classes registered). " +
                    "Ensure the JTE Maven plugin includes JteClassRegistryExtension. Registered: {}",
                viewModel.template(),
                registries.size,
                availableTemplates.size,
                availableTemplates.takeLast(10),
            )
            throw ViewNotFound(viewModel)
        }

        try {
            val output = StringOutput()
            Template(templateName, templateClass).render(OwaspHtmlTemplateOutput(output), null, viewModel)
            output.toString()
        } catch (e: IllegalArgumentException) {
            logger.error(
                "JTE registry render failed for {} (class {}): {}",
                templateName,
                templateClass.name,
                e.message,
            )
            throw ViewNotFound(viewModel)
        } catch (e: IllegalStateException) {
            logger.error(
                "JTE registry render failed for {} (class {}): {}",
                templateName,
                templateClass.name,
                e.message,
            )
            throw ViewNotFound(viewModel)
        }
    }
