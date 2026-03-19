package dev.outerstellar.starter.di

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.analytics.AnalyticsService
import dev.outerstellar.starter.analytics.NoOpAnalyticsService
import dev.outerstellar.starter.analytics.SegmentAnalyticsService
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqNotificationRepository
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.NotificationRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.security.JwtService
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.EmailService
import dev.outerstellar.starter.service.EventPublisher
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.service.NoOpEmailService
import dev.outerstellar.starter.service.NotificationService
import dev.outerstellar.starter.service.SmtpConfig
import dev.outerstellar.starter.service.SmtpEmailService
import dev.outerstellar.starter.web.SyncApi
import dev.outerstellar.starter.web.SyncWebSocket
import dev.outerstellar.starter.web.WebPageFactory
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
        single<MessageCache> { dev.outerstellar.starter.persistence.CaffeineMessageCache() }
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
                get<dev.outerstellar.starter.service.ContactService>(),
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
            )
        }
    }
