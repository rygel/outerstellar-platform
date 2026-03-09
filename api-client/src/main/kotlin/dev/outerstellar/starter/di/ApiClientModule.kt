package dev.outerstellar.starter.di

import dev.outerstellar.starter.service.SyncProvider
import dev.outerstellar.starter.sync.SyncService
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.koin.dsl.module

val apiClientModule = module {
    single<HttpHandler> { ApacheClient() }
    single<SyncProvider> { 
        // serverBaseUrl is expected to be provided via a property or another single
        SyncService(get(), get(), get(), get(), get()) 
    }
    single { get<SyncProvider>() as SyncService }
}
