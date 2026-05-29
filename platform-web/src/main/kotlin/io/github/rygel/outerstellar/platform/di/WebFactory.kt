package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.PluginMigrationSource
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.analytics.SegmentAnalyticsService
import io.github.rygel.outerstellar.platform.infra.PluginTemplateRenderer
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import io.github.rygel.outerstellar.platform.persistence.PollRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.persistence.VoteRepository
import io.github.rygel.outerstellar.platform.plugin.HostedApp
import io.github.rygel.outerstellar.platform.security.AdminStatsService
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.SessionService
import io.github.rygel.outerstellar.platform.security.UserAdminService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.EmailService
import io.github.rygel.outerstellar.platform.service.EventPublisher
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NoOpEmailService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.service.PollService
import io.github.rygel.outerstellar.platform.service.ResilientEmailService
import io.github.rygel.outerstellar.platform.service.SmtpConfig
import io.github.rygel.outerstellar.platform.service.SmtpEmailService
import io.github.rygel.outerstellar.platform.service.VoteService
import io.github.rygel.outerstellar.platform.web.SyncWebSocket
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import org.http4k.template.TemplateRenderer

private object NoOpPluginMigrationSource : PluginMigrationSource

@Suppress("LongParameterList")
class WebComponents(
    val templateRenderer: TemplateRenderer,
    val pageFactory: WebPageFactory,
    val analyticsService: AnalyticsService,
    val emailService: EmailService,
    val i18nService: I18nService,
    val syncWebSocket: SyncWebSocket,
    val eventPublisher: EventPublisher,
    val voteService: VoteService,
    val pollService: PollService,
    val notificationService: NotificationService,
    val adminStatsService: AdminStatsService,
    val pluginMigrationSource: PluginMigrationSource,
)

@Suppress("LongParameterList")
fun createWebComponents(
    config: AppConfig,
    plugin: HostedApp? = null,
    apiKeyService: ApiKeyService,
    sessionService: SessionService,
    userAdminService: UserAdminService,
    messageRepository: MessageRepository,
    messageService: MessageService? = null,
    contactService: ContactService? = null,
    userRepository: UserRepository,
    voteRepository: VoteRepository,
    pollRepository: PollRepository,
    notificationRepository: NotificationRepository,
): WebComponents {
    val baseRenderer = createRenderer(config.runtime)
    val overrides = plugin?.templateOverrides()
    val templateRenderer: TemplateRenderer =
        if (plugin != null && overrides != null && overrides.isNotEmpty()) {
            PluginTemplateRenderer(baseRenderer, overrides, plugin::class.java.classLoader)
        } else {
            baseRenderer
        }

    val pageFactory =
        WebPageFactory(
            messageRepository,
            messageService,
            contactService,
            apiKeyService,
            appleOAuthEnabled = config.appleOAuth.enabled,
            userAdminService = userAdminService,
        )

    val runtime = config.runtime
    val analyticsService: AnalyticsService =
        if (config.segment.enabled && config.segment.writeKey.isNotBlank()) {
            SegmentAnalyticsService(config.segment.writeKey)
        } else {
            NoOpAnalyticsService()
        }

    val emailService: EmailService =
        if (config.email.enabled && config.email.host.isNotBlank()) {
            ResilientEmailService(
                SmtpEmailService(
                    SmtpConfig(
                        host = config.email.host,
                        port = config.email.port,
                        username = config.email.username,
                        password = config.email.password,
                        from = config.email.from,
                        startTls = config.email.startTls,
                    )
                )
            )
        } else {
            NoOpEmailService()
        }

    val i18nService = I18nService.create("messages")
    val syncWebSocket = SyncWebSocket(sessionService)
    val eventPublisher: EventPublisher = syncWebSocket
    val voteService = VoteService(voteRepository, messageRepository)
    val pollService = PollService(pollRepository)
    val notificationService = NotificationService(notificationRepository)
    val adminStatsService = AdminStatsService(userRepository)
    val pluginMigrationSource: PluginMigrationSource = plugin ?: NoOpPluginMigrationSource

    return WebComponents(
        templateRenderer = templateRenderer,
        pageFactory = pageFactory,
        analyticsService = analyticsService,
        emailService = emailService,
        i18nService = i18nService,
        syncWebSocket = syncWebSocket,
        eventPublisher = eventPublisher,
        voteService = voteService,
        pollService = pollService,
        notificationService = notificationService,
        adminStatsService = adminStatsService,
        pluginMigrationSource = pluginMigrationSource,
    )
}
