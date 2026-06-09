package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import kotlin.test.Test
import org.http4k.contract.bindContract
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.hamkrest.hasStatus
import org.http4k.routing.ResourceLoader

class PlatformDiagnosticsIntegrationTest : WebTest() {
    private val app by lazy { buildApp(extension = diagnosticsExtension()) }

    @Test
    fun `debug routes is localhost only`() {
        val response = app(Request(GET, "/debug/routes").header("Host", "platform.example"))

        assertThat(response, hasStatus(Status.FORBIDDEN))
    }

    @Test
    fun `debug routes includes handler kind and wildcard route diagnostics`() {
        val response = app(Request(GET, "/debug/routes").header("Host", "localhost:8080"))

        assertThat(response, hasStatus(Status.OK))
        assertThat(response, bodyContains("\"handlerKind\":\"contract\""))
        assertThat(response, bodyContains("\"handlerKind\":\"routing\""))
        assertThat(response, bodyContains("/extensions/reports/assets/*"))
        assertThat(response, bodyContains("reports-cache"))
    }

    @Test
    fun `platform shell component urls are registered in route diagnostics`() {
        val body = app(Request(GET, "/debug/routes").header("Host", "localhost:8080")).bodyString()

        listOf("/components/footer-status", "/components/notification-bell").forEach { componentPath ->
            assert(body.contains(""""pathPattern":"$componentPath"""")) {
                "Shell component route $componentPath was not registered in diagnostics: $body"
            }
        }
    }

    private fun diagnosticsExtension(): PlatformExtension =
        object : PlatformExtension {
            override val id = "reports"
            override val appLabel = "Reports"

            override fun contribute(context: ExtensionContributionContext) {
                context.routes.publicUi(
                    ("/reports/diagnostics" bindContract GET).to { _: Request -> Response(Status.OK) },
                    "Reports diagnostics",
                    "/reports/diagnostics",
                )
                context.routes.staticAssets(
                    "/extensions/reports/assets",
                    ResourceLoader.Classpath("assets"),
                    "Reports assets",
                )
                context.readiness.up("reports-cache", "Reports cache is ready")
            }
        }
}
