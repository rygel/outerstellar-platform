package io.github.rygel.outerstellar.platform.fx.controller

import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextArea
import javafx.stage.Stage

class FeedbackController {

    @FXML private lateinit var feedbackTextArea: TextArea
    @FXML private lateinit var sendButton: Button

    @FXML
    fun onSend() {
        val text = feedbackTextArea.text
        if (text.isBlank()) return
        if (
            java.awt.Desktop.isDesktopSupported() &&
                java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MAIL)
        ) {
            java.awt.Desktop.getDesktop()
                .mail(
                    java.net.URI.create(
                        "mailto:feedback@outerstellar.app?body=${java.net.URLEncoder.encode(text, "UTF-8")}"
                    )
                )
        }
        (sendButton.scene.window as Stage).close()
    }

    @FXML
    fun onCancel() {
        (sendButton.scene.window as Stage).close()
    }
}
