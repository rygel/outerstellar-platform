package io.github.rygel.outerstellar.platform.web

import gg.jte.Content
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteLayoutRouterGenerated
import gg.jte.html.OwaspHtmlTemplateOutput
import gg.jte.output.StringOutput
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.Notification
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.plugin.HostedAppContext
import io.github.rygel.outerstellar.platform.plugin.PluginAnalytics
import io.github.rygel.outerstellar.platform.plugin.PluginApiKeys
import io.github.rygel.outerstellar.platform.plugin.PluginAppInfo
import io.github.rygel.outerstellar.platform.plugin.PluginNotification
import io.github.rygel.outerstellar.platform.plugin.PluginNotifications
import io.github.rygel.outerstellar.platform.plugin.PluginOAuth
import io.github.rygel.outerstellar.platform.plugin.PluginRendering
import io.github.rygel.outerstellar.platform.plugin.PluginSecurity
import io.github.rygel.outerstellar.platform.plugin.PluginUsers
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.service.NotificationService
import java.util.UUID
import org.http4k.core.Request
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

class HostedAppContextFactory(
    private val renderer: TemplateRenderer,
    private val apiKeyService: ApiKeyService,
    private val oauthService: OAuthService,
    private val userRepository: UserRepository,
    private val analytics: AnalyticsService = NoOpAnalyticsService(),
    private val notificationService: NotificationService? = null,
) {
    fun create(config: AppConfig): HostedAppContext =
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
): HostedAppContext =
    HostedAppContext(
        app =
            PluginAppInfo(
                version = config.version,
                appBaseUrl = config.appBaseUrl,
                devMode = config.devMode,
                registrationEnabled = config.registrationEnabled,
            ),
        users = DefaultPluginUsers(userRepository),
        analytics = DefaultPluginAnalytics(analytics),
        notifications = notificationService?.let(::DefaultPluginNotifications),
        rendering = DefaultPluginRendering(renderer),
        security =
            PluginSecurity(apiKeys = DefaultPluginApiKeys(apiKeyService), oauth = DefaultPluginOAuth(oauthService)),
    )

internal fun hostedAppContextForTesting(
    renderer: TemplateRenderer,
    apiKeyService: ApiKeyService,
    oauthService: OAuthService,
    userRepository: UserRepository,
    analytics: AnalyticsService = NoOpAnalyticsService(),
    notificationService: NotificationService? = null,
    config: AppConfig = AppConfig(),
): HostedAppContext =
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
        "Use HostedAppContext.forTesting(rendering = ..., users = ..., security = ...) when testing against the SPI alone."
)
fun HostedAppContext.Companion.forTesting(
    renderer: TemplateRenderer,
    apiKeyService: ApiKeyService,
    oauthService: OAuthService,
    userRepository: UserRepository,
    analytics: AnalyticsService = NoOpAnalyticsService(),
    notificationService: NotificationService? = null,
    config: AppConfig = AppConfig(),
): HostedAppContext =
    hostedAppContextForTesting(
        renderer = renderer,
        apiKeyService = apiKeyService,
        oauthService = oauthService,
        userRepository = userRepository,
        analytics = analytics,
        notificationService = notificationService,
        config = config,
    )

private class DefaultPluginUsers(private val userRepository: UserRepository) : PluginUsers {
    private val log = LoggerFactory.getLogger(DefaultPluginUsers::class.java)

    override fun currentUser(request: Request): User? =
        try {
            request.requestContext.user
        } catch (exception: IllegalStateException) {
            log.debug("Hosted app request missing RequestContext", exception)
            null
        }

    override fun findById(id: UUID): User? = userRepository.findById(id)

    override fun findByUsername(username: String): User? = userRepository.findByUsername(username)

    override fun findByEmail(email: String): User? = userRepository.findByEmail(email)
}

private class DefaultPluginAnalytics(private val analytics: AnalyticsService) : PluginAnalytics {
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

private class DefaultPluginNotifications(private val notificationService: NotificationService) : PluginNotifications {
    override fun create(userId: UUID, title: String, body: String, type: String) {
        notificationService.create(userId, title, body, type)
    }

    override fun listForUser(userId: UUID, limit: Int): List<PluginNotification> =
        notificationService.listForUser(userId, limit).map(Notification::toPluginNotification)

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

private fun Notification.toPluginNotification(): PluginNotification =
    PluginNotification(
        id = id.toString(),
        title = title,
        body = body,
        type = type,
        read = isRead,
        createdAt = createdAt.toString(),
    )

private class DefaultPluginRendering(override val renderer: TemplateRenderer) : PluginRendering {
    override fun renderShell(shell: ShellView, bodyHtml: String): String {
        val output = StringOutput()
        val content = Content { it.writeUnsafeContent(bodyHtml) }
        JteLayoutRouterGenerated.render(OwaspHtmlTemplateOutput(output), null, shell, content)
        return output.toString()
    }
}

private class DefaultPluginApiKeys(private val apiKeyService: ApiKeyService) : PluginApiKeys {
    override fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse =
        apiKeyService.createApiKey(userId, name)

    override fun listApiKeys(userId: UUID): List<ApiKeySummary> = apiKeyService.listApiKeys(userId)

    override fun deleteApiKey(userId: UUID, keyId: Long) {
        apiKeyService.deleteApiKey(userId, keyId)
    }
}

private class DefaultPluginOAuth(private val oauthService: OAuthService) : PluginOAuth {
    override fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User =
        oauthService.findOrCreateOAuthUser(providerName, oauthSubject, email)
}
