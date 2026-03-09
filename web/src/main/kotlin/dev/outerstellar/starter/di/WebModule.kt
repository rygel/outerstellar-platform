package dev.outerstellar.starter.di

import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.web.SyncApi
import dev.outerstellar.starter.web.WebPageFactory
import org.http4k.core.HttpHandler
import org.http4k.template.TemplateRenderer
import org.koin.dsl.module

val webModule = module {
    single { AppConfig.fromEnvironment() }
    single { get<AppConfig>().jdbcUrl }
    single<TemplateRenderer> { createRenderer() }
    single { WebPageFactory(get()) }
    single { SyncApi(get()) }
    single<HttpHandler> { app(get(), get(), get()) }
}
