package io.github.rygel.outerstellar.platform.fx.app

import io.github.rygel.outerstellar.platform.fx.controller.LoginController
import io.github.rygel.outerstellar.platform.fx.controller.MainController
import io.github.rygel.outerstellar.platform.fx.di.fxRuntimeModules
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

class JavaFxApp : Application(), KoinComponent {

    override fun init() {
        startKoin { modules(fxRuntimeModules()) }
    }

    override fun start(primaryStage: Stage) {
        val splash = showSplash(primaryStage)
        val themeManager = get<FxThemeManager>()

        showLogin(primaryStage, themeManager)
        splash.close()
        primaryStage.show()
    }

    private fun showLogin(primaryStage: Stage, themeManager: FxThemeManager) {
        val loginController = LoginController(onLoginSuccess = { showMainWindow(primaryStage, themeManager) })
        val scene = loginController.createScene()
        themeManager.applyThemeByName("DARK")
        primaryStage.title = "Outerstellar - Login"
        primaryStage.minWidth = LOGIN_MIN_WIDTH
        primaryStage.minHeight = LOGIN_MIN_HEIGHT
        primaryStage.scene = scene
    }

    private fun showMainWindow(primaryStage: Stage, themeManager: FxThemeManager) {
        val mainController = MainController(onLogout = { primaryStage.close() })
        val scene = mainController.createScene()
        themeManager.applyThemeByName("DARK")
        primaryStage.title = "Outerstellar"
        primaryStage.minWidth = MAIN_MIN_WIDTH
        primaryStage.minHeight = MAIN_MIN_HEIGHT
        primaryStage.scene = scene
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

    companion object {
        private const val LOGIN_MIN_WIDTH = 400.0
        private const val LOGIN_MIN_HEIGHT = 350.0
        private const val MAIN_MIN_WIDTH = 800.0
        private const val MAIN_MIN_HEIGHT = 600.0

        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(JavaFxApp::class.java)
        }
    }
}
