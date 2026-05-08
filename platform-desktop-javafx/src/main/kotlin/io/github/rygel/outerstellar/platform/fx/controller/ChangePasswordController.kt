package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.sync.SyncService
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.PasswordField
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class ChangePasswordController : KoinComponent {

    private val logger = LoggerFactory.getLogger(ChangePasswordController::class.java)
    private val syncService: SyncService by inject()

    @FXML private lateinit var currentField: PasswordField
    @FXML private lateinit var newField: PasswordField
    @FXML private lateinit var confirmField: PasswordField

    @FXML
    fun onChange() {
        val current = currentField.text
        val newPass = newField.text
        val confirm = confirmField.text
        if (current.isBlank() || newPass.isBlank()) return
        if (newPass != confirm) return
        Thread {
                try {
                    syncService.changePassword(current, newPass)
                    Platform.runLater { close() }
                } catch (e: Exception) {
                    logger.warn("Password change failed: {}", e.message)
                }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onCancel() {
        close()
    }

    private fun close() {
        (currentField.scene.window as? Stage)?.close()
    }
}
