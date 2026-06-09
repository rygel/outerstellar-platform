package io.github.rygel.outerstellar.platform.extension

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

class ExtensionContractTest {
    @Test
    fun `contract helper collects diagnostics for a valid extension`() {
        val extension =
            object : PlatformExtension {
                override val id = "reports"
                override val appLabel = "Reports"
                override val mode = PlatformMode.ExtensionHost

                override fun contribute(context: ExtensionContributionContext) {
                    context.routes.publicUi(
                        ("/" bindContract GET).to { _: Request -> Response(Status.OK).body("reports") },
                        "Reports home",
                        "/",
                    )
                    context.navigation.item("Reports", "/", "bar-chart")
                }
            }

        val diagnostics = ExtensionContract.diagnostics(extension, extensionTestContext())

        assertEquals("reports", diagnostics.extensionId)
        assertEquals("Reports", diagnostics.appLabel)
        assertEquals("ExtensionHost", diagnostics.mode)
        assertEquals(listOf("/"), diagnostics.routes.map { it.pathPattern })
        assertEquals(1, diagnostics.capabilities.single { it.id == "routes" }.count)
        assertEquals(1, diagnostics.capabilities.single { it.id == "navigation" }.count)
    }

    @Test
    fun `contract helper collects extension readiness checks`() {
        val extension =
            object : PlatformExtension {
                override val id = "reports"
                override val appLabel = "Reports"

                override fun contribute(context: ExtensionContributionContext) {
                    context.readiness.down("content-dir", "Set CONTENT_DIR to an existing directory")
                    context.readiness.warn("preview-cache", "Preview cache is disabled")
                }
            }

        val diagnostics = ExtensionContract.diagnostics(extension, extensionTestContext())

        assertEquals(listOf("content-dir", "preview-cache"), diagnostics.readiness.map { it.name })
        assertEquals(ExtensionReadinessStatus.DOWN, diagnostics.readiness.first().status)
        assertEquals(true, diagnostics.readiness.first().required)
        assertEquals(false, diagnostics.readiness.last().required)
    }

    @Test
    fun `contract helper reports ownership mistakes before full platform boot`() {
        val extension =
            object : PlatformExtension {
                override val id = "reports"

                override fun contribute(context: ExtensionContributionContext) {
                    context.routes.publicUi(
                        ("/wrong" bindContract GET).to { _: Request -> Response(Status.OK) },
                        "Wrong page",
                        "/wrong",
                    )
                }
            }

        val error =
            assertFailsWith<IllegalArgumentException> { ExtensionContract.collect(extension, extensionTestContext()) }

        val message = error.message.orEmpty()
        assert(message.contains("Route * /wrong (Wrong page) is outside extension 'reports' ownership")) { message }
        assert(message.contains("Allowed prefixes: /reports, /extension/reports")) { message }
        assert(message.contains("Fix the pathPattern, update ExtensionManifest.ownership")) { message }
    }

    private fun extensionTestContext(): ExtensionHostContext =
        ExtensionHostContext.forTesting(
            rendering =
                object : HostRendering {
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
                object : HostUsers {
                    override fun currentUser(request: Request): User? = null

                    override fun findById(id: UUID): User? = null

                    override fun findByUsername(username: String): User? = null

                    override fun findByEmail(email: String): User? = null
                },
            security =
                HostSecurity(
                    apiKeys =
                        object : HostApiKeys {
                            override fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse =
                                error("Not used")

                            override fun listApiKeys(userId: UUID): List<ApiKeySummary> = emptyList()

                            override fun deleteApiKey(userId: UUID, keyId: Long) = Unit
                        },
                    oauth =
                        object : HostOAuth {
                            override fun findOrCreateOAuthUser(
                                providerName: String,
                                oauthSubject: String,
                                email: String?,
                            ): User = error("Not used")
                        },
                ),
        )
}
