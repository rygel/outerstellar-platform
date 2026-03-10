package dev.outerstellar.starter.di

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.SyncApi
import dev.outerstellar.starter.web.WebPageFactory
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.template.TemplateRenderer
import org.koin.core.qualifier.named
import org.koin.dsl.module

val webModule = module {
    single { AppConfig.fromEnvironment() }
    single(named("jdbcUrl")) { get<AppConfig>().jdbcUrl }
    single(named("serverBaseUrl")) { "http://localhost:8080" } // Or from AppConfig if available
    single<TemplateRenderer> { createRenderer() }
    single { WebPageFactory(get(), get<AppConfig>().devDashboardEnabled) }
    single { SyncApi(get()) }
    single<I18nService> { I18nService.fromResourceBundle("web-messages") }
    single<HttpHandler>(named("webServer")) { 
        app(
            get<MessageService>(), 
            get<MessageRepository>(), 
            get<OutboxRepository>(), 
            get<MessageCache>(), 
            get<TemplateRenderer>(), 
            get<WebPageFactory>(),
            get<AppConfig>(),
            get<I18nService>()
        ) 
    }
}
