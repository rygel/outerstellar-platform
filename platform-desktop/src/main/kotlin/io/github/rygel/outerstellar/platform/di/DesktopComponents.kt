package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.persistence.NoOpMessageCache
import io.github.rygel.outerstellar.platform.swing.SystemTrayNotifier
import io.github.rygel.outerstellar.platform.swing.analytics.PersistentBatchingAnalyticsService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
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
import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig
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
import java.nio.file.Path
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler

class HttpClients(
    val handler: HttpHandler,
    val session: ApiSession,
    val auth: AuthClient,
    val sync: SyncClient,
    val profile: ProfileClient,
    val admin: AdminClient,
    val notification: NotificationClient,
)

class SyncModules(
    val auth: AuthModule,
    val syncData: SyncDataModule,
    val profile: ProfileModule,
    val admin: AdminModule,
    val notification: NotificationModule,
)

class DesktopComponents(
    val appConfig: DesktopAppConfig,
    val config: AppConfig,
    val persistence: PersistenceComponents,
    val core: CoreComponents,
    val clients: HttpClients,
    val modules: SyncModules,
    val syncViewModel: SyncViewModel,
    val systemTrayNotifier: SystemTrayNotifier,
    val i18nService: I18nService,
    val analyticsService: AnalyticsService,
)

private fun createHttpClients(appConfig: DesktopAppConfig): HttpClients {
    val handler: HttpHandler = JavaHttpClient()
    val session = ApiSession()
    return HttpClients(
        handler = handler,
        session = session,
        auth = HttpAuthClient(appConfig.serverBaseUrl, session, handler),
        sync = HttpSyncClient(appConfig.serverBaseUrl, session, handler),
        profile = HttpProfileClient(appConfig.serverBaseUrl, session, handler),
        admin = HttpAdminClient(appConfig.serverBaseUrl, session, handler),
        notification = HttpNotificationClient(appConfig.serverBaseUrl, session, handler),
    )
}

private fun createSyncModules(
    clients: HttpClients,
    persistence: PersistenceComponents,
    core: CoreComponents,
    analyticsService: AnalyticsService,
): SyncModules {
    lateinit var syncDataModule: SyncDataModule
    lateinit var authModule: AuthModule

    authModule =
        AuthModuleImpl(
            authClient = clients.auth,
            analytics = analyticsService,
            onLoadData = { syncDataModule.loadData() },
            onStartAutoSync = { syncDataModule.startAutoSync() },
            onStopAutoSync = { syncDataModule.stopAutoSync() },
        )
    syncDataModule =
        SyncDataModuleImpl(
            syncClient = clients.sync,
            messageService = core.messageService,
            contactService = core.contactService,
            analytics = analyticsService,
            repository = persistence.messageRepository,
            transactionManager = persistence.transactionManager,
            authStateProvider = { authModule.authState },
        )
    val profileModule =
        ProfileModuleImpl(
            profileClient = clients.profile,
            analytics = analyticsService,
            authStateProvider = { authModule.authState },
            onLoadData = { syncDataModule.loadData() },
            onStopAutoSync = { syncDataModule.stopAutoSync() },
            onLogout = { authModule.logout() },
        )
    val adminModule =
        AdminModuleImpl(
            adminClient = clients.admin,
            analytics = analyticsService,
            authStateProvider = { authModule.authState },
            onStopAutoSync = { syncDataModule.stopAutoSync() },
            onLogout = { authModule.logout() },
        )
    val notificationModule =
        NotificationModuleImpl(
            notificationClient = clients.notification,
            onStopAutoSync = { syncDataModule.stopAutoSync() },
            onLogout = { authModule.logout() },
        )
    return SyncModules(authModule, syncDataModule, profileModule, adminModule, notificationModule)
}

private fun createAnalyticsService(appConfig: DesktopAppConfig): AnalyticsService =
    if (appConfig.analyticsEnabled && appConfig.segmentWriteKey.isNotBlank())
        PersistentBatchingAnalyticsService(
            writeKey = appConfig.segmentWriteKey,
            dataDir = Path.of("./data"),
            maxFileSizeBytes = appConfig.analyticsMaxFileSizeKb * 1024,
            maxEventAgeDays = appConfig.analyticsMaxEventAgeDays,
        )
    else NoOpAnalyticsService()

fun createDesktopComponents(): DesktopComponents {
    val appConfig = DesktopAppConfig.fromEnvironment()
    val config =
        AppConfig(jdbcUrl = appConfig.jdbcUrl, jdbcUser = appConfig.jdbcUser, jdbcPassword = appConfig.jdbcPassword)
    val persistence = createPersistenceComponents(config)
    val core =
        createCoreComponents(
            config = config,
            messageRepository = persistence.messageRepository,
            contactRepository = persistence.contactRepository,
            outboxRepository = persistence.outboxRepository,
            messageCache = NoOpMessageCache,
            transactionManager = persistence.transactionManager,
            auditRepository = persistence.auditRepository,
        )
    val i18nService = I18nService.create("messages")
    val analyticsService = createAnalyticsService(appConfig)
    val clients = createHttpClients(appConfig)
    val modules = createSyncModules(clients, persistence, core, analyticsService)

    val syncViewModel =
        SyncViewModel(
            authModule = modules.auth,
            syncDataModule = modules.syncData,
            profileModule = modules.profile,
            adminModule = modules.admin,
            notificationModule = modules.notification,
            i18nService = i18nService,
        )
    val systemTrayNotifier = SystemTrayNotifier(i18nService)
    return DesktopComponents(
        appConfig = appConfig,
        config = config,
        persistence = persistence,
        core = core,
        clients = clients,
        modules = modules,
        syncViewModel = syncViewModel,
        systemTrayNotifier = systemTrayNotifier,
        i18nService = i18nService,
        analyticsService = analyticsService,
    )
}
