package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.http4k.contract.bindContract
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.RequestSource
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.hamkrest.hasStatus
import org.http4k.routing.ResourceLoader

class PlatformDiagnosticsIntegrationTest : WebTest() {
    private val app by lazy { buildApp(extension = diagnosticsExtension()) }

    @Test
    fun `remote request cannot access diagnostics by claiming a localhost Host header`() {
        val response =
            app(Request(GET, "/debug/routes").source(RequestSource("203.0.113.10")).header("Host", "localhost:8080"))

        assertThat(response, hasStatus(Status.FORBIDDEN))
    }

    @Test
    fun `forwarded request from loopback requires management token`() {
        val response =
            app(
                Request(GET, "/debug/routes")
                    .source(RequestSource("127.0.0.1"))
                    .header("X-Forwarded-For", "203.0.113.10")
            )

        assertThat(response, hasStatus(Status.FORBIDDEN))
    }

    @Test
    fun `request without transport source fails closed`() {
        val rawApp = buildApp(extension = diagnosticsExtension(), defaultRequestSource = null)
        val response = rawApp(Request(GET, "/debug/routes"))

        assertThat(response, hasStatus(Status.FORBIDDEN))
    }

    @Test
    fun `remote orchestrator can access diagnostics with management token`() {
        val tokenApp = buildApp(config = testConfig.copy(managementToken = MANAGEMENT_TOKEN))
        val response =
            tokenApp(
                Request(GET, "/debug/routes")
                    .source(RequestSource("203.0.113.10"))
                    .header("Authorization", "Bearer $MANAGEMENT_TOKEN")
            )

        assertThat(response, hasStatus(Status.OK))
        assertThat(response, bodyContains("\"routes\""))
    }

    @Test
    fun `short management token fails application startup`() {
        assertFailsWith<IllegalArgumentException> {
            buildApp(config = testConfig.copy(managementToken = "short-token"))
        }
    }

    @Test
    fun `debug routes includes handler kind and wildcard route diagnostics`() {
        val response = app(loopbackDiagnosticsRequest())

        assertThat(response, hasStatus(Status.OK))
        assertThat(response, bodyContains("\"handlerKind\":\"contract\""))
        assertThat(response, bodyContains("\"handlerKind\":\"routing\""))
        assertThat(response, bodyContains("/extensions/reports/assets/*"))
        assertThat(response, bodyContains("reports-cache"))
    }

    @Test
    fun `platform shell component urls are registered in route diagnostics`() {
        val body = app(loopbackDiagnosticsRequest()).bodyString()

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

    private fun loopbackDiagnosticsRequest(): Request = Request(GET, "/debug/routes").source(RequestSource("127.0.0.1"))

    companion object {
        private const val MANAGEMENT_TOKEN = "management-route-test-token-32-bytes"
    }
}
