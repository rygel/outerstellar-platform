#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.extension

import io.github.rygel.outerstellar.platform.extension.ExtensionHostContext
import io.github.rygel.outerstellar.platform.extension.HostApiKeys
import io.github.rygel.outerstellar.platform.extension.HostOAuth
import io.github.rygel.outerstellar.platform.extension.HostAppInfo
import io.github.rygel.outerstellar.platform.extension.HostRendering
import io.github.rygel.outerstellar.platform.extension.HostSecurity
import io.github.rygel.outerstellar.platform.extension.HostUsers
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.web.ShellView
import java.util.UUID
import org.http4k.core.Request
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

fun testHostContext(): ExtensionHostContext =
    ExtensionHostContext.forTesting(
        app = HostAppInfo(version = "dev", appBaseUrl = "http://localhost:8080", devMode = true, registrationEnabled = true),
        rendering = object : HostRendering {
            override val renderer: TemplateRenderer = object : TemplateRenderer {
                override fun invoke(viewModel: ViewModel): String = ""
            }
            override fun renderShell(shell: ShellView, bodyHtml: String): String = bodyHtml
        },
        users = object : HostUsers {
            override fun currentUser(request: Request): User? = null
            override fun findById(id: UUID): User? = null
            override fun findByUsername(username: String): User? = null
            override fun findByEmail(email: String): User? = null
        },
        security = HostSecurity(
            apiKeys = object : HostApiKeys {
                override fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse = error("Not used")
                override fun listApiKeys(userId: UUID): List<ApiKeySummary> = emptyList()
                override fun deleteApiKey(userId: UUID, keyId: Long) = Unit
            },
            oauth = object : HostOAuth {
                override fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User = error("Not used")
            },
        ),
    )
