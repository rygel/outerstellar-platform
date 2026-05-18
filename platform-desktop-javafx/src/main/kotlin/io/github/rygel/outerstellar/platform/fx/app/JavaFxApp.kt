package io.github.rygel.outerstellar.platform.fx.app

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.fx.di.fxRuntimeModules
import io.github.rygel.outerstellar.platform.fx.service.FxStateProvider
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.service.FxWindowState
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.sync.engine.HttpConnectivityChecker
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

class JavaFxApp : Application(), KoinComponent {

    private lateinit var viewModel: FxSyncViewModel

    override fun init() {
        startKoin { modules(fxRuntimeModules()) }
    }

    override fun start(primaryStage: Stage) {
        val splash = showSplash(primaryStage)

        viewModel = get()
        val config = get<FxAppConfig>()
        val themeManager = get<FxThemeManager>()

        val connectivityChecker =
            HttpConnectivityChecker(healthUrl = "${config.serverBaseUrl}/health").also { it.start() }

        val savedState = FxStateProvider.loadState()
        val locale = savedState?.language?.let { Locale.of(it) } ?: Locale.getDefault()
        Locale.setDefault(locale)
        val i18nService: I18nService = get()
        i18nService.setLocale(locale)

        val loader = FXMLLoader(javaClass.getResource("/fxml/MainWindow.fxml"))
        val root = loader.load<javafx.scene.Parent>()

        val width = savedState?.width ?: DEFAULT_WIDTH
        val height = savedState?.height ?: DEFAULT_HEIGHT
        val scene = Scene(root, width, height)

        themeManager.setScene(scene)
        savedState?.themeId?.let { themeManager.applyThemeByName(it) } ?: themeManager.applyThemeByName("DARK")

        savedState?.lastSearchQuery?.let { viewModel.searchQuery.set(it) }

        primaryStage.title = i18nService.translate("javafx.app.title", "Outerstellar")
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
