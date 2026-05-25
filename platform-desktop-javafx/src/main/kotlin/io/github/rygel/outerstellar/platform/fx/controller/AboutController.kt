package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.FxAppContext
import io.github.rygel.outerstellar.platform.fx.app.FxAppConfig
import javafx.scene.control.Alert
import javafx.stage.Stage

object AboutController {
    private val appConfig: FxAppConfig
        get() = FxAppContext.appConfig

    fun show(owner: Stage) {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.initOwner(owner)
        alert.title = "About"
        alert.headerText = "Outerstellar"
        alert.contentText = buildString {
            appendLine("Version: ${appConfig.version}")
            appendLine()
            appendLine("Licensed under the MIT License.")
            append("Built with Kotlin, JavaFX, and AtlantaFX.")
        }
        alert.showAndWait()
    }
}
