package io.github.rygel.outerstellar.platform.fx.app

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.di.createSyncModules
import io.github.rygel.outerstellar.platform.fx.FxAppContext
import io.github.rygel.outerstellar.platform.fx.controller.MainController
import io.github.rygel.outerstellar.platform.fx.service.FxStateProvider
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.service.FxTrayNotifier
import io.github.rygel.outerstellar.platform.fx.service.FxWindowState
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.persistence.JdbiContactRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiOutboxRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiTransactionManager
import io.github.rygel.outerstellar.platform.persistence.NoOpMessageCache
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NoOpEventPublisher
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
import io.github.rygel.outerstellar.platform.sync.engine.HttpConnectivityChecker
import io.micrometer.core.instrument.Metrics
import java.awt.Desktop
import java.net.URI
import java.util.Locale
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.http4k.client.JavaHttpClient
import org.jdbi.v3.core.Jdbi

private class HttpClients(
    val auth: AuthClient,
    val sync: SyncClient,
    val profile: ProfileClient,
    val admin: AdminClient,
    val notification: NotificationClient,
)

private class Repositories(
    val messageRepository: JdbiMessageRepository,
    val transactionManager: JdbiTransactionManager,
    val messageService: MessageService,
    val contactService: ContactService,
)

class JavaFxApp : Application() {

    private lateinit var viewModel: FxSyncViewModel

    override fun init() {
        val fxConfig = FxAppConfig.fromEnvironment()
        val clients = createHttpClients(fxConfig)
        val repositories = createRepositories(fxConfig)
        val analytics = NoOpAnalyticsService()
        val modules =
            createSyncModules(
                syncClient = clients.sync,
                authClient = clients.auth,
                profileClient = clients.profile,
                adminClient = clients.admin,
                notificationClient = clients.notification,
                messageService = repositories.messageService,
                contactService = repositories.contactService,
                analytics = analytics,
                repository = repositories.messageRepository,
                transactionManager = repositories.transactionManager,
                notifier = FxTrayNotifier,
            )
        val i18nService = I18nService.create("messages")

        viewModel =
            FxSyncViewModel(
                modules.auth,
                modules.syncData,
                modules.profile,
                modules.admin,
                modules.notification,
                i18nService,
            )

        FxAppContext.viewModel = viewModel
        FxAppContext.themeManager = FxThemeManager()
        FxAppContext.i18nService = i18nService
        FxAppContext.appConfig = fxConfig
        FxAppContext.authModule = modules.auth
    }

    private fun createHttpClients(config: FxAppConfig): HttpClients {
        val httpClient = JavaHttpClient()
        val apiSession = ApiSession()
        return HttpClients(
            auth = HttpAuthClient(config.serverBaseUrl, apiSession, httpClient),
            sync = HttpSyncClient(config.serverBaseUrl, apiSession, httpClient),
            profile = HttpProfileClient(config.serverBaseUrl, apiSession, httpClient),
            admin = HttpAdminClient(config.serverBaseUrl, apiSession, httpClient),
            notification = HttpNotificationClient(config.serverBaseUrl, apiSession, httpClient),
        )
    }

    private fun createRepositories(config: FxAppConfig): Repositories {
        val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)
        try {
            migrate(ds)
        } catch (e: Exception) {
            ds.close()
            throw e
        }

        val jdbi =
            Jdbi.create(ds).also {
                if (Metrics.globalRegistry.find("database.connections.active").gauge() == null) {
                    Metrics.globalRegistry.gauge("database.connections.active", 1)
                }
            }

        val messageRepository = JdbiMessageRepository(jdbi)
        val contactRepository = JdbiContactRepository(jdbi)
        val outboxRepository = JdbiOutboxRepository(jdbi)
        val transactionManager = JdbiTransactionManager(jdbi)
        val messageService = MessageService(messageRepository, outboxRepository, transactionManager, NoOpMessageCache)
        val contactService = ContactService(contactRepository, NoOpEventPublisher, transactionManager)
        return Repositories(messageRepository, transactionManager, messageService, contactService)
    }

    override fun start(primaryStage: Stage) {
        val splash = showSplash(primaryStage)

        val config = FxAppContext.appConfig
        val themeManager = FxAppContext.themeManager

        val connectivityChecker =
            HttpConnectivityChecker(healthUrl = "${config.serverBaseUrl}/health").also { it.start() }

        val savedState = FxStateProvider.loadState()
        val locale = savedState?.language?.let { Locale.of(it) } ?: Locale.getDefault()
        Locale.setDefault(locale)
        FxAppContext.i18nService.setLocale(locale)

        val loader = FXMLLoader(javaClass.getResource("/fxml/MainWindow.fxml"))
        val root = loader.load<javafx.scene.Parent>()

        val width = savedState?.width ?: DEFAULT_WIDTH
        val height = savedState?.height ?: DEFAULT_HEIGHT
        val scene = Scene(root, width, height)

        loader.getController<MainController>().createScene(scene)

        themeManager.setScene(scene)
        savedState?.themeId?.let { themeManager.applyThemeByName(it) } ?: themeManager.applyThemeByName("DARK")

        savedState?.lastSearchQuery?.let { viewModel.searchQuery.set(it) }

        primaryStage.title = FxAppContext.i18nService.translate("javafx.app.title", "Outerstellar")
        if (savedState != null) {
            primaryStage.x = savedState.x
            primaryStage.y = savedState.y
            primaryStage.isMaximized = savedState.maximized
        }

        primaryStage.onCloseRequest = {
            viewModel.stopAutoSync()
            viewModel.shutdown()
            connectivityChecker.stop()
            FxStateProvider.saveState(
                FxWindowState(
                    x = primaryStage.x,
                    y = primaryStage.y,
                    width = scene.width,
                    height = scene.height,
                    maximized = primaryStage.isMaximized,
                    themeId = themeManager.currentThemeName(),
                    language = locale.language,
                    lastSearchQuery = viewModel.searchQuery.get().takeIf { it.isNotBlank() },
                )
            )
        }

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
            Desktop.getDesktop().setOpenURIHandler { event -> handleDeepLink(event.uri) }
        }

        if (config.devMode && config.devUsername.isNotBlank() && config.devPassword.isNotBlank()) {
            viewModel.login(config.devUsername, config.devPassword).runInBackground()
        }

        primaryStage.scene = scene
        splash.close()
        primaryStage.show()
    }

    private fun showSplash(primaryStage: Stage): Stage {
        val splash = Stage()
        splash.initStyle(StageStyle.UNDECORATED)
        splash.initOwner(primaryStage)
        val content =
            VBox(
                20.0,
                Label("Outerstellar").apply { style = "-fx-font-size: 24px; -fx-font-weight: bold;" },
                Label("Starting application...").apply { style = "-fx-font-size: 14px;" },
            )
        content.style = "-fx-padding: 40; -fx-alignment: center;"
        splash.scene = Scene(content, 400.0, 300.0)
        splash.show()
        return splash
    }

    private fun handleDeepLink(uri: URI) {
        if (uri.scheme != "outerstellar") return
        when (uri.host) {
            "search" -> {
                val query = uri.query?.removePrefix("q=") ?: return
                Platform.runLater { viewModel.searchQuery.set(query) }
            }
            "sync" -> Platform.runLater { viewModel.sync().runInBackground() }
        }
    }

    companion object {
        private const val DEFAULT_WIDTH = 1200.0
        private const val DEFAULT_HEIGHT = 800.0

        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(JavaFxApp::class.java)
        }
    }
}
