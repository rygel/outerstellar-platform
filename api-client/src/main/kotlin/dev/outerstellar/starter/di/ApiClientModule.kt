package dev.outerstellar.starter.di

import dev.outerstellar.starter.service.SyncProvider
import dev.outerstellar.starter.sync.SyncService
import org.koin.core.qualifier.named
import org.koin.dsl.module

val apiClientModule = module {
    single<SyncProvider> {
        SyncService(
            baseUrl = get(named("serverBaseUrl")),
            repository = get(),
            transactionManager = get(),
        )
    }
}
