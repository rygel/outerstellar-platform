package dev.outerstellar.starter.di

import dev.outerstellar.starter.service.SyncProvider
import dev.outerstellar.starter.sync.SyncService
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.koin.core.qualifier.named
import org.koin.dsl.module

val apiClientModule = module {
    single<HttpHandler>(named("apiClient")) { ApacheClient() }
    single<SyncProvider> { 
        SyncService(get(), get(named("serverBaseUrl")), get(), get(), get(named("apiClient"))) 
    }
    single { get<SyncProvider>() as SyncService }
}
