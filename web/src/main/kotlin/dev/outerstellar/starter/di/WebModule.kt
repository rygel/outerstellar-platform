package dev.outerstellar.starter.di

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.service.EventPublisher
import dev.outerstellar.starter.web.SyncApi
import dev.outerstellar.starter.web.WebPageFactory
import dev.outerstellar.starter.web.SyncWebSocket
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.User
import org.http4k.core.HttpHandler
import org.http4k.server.PolyHandler
import org.http4k.template.TemplateRenderer
import org.koin.core.qualifier.named
import org.koin.dsl.module

val webModule = module {
    single { AppConfig.fromEnvironment() }
    single(named("jdbcUrl")) { get<AppConfig>().jdbcUrl }
    single(named("serverBaseUrl")) { "http://localhost:8080" }
    single<TemplateRenderer> { createRenderer() }
    single { WebPageFactory(get(), get<AppConfig>().devDashboardEnabled) }
    single { SyncApi(get()) }
    single<MessageCache> { dev.outerstellar.starter.persistence.NoOpMessageCache }
    
    single<I18nService> { I18nService.fromResourceBundle("messages") }
    single<EventPublisher> { SyncWebSocket }
    single<PolyHandler>(named("webServer")) { 
        app(
            get<MessageService>(), 
            get<MessageRepository>(), 
            get<OutboxRepository>(), 
            get<MessageCache>(), 
            get<TemplateRenderer>(), 
            get<WebPageFactory>(),
            get<AppConfig>(),
            get<I18nService>(),
            get<SecurityService>(),
            get<UserRepository>(),
            get<dev.outerstellar.starter.security.PasswordEncoder>()
        ) 
    }
}
