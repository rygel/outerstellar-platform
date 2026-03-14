package dev.outerstellar.starter.di

import dev.outerstellar.starter.service.SyncProvider
import dev.outerstellar.starter.sync.SyncService
import org.koin.core.qualifier.named
import org.koin.dsl.module

val apiClientModule
    get() = module {
        single<SyncService> {
            SyncService(
                baseUrl = get(named("serverBaseUrl")),
                repository = get(),
                transactionManager = get(),
            )
        }
        single<SyncProvider> { get<SyncService>() }
    }
