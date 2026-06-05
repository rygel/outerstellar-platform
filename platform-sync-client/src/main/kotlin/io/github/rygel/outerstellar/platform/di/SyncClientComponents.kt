package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
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
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler

class SyncClientComponents(
    val session: ApiSession,
    val authClient: AuthClient,
    val syncClient: SyncClient,
    val profileClient: ProfileClient,
    val adminClient: AdminClient,
    val notificationClient: NotificationClient,
    val authModule: AuthModule,
    val syncDataModule: SyncDataModule,
    val profileModule: ProfileModule,
    val adminModule: AdminModule,
    val notificationModule: NotificationModule,
)

fun createSyncClientComponents(
    baseUrl: String,
    analytics: AnalyticsService,
    messageService: MessageService,
    contactService: ContactService?,
    repository: MessageRepository,
    transactionManager: TransactionManager,
    notifier: ModuleNotifier? = null,
    connectivityChecker: ConnectivityChecker? = null,
): SyncClientComponents {
    val session = ApiSession()
    val httpClient: HttpHandler = JavaHttpClient()

    val authClient = HttpAuthClient(baseUrl, session, httpClient)
    val syncClient = HttpSyncClient(baseUrl, session, httpClient)
    val profileClient = HttpProfileClient(baseUrl, session, httpClient)
    val adminClient = HttpAdminClient(baseUrl, session, httpClient)
    val notificationClient = HttpNotificationClient(baseUrl, session, httpClient)

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

    return SyncClientComponents(
        session = session,
        authClient = authClient,
        syncClient = syncClient,
        profileClient = profileClient,
        adminClient = adminClient,
        notificationClient = notificationClient,
        authModule = authModule,
        syncDataModule = syncDataModule,
        profileModule = profileModule,
        adminModule = adminModule,
        notificationModule = notificationModule,
    )
}
