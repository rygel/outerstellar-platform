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
