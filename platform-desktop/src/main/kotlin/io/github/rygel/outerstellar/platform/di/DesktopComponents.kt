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

class DesktopComponents(
    val appConfig: DesktopAppConfig,
    val config: AppConfig,
    val persistence: PersistenceComponents,
    val core: CoreComponents,
    val httpHandler: HttpHandler,
    val apiSession: ApiSession,
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
    val syncViewModel: SyncViewModel,
    val systemTrayNotifier: SystemTrayNotifier,
    val i18nService: I18nService,
    val analyticsService: AnalyticsService,
)

@Suppress("LongFunction")
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
    val analyticsService: AnalyticsService =
        if (appConfig.analyticsEnabled && appConfig.segmentWriteKey.isNotBlank())
            PersistentBatchingAnalyticsService(
                writeKey = appConfig.segmentWriteKey,
                dataDir = Path.of("./data"),
                maxFileSizeBytes = appConfig.analyticsMaxFileSizeKb * 1024,
                maxEventAgeDays = appConfig.analyticsMaxEventAgeDays,
            )
        else NoOpAnalyticsService()

    val httpHandler: HttpHandler = JavaHttpClient()
    val apiSession = ApiSession()
    val authClient: AuthClient = HttpAuthClient(appConfig.serverBaseUrl, apiSession, httpHandler)
    val syncClientObj: SyncClient = HttpSyncClient(appConfig.serverBaseUrl, apiSession, httpHandler)
    val profileClient: ProfileClient = HttpProfileClient(appConfig.serverBaseUrl, apiSession, httpHandler)
    val adminClient: AdminClient = HttpAdminClient(appConfig.serverBaseUrl, apiSession, httpHandler)
    val notificationClient: NotificationClient =
        HttpNotificationClient(appConfig.serverBaseUrl, apiSession, httpHandler)

    lateinit var syncDataModule: SyncDataModule
    lateinit var authModule: AuthModule

    authModule =
        AuthModuleImpl(
            authClient = authClient,
            analytics = analyticsService,
            onLoadData = { syncDataModule.loadData() },
            onStartAutoSync = { syncDataModule.startAutoSync() },
            onStopAutoSync = { syncDataModule.stopAutoSync() },
        )
    syncDataModule =
        SyncDataModuleImpl(
            syncClient = syncClientObj,
            messageService = core.messageService,
            contactService = core.contactService,
            analytics = analyticsService,
            repository = persistence.messageRepository,
            transactionManager = persistence.transactionManager,
            authStateProvider = { authModule.authState },
        )
    val profileModule =
        ProfileModuleImpl(
            profileClient = profileClient,
            analytics = analyticsService,
            authStateProvider = { authModule.authState },
            onLoadData = { syncDataModule.loadData() },
            onStopAutoSync = { syncDataModule.stopAutoSync() },
            onLogout = { authModule.logout() },
        )
    val adminModule =
        AdminModuleImpl(
            adminClient = adminClient,
            analytics = analyticsService,
            authStateProvider = { authModule.authState },
            onStopAutoSync = { syncDataModule.stopAutoSync() },
            onLogout = { authModule.logout() },
        )
    val notificationModule =
        NotificationModuleImpl(
            notificationClient = notificationClient,
            onStopAutoSync = { syncDataModule.stopAutoSync() },
            onLogout = { authModule.logout() },
        )

    val syncViewModel =
        SyncViewModel(
            authModule = authModule,
            syncDataModule = syncDataModule,
            profileModule = profileModule,
            adminModule = adminModule,
            notificationModule = notificationModule,
            i18nService = i18nService,
        )
    val systemTrayNotifier = SystemTrayNotifier(i18nService)
    return DesktopComponents(
        appConfig = appConfig,
        config = config,
        persistence = persistence,
        core = core,
        httpHandler = httpHandler,
        apiSession = apiSession,
        authClient = authClient,
        syncClient = syncClientObj,
        profileClient = profileClient,
        adminClient = adminClient,
        notificationClient = notificationClient,
        authModule = authModule,
        syncDataModule = syncDataModule,
        profileModule = profileModule,
        adminModule = adminModule,
        notificationModule = notificationModule,
        syncViewModel = syncViewModel,
        systemTrayNotifier = systemTrayNotifier,
        i18nService = i18nService,
        analyticsService = analyticsService,
    )
}
