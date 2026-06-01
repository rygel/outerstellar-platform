package io.github.rygel.outerstellar.platform

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import io.github.rygel.outerstellar.platform.web.WebTest
import kotlin.test.Test
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.hamkrest.hasStatus

class ServerComponentsIntegrationTest : WebTest() {
    @Test
    fun `server components can boot extension with explicit test config`() {
        val config = testConfig.copy(version = "test-config-version", platformMode = PlatformMode.ExtensionHost)
        val components = createServerComponents(config = config, extension = configProbeApp())

        try {
            val response = components.app.http!!(Request(GET, "/probe/config"))

            assertThat(response, hasStatus(Status.OK))
            assertThat(response.bodyString(), equalTo("test-config-version"))
            assertThat(components.config, equalTo(config))
        } finally {
            components.persistence.close()
        }
    }

    @Test
    fun `extension in ExtensionHost mode owns root route`() {
        val config = testConfig.copy(platformMode = PlatformMode.ExtensionHost)
        val app = rootOwningApp()
        val components = createServerComponents(config = config, extension = app)

        try {
            val rootResponse = components.app.http!!(Request(GET, "/"))
            assertThat(rootResponse, hasStatus(Status.OK))
            assertThat(rootResponse.bodyString(), equalTo("root-owned"))

            val dashboardResponse = components.app.http!!(Request(GET, "/dashboard"))
            assertThat(dashboardResponse, hasStatus(Status.OK))
            assertThat(dashboardResponse.bodyString(), equalTo("dashboard"))

            val aboutResponse = components.app.http!!(Request(GET, "/about"))
            assertThat(aboutResponse, hasStatus(Status.OK))
            assertThat(aboutResponse.bodyString(), equalTo("about"))
        } finally {
            components.persistence.close()
        }
    }

    @Test
    fun `extension with FullPlatform mode cannot register root route`() {
        val thrown =
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                val config = testConfig.copy(platformMode = PlatformMode.FullPlatform)
                createServerComponents(config = config, extension = rootClaimingFullModeApp())
            }
        assert(thrown.message!!.contains("outside extension"))
    }

    private fun rootClaimingFullModeApp(): PlatformExtension =
        object : PlatformExtension {
            override val id = "root-claim"
            override val appLabel = "Root Claim"
            override val mode = PlatformMode.FullPlatform

            override fun contribute(context: ExtensionContributionContext) {
                val rootRoute =
                    "/" meta
                        {
                            summary = "Root"
                        } bindContract
                        GET to
                        { _: Request ->
                            Response(Status.OK).body("should-not-work")
                        }
                context.routes.publicUi(rootRoute, "Root", "/")
            }
        }

    private fun rootOwningApp(): PlatformExtension =
        object : PlatformExtension {
            override val id = "root-owner"
            override val appLabel = "Root Owner"
            override val mode = PlatformMode.ExtensionHost

            override fun contribute(context: ExtensionContributionContext) {
                val rootRoute =
                    "/" meta
                        {
                            summary = "Root"
                        } bindContract
                        GET to
                        { _: Request ->
                            Response(Status.OK).body("root-owned")
                        }
                context.routes.publicUi(rootRoute, "Root", "/")

                val dashboardRoute =
                    "/dashboard" meta
                        {
                            summary = "Dashboard"
                        } bindContract
                        GET to
                        { _: Request ->
                            Response(Status.OK).body("dashboard")
                        }
                context.routes.publicUi(dashboardRoute, "Dashboard", "/dashboard")

                val aboutRoute =
                    "/about" meta
                        {
                            summary = "About"
                        } bindContract
                        GET to
                        { _: Request ->
                            Response(Status.OK).body("about")
                        }
                context.routes.publicUi(aboutRoute, "About", "/about")
            }
        }

    private fun configProbeApp(): PlatformExtension =
        object : PlatformExtension {
            override val id = "config-probe"
            override val appLabel = "Config Probe"
            override val mode = PlatformMode.ExtensionHost

            override fun contribute(context: ExtensionContributionContext) {
                val route =
                    "/probe/config" meta
                        {
                            summary = "Config probe"
                        } bindContract
                        GET to
                        { _: Request ->
                            Response(Status.OK).body(context.host.app.version)
                        }

                context.routes.publicUi(route, "Config probe", "/probe/config")
            }
        }
}
