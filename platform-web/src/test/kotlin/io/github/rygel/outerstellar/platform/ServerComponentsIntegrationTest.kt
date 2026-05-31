package io.github.rygel.outerstellar.platform

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.plugin.HostedApp
import io.github.rygel.outerstellar.platform.plugin.HostedAppContributionContext
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
    fun `server components can boot hosted app with explicit test config`() {
        val config = testConfig.copy(version = "test-config-version", platformMode = PlatformMode.PluginHostedApp)
        val components = createServerComponents(config = config, plugin = configProbeApp())

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
    fun `hosted app in PluginHostedApp mode owns root route`() {
        val config = testConfig.copy(platformMode = PlatformMode.PluginHostedApp)
        val app = rootOwningApp()
        val components = createServerComponents(config = config, plugin = app)

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
    fun `hosted app with FullPlatformApp mode cannot register root route`() {
        val thrown =
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                val config = testConfig.copy(platformMode = PlatformMode.FullPlatformApp)
                createServerComponents(config = config, plugin = rootClaimingFullModeApp())
            }
        assert(thrown.message!!.contains("outside hosted app"))
    }

    private fun rootClaimingFullModeApp(): HostedApp =
        object : HostedApp {
            override val id = "root-claim"
            override val appLabel = "Root Claim"
            override val mode = PlatformMode.FullPlatformApp

            override fun contribute(context: HostedAppContributionContext) {
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

    private fun rootOwningApp(): HostedApp =
        object : HostedApp {
            override val id = "root-owner"
            override val appLabel = "Root Owner"
            override val mode = PlatformMode.PluginHostedApp

            override fun contribute(context: HostedAppContributionContext) {
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

    private fun configProbeApp(): HostedApp =
        object : HostedApp {
            override val id = "config-probe"
            override val appLabel = "Config Probe"
            override val mode = PlatformMode.PluginHostedApp

            override fun contribute(context: HostedAppContributionContext) {
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
