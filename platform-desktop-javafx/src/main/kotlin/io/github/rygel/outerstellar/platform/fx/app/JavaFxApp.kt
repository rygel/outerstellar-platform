package io.github.rygel.outerstellar.platform.fx.app

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.fx.di.fxRuntimeModules
import io.github.rygel.outerstellar.platform.fx.service.FxStateProvider
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import java.util.Locale
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage
import org.koin.core.context.startKoin

class JavaFxApp : Application() {

    override fun init() {
        startKoin { modules(fxRuntimeModules()) }
    }

    override fun start(primaryStage: Stage) {
        val savedState = FxStateProvider.loadState()
        val locale = savedState?.language?.let { Locale.of(it) } ?: Locale.getDefault()
        Locale.setDefault(locale)
        val i18nService = I18nService.create("messages").also { it.setLocale(locale) }

        val loader = FXMLLoader(javaClass.getResource("/fxml/MainWindow.fxml"))
        val root = loader.load<javafx.scene.Parent>()

        val width = savedState?.width ?: DEFAULT_WIDTH
        val height = savedState?.height ?: DEFAULT_HEIGHT
        val scene = Scene(root, width, height)

        val themeManager = FxThemeManager()
        themeManager.setScene(scene)
        savedState?.themeId?.let { themeManager.applyThemeByName(it) } ?: themeManager.applyThemeByName("DARK")

        primaryStage.title = i18nService.translate("javafx.app.title", "Outerstellar")
        if (savedState != null) {
            primaryStage.x = savedState.x
            primaryStage.y = savedState.y
            primaryStage.isMaximized = savedState.maximized
        }

        primaryStage.onCloseRequest = {
            FxStateProvider.saveState(
                io.github.rygel.outerstellar.platform.fx.service.FxWindowState(
                    x = primaryStage.x,
                    y = primaryStage.y,
                    width = scene.width,
                    height = scene.height,
                    maximized = primaryStage.isMaximized,
                    themeId = themeManager.currentThemeName(),
                    language = locale.language,
                )
            )
        }

        primaryStage.scene = scene
        primaryStage.show()
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
