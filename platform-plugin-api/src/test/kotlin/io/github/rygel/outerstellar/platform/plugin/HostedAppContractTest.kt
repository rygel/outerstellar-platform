package io.github.rygel.outerstellar.platform.plugin

import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.http4k.contract.bindContract
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

class HostedAppContractTest {
    @Test
    fun `contract helper collects diagnostics for a valid hosted app`() {
        val hostedApp =
            object : HostedApp {
                override val id = "reports"
                override val appLabel = "Reports"
                override val mode = PlatformMode.PluginHostedApp

                override fun contribute(context: HostedAppContributionContext) {
                    context.routes.publicUi(
                        ("/" bindContract GET).to { _: Request -> Response(Status.OK).body("reports") },
                        "Reports home",
                        "/",
                    )
                    context.navigation.item("Reports", "/", "bar-chart")
                }
            }

        val diagnostics = HostedAppContract.diagnostics(hostedApp, hostedAppTestContext())

        assertEquals("reports", diagnostics.hostedAppId)
        assertEquals("Reports", diagnostics.appLabel)
        assertEquals("PluginHostedApp", diagnostics.mode)
        assertEquals(listOf("/"), diagnostics.routes.map { it.pathPattern })
        assertEquals(1, diagnostics.capabilities.single { it.id == "routes" }.count)
        assertEquals(1, diagnostics.capabilities.single { it.id == "navigation" }.count)
    }

    @Test
    fun `contract helper reports ownership mistakes before full platform boot`() {
        val hostedApp =
            object : HostedApp {
                override val id = "reports"

                override fun contribute(context: HostedAppContributionContext) {
                    context.routes.publicUi(
                        ("/wrong" bindContract GET).to { _: Request -> Response(Status.OK) },
                        "Wrong page",
                        "/wrong",
                    )
                }
            }

        val error =
            assertFailsWith<IllegalArgumentException> { HostedAppContract.collect(hostedApp, hostedAppTestContext()) }

        val message = error.message.orEmpty()
        assert(message.contains("Route * /wrong (Wrong page) is outside hosted app 'reports' ownership")) { message }
        assert(message.contains("Allowed prefixes: /reports, /plugin/reports")) { message }
        assert(message.contains("Fix the pathPattern, update HostedAppManifest.ownership")) { message }
    }

    private fun hostedAppTestContext(): HostedAppContext =
        HostedAppContext.forTesting(
            rendering =
                object : PluginRendering {
                    override val renderer: TemplateRenderer =
                        object : TemplateRenderer {
                            override fun invoke(viewModel: ViewModel): String = ""
                        }

                    override fun renderShell(
                        shell: io.github.rygel.outerstellar.platform.web.ShellView,
                        bodyHtml: String,
                    ) = bodyHtml
                },
            users =
                object : PluginUsers {
                    override fun currentUser(request: Request): User? = null

                    override fun findById(id: UUID): User? = null

                    override fun findByUsername(username: String): User? = null

                    override fun findByEmail(email: String): User? = null
                },
            security =
                PluginSecurity(
                    apiKeys =
                        object : PluginApiKeys {
                            override fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse =
                                error("Not used")

                            override fun listApiKeys(userId: UUID): List<ApiKeySummary> = emptyList()

                            override fun deleteApiKey(userId: UUID, keyId: Long) = Unit
                        },
                    oauth =
                        object : PluginOAuth {
                            override fun findOrCreateOAuthUser(
                                providerName: String,
                                oauthSubject: String,
                                email: String?,
                            ): User = error("Not used")
                        },
                ),
        )
}
