package dev.outerstellar.starter.di

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.service.EventPublisher
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.MessageService
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
        single<TemplateRenderer> { createRenderer() }
        single { WebPageFactory(get(), get()) }
        single { SyncApi(get(), get()) }
        single<MessageCache> { dev.outerstellar.starter.persistence.NoOpMessageCache }

        single<I18nService> { I18nService.fromResourceBundle("messages") }
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
            )
        }
    }
