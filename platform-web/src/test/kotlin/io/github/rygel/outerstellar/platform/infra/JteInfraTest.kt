package io.github.rygel.outerstellar.platform.infra

import gg.jte.CodeResolver
import gg.jte.TemplateEngine
import gg.jte.resolve.ResourceCodeResolver
import io.github.rygel.outerstellar.platform.web.ErrorPage
import io.github.rygel.outerstellar.platform.web.Page
import io.github.rygel.outerstellar.platform.web.RequestContext
import io.github.rygel.outerstellar.platform.web.ShellRenderer
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.junit.jupiter.api.io.TempDir

class JteInfraTest {

    @TempDir lateinit var generatedTemplates: Path

    @Test
    fun `dev renderer falls back to precompiled platform template when source is absent`() {
        val renderer =
            renderUsingDevEngineWithPrecompiledFallback(
                TemplateEngine.create(
                    ResourceCodeResolver("."),
                    generatedTemplates,
                    gg.jte.ContentType.Html,
                    Thread.currentThread().contextClassLoader,
                ),
                MissingTemplateSourceResolver,
            )
        val shell = ShellRenderer(RequestContext(Request(GET, "/broken"))).shell("Error", "/errors")
        val page =
            Page(
                shell = shell,
                data =
                    ErrorPage(
                        statusCode = 500,
                        heading = "Server down",
                        message = "The original error should stay visible in logs.",
                        primaryActionLabel = "Home",
                        primaryActionUrl = "/",
                        secondaryActionLabel = "Sign in",
                        secondaryActionUrl = "/auth",
                        helpButtonLabel = "Help",
                        helpUrl = "/errors/components/help/server-error",
                        errorLabel = "Error",
                    ),
            )

        val html = renderer(page)

        assertTrue(html.contains("Server down"), "Expected precompiled ErrorPage.kte to render, got: $html")
    }

    private object MissingTemplateSourceResolver : CodeResolver {
        override fun resolve(name: String): String = error("No source template should be resolved for $name")

        override fun exists(name: String): Boolean = false

        override fun getLastModified(name: String): Long = 0

        override fun resolveAllTemplateNames(): MutableList<String> = mutableListOf()
    }
}
