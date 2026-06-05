package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.client.AdminClient
import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import io.github.rygel.outerstellar.platform.sync.client.NotificationClient
import io.github.rygel.outerstellar.platform.sync.client.ProfileClient
import io.github.rygel.outerstellar.platform.sync.client.SyncClient
import io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker
import io.github.rygel.outerstellar.platform.sync.engine.DefaultSessionLifecycle
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.ModuleNotifier
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModuleImpl

class SyncModules(
    val auth: AuthModule,
    val syncData: SyncDataModule,
    val profile: ProfileModule,
    val admin: AdminModule,
    val notification: NotificationModule,
)

@Suppress("LongParameterList")
fun createSyncModules(
    syncClient: SyncClient,
    authClient: AuthClient,
    profileClient: ProfileClient,
    adminClient: AdminClient,
    notificationClient: NotificationClient,
    messageService: MessageService,
    contactService: ContactService?,
    analytics: AnalyticsService,
    repository: MessageRepository,
    transactionManager: TransactionManager,
    notifier: ModuleNotifier? = null,
    connectivityChecker: ConnectivityChecker? = null,
): SyncModules {
    val lifecycle = DefaultSessionLifecycle()

    val syncDataModule: SyncDataModule =
        SyncDataModuleImpl(
            syncClient = syncClient,
            messageService = messageService,
            contactService = contactService,
            analytics = analytics,
            repository = repository,
            transactionManager = transactionManager,
            lifecycle = lifecycle,
            notifier = notifier,
            connectivityChecker = connectivityChecker,
        )
    val authModule: AuthModule =
        AuthModuleImpl(authClient = authClient, analytics = analytics, lifecycle = lifecycle, notifier = notifier)
    val profileModule: ProfileModule =
        ProfileModuleImpl(
            profileClient = profileClient,
            analytics = analytics,
            lifecycle = lifecycle,
            notifier = notifier,
        )
    val adminModule: AdminModule =
        AdminModuleImpl(adminClient = adminClient, analytics = analytics, lifecycle = lifecycle)
    val notificationModule: NotificationModule =
        NotificationModuleImpl(notificationClient = notificationClient, lifecycle = lifecycle)

    lifecycle.initialize(syncDataModule = syncDataModule, authModule = authModule, authClient = authClient)

    return SyncModules(authModule, syncDataModule, profileModule, adminModule, notificationModule)
}
