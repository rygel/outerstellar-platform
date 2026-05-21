package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.sync.client.AdminClient
import io.github.rygel.outerstellar.platform.sync.client.ApiSession
import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import io.github.rygel.outerstellar.platform.sync.client.HttpAdminClient
import io.github.rygel.outerstellar.platform.sync.client.HttpAuthClient
import io.github.rygel.outerstellar.platform.sync.client.HttpNotificationClient
import io.github.rygel.outerstellar.platform.sync.client.HttpProfileClient
import io.github.rygel.outerstellar.platform.sync.client.HttpSyncClient
import io.github.rygel.outerstellar.platform.sync.client.NotificationClient
import io.github.rygel.outerstellar.platform.sync.client.ProfileClient
import io.github.rygel.outerstellar.platform.sync.client.SyncClient
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModuleImpl
import org.koin.core.qualifier.named
import org.koin.dsl.module

val apiClientModule
    get() = module {
        single { ApiSession() }
        single<AuthClient> { HttpAuthClient(get(named("serverBaseUrl")), get(), get()) }
        single<SyncClient> { HttpSyncClient(get(named("serverBaseUrl")), get(), get()) }
        single<ProfileClient> { HttpProfileClient(get(named("serverBaseUrl")), get(), get()) }
        single<AdminClient> { HttpAdminClient(get(named("serverBaseUrl")), get(), get()) }
        single<NotificationClient> { HttpNotificationClient(get(named("serverBaseUrl")), get(), get()) }

        single<AuthModule> {
            AuthModuleImpl(
                authClient = get(),
                analytics = get(),
                onLoadData = { get<SyncDataModule>().loadData() },
                onStartAutoSync = { get<SyncDataModule>().startAutoSync() },
                onStopAutoSync = { get<SyncDataModule>().stopAutoSync() },
                notifier = getOrNull(),
            )
        }
        single<SyncDataModule> {
            SyncDataModuleImpl(
                syncClient = get(),
                messageService = get(),
                contactService = getOrNull(),
                analytics = get(),
                repository = get(),
                transactionManager = get(),
                authStateProvider = { get<AuthModule>().authState },
                notifier = getOrNull(),
                connectivityChecker = getOrNull(),
            )
        }
        single<ProfileModule> {
            ProfileModuleImpl(
                profileClient = get(),
                analytics = get(),
                authStateProvider = { get<AuthModule>().authState },
                onLoadData = { get<SyncDataModule>().loadData() },
                onStopAutoSync = { get<SyncDataModule>().stopAutoSync() },
                onLogout = { get<AuthModule>().logout() },
                notifier = getOrNull(),
            )
        }
        single<AdminModule> {
            AdminModuleImpl(
                adminClient = get(),
                analytics = get(),
                authStateProvider = { get<AuthModule>().authState },
                onStopAutoSync = { get<SyncDataModule>().stopAutoSync() },
                onLogout = { get<AuthModule>().logout() },
            )
        }
        single<NotificationModule> {
            NotificationModuleImpl(
                notificationClient = get(),
                onStopAutoSync = { get<SyncDataModule>().stopAutoSync() },
                onLogout = { get<AuthModule>().logout() },
            )
        }
    }
