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

fun TemplateRenderer.render(viewModel: ViewModel, status: Status = Status.OK): Response =
    Response(status)
        .header("content-type", ContentType.TEXT_HTML.toHeaderValue() + "; charset=utf-8")
        .body(this(viewModel))

fun createRenderer(): TemplateRenderer {
    val isProduction = System.getProperty("jte.production") == "true" || System.getenv("JTE_PRODUCTION") == "true"

    if (isProduction) {
        System.err.println(
            "JTE: production mode, template classes referenced: ${JteAuthPageGenerated::class.java.name}, ${JteErrorPageGenerated::class.java.name}"
        )
        ensureTemplateClassesLoaded()
    }

    val templateEngine =
        if (isProduction) {
            val precompiledDir = findPrecompiledDir()
            TemplateEngine.createPrecompiled(precompiledDir, gg.jte.ContentType.Html, null, PLATFORM_JTE_PACKAGE)
        } else {
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
        }

    return renderUsing { templateEngine }
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

    val classNames = mutableListOf<String>()
    val base = "gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web"
    val templateNames =
        listOf(
            "ApiKeysPage",
            "AuditLogPage",
            "AuthFormFragment",
            "AuthPage",
            "AuthResultFragment",
            "ConflictResolveModal",
            "ContactForm",
            "ContactsPage",
            "DashboardPage",
            "ErrorPage",
            "Layout",
            "LoginPage",
            "MessageList",
            "MessagesPage",
            "Modal",
            "ModalOverlay",
            "NavTag",
            "NotificationBell",
            "NotificationPage",
            "PageHeader",
            "Pagination",
            "PasswordResetPage",
            "ProfilePage",
            "SettingsPage",
            "SidebarSelector",
            "SyncPage",
            "UserAdminPage",
        )
    for (name in templateNames) {
        classNames += "$base.Jte${name}Generated"
        classNames += "$base.components.Jte${name}Generated"
        classNames += "$base.layouts.Jte${name}Generated"
    }
    var loaded = 0
    var failed = 0
    for (className in classNames) {
        try {
            Class.forName(className)
            loaded++
        } catch (_: ClassNotFoundException) {
            failed++
        }
    }
    System.err.println("JTE: preloaded $loaded template classes, $failed not found")
}

private fun findPrecompiledDir(): Path? {
    val resource = Thread.currentThread().contextClassLoader.getResource("gg/jte/generated/precompiled/outerstellar")
    if (resource != null && resource.protocol == "file") {
        val dir = Path.of(resource.toURI())
        if (Files.isDirectory(dir)) return dir.parent.parent
    }
    val cwd = Path.of(System.getProperty("user.dir"))
    val candidate = cwd.resolve("platform-web").resolve("target").resolve("classes")
    if (Files.isDirectory(candidate.resolve("gg").resolve("jte").resolve("generated"))) return candidate
    val candidate2 = cwd.resolve("target").resolve("classes")
    if (Files.isDirectory(candidate2.resolve("gg").resolve("jte").resolve("generated"))) return candidate2
    return null
}

private fun renderUsing(engineProvider: () -> TemplateEngine): TemplateRenderer = { viewModel: ViewModel ->
    val templateName = "${viewModel.template()}.kte"
    val templateEngine = engineProvider()

    try {
        StringOutput().also { templateEngine.render(templateName, viewModel, it) }.toString()
    } catch (e: Exception) {
        val className = "$PLATFORM_JTE_PACKAGE.${viewModel.template().replace('/', '.')}"
        val fullClassName = className.replace(".kte", "") + "Generated"
        System.err.println("=== JTE DIAGNOSTIC ===")
        System.err.println("  templateName: $templateName")
        System.err.println("  expected class: $fullClassName")
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
