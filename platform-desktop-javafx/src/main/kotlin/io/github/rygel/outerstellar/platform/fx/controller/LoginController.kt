package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class LoginController : KoinComponent {

    private val logger = LoggerFactory.getLogger(LoginController::class.java)
    private val engine: DesktopSyncEngine by inject()

    @FXML private lateinit var usernameField: TextField
    @FXML private lateinit var passwordField: PasswordField

    var loginSucceeded: Boolean = false
        private set

    @FXML
    fun onLogin() {
        val username = usernameField.text.trim()
        val password = passwordField.text
        if (username.isBlank() || password.isBlank()) return
        performLogin(username, password)
    }

    @FXML
    fun onCancel() {
        close()
    }

    private fun performLogin(user: String, pass: String) {
        Thread {
                engine
                    .login(user, pass)
                    .onSuccess {
                        loginSucceeded = true
                        Platform.runLater { close() }
                    }
                    .onFailure {
                        logger.warn("Login failed: {}", it.message)
                        loginSucceeded = false
                    }
            }
            .also { it.isDaemon = true }
            .start()
    }

    private fun close() {
        (usernameField.scene.window as? Stage)?.close()
    }
}
