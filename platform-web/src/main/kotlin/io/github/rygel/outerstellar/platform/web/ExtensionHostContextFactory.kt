package io.github.rygel.outerstellar.platform.web

import gg.jte.Content
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteLayoutRouterGenerated
import gg.jte.html.OwaspHtmlTemplateOutput
import gg.jte.output.StringOutput
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.extension.ExtensionHostContext
import io.github.rygel.outerstellar.platform.extension.HostAnalytics
import io.github.rygel.outerstellar.platform.extension.HostApiKeys
import io.github.rygel.outerstellar.platform.extension.HostAppInfo
import io.github.rygel.outerstellar.platform.extension.HostNotification
import io.github.rygel.outerstellar.platform.extension.HostNotifications
import io.github.rygel.outerstellar.platform.extension.HostOAuth
import io.github.rygel.outerstellar.platform.extension.HostRendering
import io.github.rygel.outerstellar.platform.extension.HostSecurity
import io.github.rygel.outerstellar.platform.extension.HostUsers
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.Notification
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.service.NotificationService
import java.util.UUID
import org.http4k.core.Request
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

class ExtensionHostContextFactory(
    private val renderer: TemplateRenderer,
    private val apiKeyService: ApiKeyService,
    private val oauthService: OAuthService,
    private val userRepository: UserRepository,
    private val analytics: AnalyticsService = NoOpAnalyticsService(),
    private val notificationService: NotificationService? = null,
) {
    fun create(config: AppConfig): ExtensionHostContext =
        hostedAppContextFromHostServices(
            renderer = renderer,
            config = config,
            apiKeyService = apiKeyService,
            oauthService = oauthService,
            userRepository = userRepository,
            analytics = analytics,
            notificationService = notificationService,
        )
}

internal fun hostedAppContextFromHostServices(
    renderer: TemplateRenderer,
    config: AppConfig,
    apiKeyService: ApiKeyService,
    oauthService: OAuthService,
    userRepository: UserRepository,
    analytics: AnalyticsService = NoOpAnalyticsService(),
    notificationService: NotificationService? = null,
): ExtensionHostContext =
    ExtensionHostContext(
        app =
            HostAppInfo(
                version = config.version,
                appBaseUrl = config.appBaseUrl,
                devMode = config.devMode,
                registrationEnabled = config.registrationEnabled,
            ),
        users = DefaultHostUsers(userRepository),
        analytics = DefaultHostAnalytics(analytics),
        notifications = notificationService?.let(::DefaultHostNotifications),
        rendering = DefaultHostRendering(renderer),
        security = HostSecurity(apiKeys = DefaultHostApiKeys(apiKeyService), oauth = DefaultHostOAuth(oauthService)),
    )

internal fun hostedAppContextForTesting(
    renderer: TemplateRenderer,
    apiKeyService: ApiKeyService,
    oauthService: OAuthService,
    userRepository: UserRepository,
    analytics: AnalyticsService = NoOpAnalyticsService(),
    notificationService: NotificationService? = null,
    config: AppConfig = AppConfig(),
): ExtensionHostContext =
    hostedAppContextFromHostServices(
        renderer = renderer,
        config = config,
        apiKeyService = apiKeyService,
        oauthService = oauthService,
        userRepository = userRepository,
        analytics = analytics,
        notificationService = notificationService,
    )

@Deprecated(
    message =
        "Use ExtensionHostContext.forTesting(rendering = ..., users = ..., security = ...) when testing against the SPI alone."
)
fun ExtensionHostContext.Companion.forTesting(
    renderer: TemplateRenderer,
    apiKeyService: ApiKeyService,
    oauthService: OAuthService,
    userRepository: UserRepository,
    analytics: AnalyticsService = NoOpAnalyticsService(),
    notificationService: NotificationService? = null,
    config: AppConfig = AppConfig(),
): ExtensionHostContext =
    hostedAppContextForTesting(
        renderer = renderer,
        apiKeyService = apiKeyService,
        oauthService = oauthService,
        userRepository = userRepository,
        analytics = analytics,
        notificationService = notificationService,
        config = config,
    )

private class DefaultHostUsers(private val userRepository: UserRepository) : HostUsers {
    private val log = LoggerFactory.getLogger(DefaultHostUsers::class.java)

    override fun currentUser(request: Request): User? =
        try {
            request.requestContext.user
        } catch (exception: IllegalStateException) {
            log.debug("Extension request missing RequestContext", exception)
            null
        }

    override fun findById(id: UUID): User? = userRepository.findById(id)

    override fun findByUsername(username: String): User? = userRepository.findByUsername(username)

    override fun findByEmail(email: String): User? = userRepository.findByEmail(email)
}

private class DefaultHostAnalytics(private val analytics: AnalyticsService) : HostAnalytics {
    override fun identify(userId: String, traits: Map<String, Any>) {
        analytics.identify(userId, traits)
    }

    override fun track(userId: String, event: String, properties: Map<String, Any>) {
        analytics.track(userId, event, properties)
    }

    override fun page(userId: String, path: String) {
        analytics.page(userId, path)
    }
}

private class DefaultHostNotifications(private val notificationService: NotificationService) : HostNotifications {
    override fun create(userId: UUID, title: String, body: String, type: String) {
        notificationService.create(userId, title, body, type)
    }

    override fun listForUser(userId: UUID, limit: Int): List<HostNotification> =
        notificationService.listForUser(userId, limit).map(Notification::toHostNotification)

    override fun countUnread(userId: UUID): Int = notificationService.countUnread(userId)

    override fun markRead(id: UUID, userId: UUID) {
        notificationService.markRead(id, userId)
    }

    override fun markAllRead(userId: UUID) {
        notificationService.markAllRead(userId)
    }

    override fun delete(id: UUID, userId: UUID) {
        notificationService.delete(id, userId)
    }
}

private fun Notification.toHostNotification(): HostNotification =
    HostNotification(
        id = id.toString(),
        title = title,
        body = body,
        type = type,
        read = isRead,
        createdAt = createdAt.toString(),
    )

private class DefaultHostRendering(override val renderer: TemplateRenderer) : HostRendering {
    override fun renderShell(shell: ShellView, bodyHtml: String): String {
        val output = StringOutput()
        val content = Content { it.writeUnsafeContent(bodyHtml) }
        JteLayoutRouterGenerated.render(OwaspHtmlTemplateOutput(output), null, shell, content)
        return output.toString()
    }
}

private class DefaultHostApiKeys(private val apiKeyService: ApiKeyService) : HostApiKeys {
    override fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse =
        apiKeyService.createApiKey(userId, name)

    override fun listApiKeys(userId: UUID): List<ApiKeySummary> = apiKeyService.listApiKeys(userId)

    override fun deleteApiKey(userId: UUID, keyId: Long) {
        apiKeyService.deleteApiKey(userId, keyId)
    }
}

private class DefaultHostOAuth(private val oauthService: OAuthService) : HostOAuth {
    override fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User =
        oauthService.findOrCreateOAuthUser(providerName, oauthSubject, email)
}
