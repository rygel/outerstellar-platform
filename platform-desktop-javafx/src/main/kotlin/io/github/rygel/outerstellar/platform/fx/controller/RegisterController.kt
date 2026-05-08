package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.sync.SyncService
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RegisterController : KoinComponent {

    private val syncService: SyncService by inject()

    @FXML private lateinit var usernameField: TextField
    @FXML private lateinit var passwordField: PasswordField
    @FXML private lateinit var confirmField: PasswordField

    @FXML
    fun onRegister() {
        val username = usernameField.text.trim()
        val password = passwordField.text
        val confirm = confirmField.text
        if (username.isBlank() || password.isBlank()) return
        if (password != confirm) return
        Thread {
                try {
                    syncService.register(username, password)
                    Platform.runLater { close() }
                } catch (_: Exception) {}
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onCancel() {
        close()
    }

    private fun close() {
        val stage = usernameField.scene.window as Stage
        stage.close()
    }
}
