package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ResetPasswordController : KoinComponent {

    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var emailField: TextField
    @FXML private lateinit var sendButton: Button

    @FXML fun initialize() {}

    @FXML
    fun onCancel() {
        (sendButton.scene.window as Stage).close()
    }
}
