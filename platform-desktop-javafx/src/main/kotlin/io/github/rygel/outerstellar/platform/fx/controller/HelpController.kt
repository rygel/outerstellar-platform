package io.github.rygel.outerstellar.platform.fx.controller

import javafx.fxml.FXML
import javafx.scene.control.TextArea
import javafx.stage.Stage

class HelpController {

    @FXML private lateinit var helpTextArea: TextArea

    @FXML
    fun onClose() {
        val stage = helpTextArea.scene.window as Stage
        stage.close()
    }
}
