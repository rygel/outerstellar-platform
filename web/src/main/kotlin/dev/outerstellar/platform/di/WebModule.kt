package dev.outerstellar.platform.di

import com.outerstellar.i18n.I18nService
import dev.outerstellar.platform.AppConfig
import dev.outerstellar.platform.analytics.AnalyticsService
import dev.outerstellar.platform.analytics.NoOpAnalyticsService
import dev.outerstellar.platform.analytics.SegmentAnalyticsService
import dev.outerstellar.platform.app
import dev.outerstellar.platform.infra.createRenderer
import dev.outerstellar.platform.persistence.JooqNotificationRepository
import dev.outerstellar.platform.persistence.MessageCache
import dev.outerstellar.platform.persistence.NotificationRepository
import dev.outerstellar.platform.persistence.OutboxRepository
import dev.outerstellar.platform.security.JwtService
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.security.UserRepository
import dev.outerstellar.platform.service.ContactService
import dev.outerstellar.platform.service.EmailService
import dev.outerstellar.platform.service.EventPublisher
import dev.outerstellar.platform.service.MessageService
import dev.outerstellar.platform.service.NoOpEmailService
import dev.outerstellar.platform.service.NotificationService
import dev.outerstellar.platform.service.SmtpConfig
import dev.outerstellar.platform.service.SmtpEmailService
import dev.outerstellar.platform.web.StarterPlugin
import dev.outerstellar.platform.web.SyncApi
import dev.outerstellar.platform.web.SyncWebSocket
import dev.outerstellar.platform.web.WebPageFactory
import org.http4k.core.PolyHandler
import org.http4k.template.TemplateRenderer
import org.koin.core.qualifier.named
import org.koin.dsl.module

val webModule
    get() = module {
        single { AppConfig.fromEnvironment() }
        single(named("jdbcUrl")) { get<AppConfig>().jdbcUrl }
        single(named("serverBaseUrl")) { "http://localhost:8080" }
        single(named("appBaseUrl")) { get<AppConfig>().appBaseUrl }
        single<TemplateRenderer> { createRenderer() }
        single<NotificationRepository> { JooqNotificationRepository(get()) }
        single { NotificationService(get()) }
        single { WebPageFactory(get(), get(), get(), get(), getOrNull(), get()) }
        single { SyncApi(get(), get(), get()) }
        single<MessageCache> { dev.outerstellar.platform.persistence.CaffeineMessageCache() }
        single<AnalyticsService> {
            val cfg = get<AppConfig>().segment
            if (cfg.enabled && cfg.writeKey.isNotBlank()) SegmentAnalyticsService(cfg.writeKey)
            else NoOpAnalyticsService()
        }
        single<EmailService> {
            val cfg = get<AppConfig>().email
            if (cfg.enabled && cfg.host.isNotBlank()) {
                SmtpEmailService(
                    SmtpConfig(
                        host = cfg.host,
                        port = cfg.port,
                        username = cfg.username,
                        password = cfg.password,
                        from = cfg.from,
                        startTls = cfg.startTls,
                    )
                )
            } else {
                NoOpEmailService()
            }
        }

        single<I18nService> { I18nService.create("messages") }
        single<EventPublisher> { SyncWebSocket }
        single<PolyHandler>(named("webServer")) {
            app(
                get<MessageService>(),
                get<dev.outerstellar.platform.service.ContactService>(),
                get<OutboxRepository>(),
                get<MessageCache>(),
                get<TemplateRenderer>(),
                get<WebPageFactory>(),
                get<AppConfig>(),
                get<SecurityService>(),
                get<UserRepository>(),
                analytics = get(),
                notificationService = get(),
                jwtService = getOrNull<JwtService>(),
                plugin = getOrNull<StarterPlugin>(),
            )
        }
    }
