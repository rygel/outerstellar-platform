package io.github.rygel.outerstellar.platform.fx.controller

import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextArea
import javafx.stage.Stage

class FeedbackController {

    @FXML private lateinit var feedbackTextArea: TextArea
    @FXML private lateinit var sendButton: Button

    @FXML fun initialize() {}

    @FXML
    fun onCancel() {
        (sendButton.scene.window as Stage).close()
    }
}
