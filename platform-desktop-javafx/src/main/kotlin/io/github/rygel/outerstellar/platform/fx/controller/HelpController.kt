package io.github.rygel.outerstellar.platform.fx.controller

import javafx.scene.control.Alert
import javafx.stage.Stage

class HelpController {

    companion object {
        fun show(owner: Stage) {
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.initOwner(owner)
            alert.title = "Help"
            alert.headerText = "Outerstellar Desktop Help"
            alert.contentText = buildString {
                appendLine("Messages:")
                appendLine("- Use the search field to filter messages")
                appendLine("- Click Create to write a new message")
                appendLine("- Click Sync to synchronize with the server")
                appendLine()
                appendLine("Contacts:")
                appendLine("- View and manage your contacts")
                appendLine("- Click Create Contact to add a new contact")
                appendLine("- Double-click a contact row to edit it")
                appendLine()
                appendLine("Settings:")
                appendLine("- Change language and theme")
                appendLine("- Preview themes before applying")
                appendLine()
                appendLine("Keyboard Shortcuts:")
                appendLine("- Ctrl+N: New message")
                appendLine("- Ctrl+S: Save")
            }
            alert.showAndWait()
        }
    }
}
