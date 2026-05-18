package io.github.rygel.outerstellar.platform.fx.controller

import javafx.scene.control.Alert
import javafx.stage.Stage

class FeedbackController {

    companion object {
        fun show(owner: Stage) {
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.initOwner(owner)
            alert.title = "Feedback"
            alert.headerText = "Send Feedback"
            alert.contentText = buildString {
                appendLine("We'd love to hear from you!")
                appendLine()
                appendLine("Please send your feedback, bug reports,")
                appendLine("and feature requests to:")
                appendLine("feedback@outerstellar.app")
                appendLine()
                append("Visit: https://outerstellar.app/feedback")
            }
            alert.showAndWait()
        }
    }
}
