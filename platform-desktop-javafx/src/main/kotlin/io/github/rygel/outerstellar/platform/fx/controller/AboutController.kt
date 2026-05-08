package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.app.FxAppConfig
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AboutController : KoinComponent {

    private val appConfig: FxAppConfig by inject()

    @FXML private lateinit var versionLabel: Label

    @FXML
    fun initialize() {
        versionLabel.text = "Version: ${appConfig.version}"
    }

    @FXML
    fun onClose() {
        (versionLabel.scene.window as? Stage)?.close()
    }
}
