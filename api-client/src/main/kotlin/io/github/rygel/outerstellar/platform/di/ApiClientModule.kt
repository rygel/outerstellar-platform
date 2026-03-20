package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.service.SyncProvider
import io.github.rygel.outerstellar.platform.sync.SyncService
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
