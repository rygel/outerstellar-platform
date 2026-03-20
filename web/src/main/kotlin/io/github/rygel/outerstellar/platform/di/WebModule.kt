package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.analytics.SegmentAnalyticsService
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqNotificationRepository
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.EmailService
import io.github.rygel.outerstellar.platform.service.EventPublisher
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NoOpEmailService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.service.SmtpConfig
import io.github.rygel.outerstellar.platform.service.SmtpEmailService
import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import io.github.rygel.outerstellar.platform.web.SyncApi
import io.github.rygel.outerstellar.platform.web.SyncWebSocket
import io.github.rygel.outerstellar.platform.web.WebPageFactory
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
        single<MessageCache> {
            io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache()
        }
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
                get<io.github.rygel.outerstellar.platform.service.ContactService>(),
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
                plugin = getOrNull<PlatformPlugin>(),
                activityUpdater = getOrNull<AsyncActivityUpdater>(),
            )
        }
    }
