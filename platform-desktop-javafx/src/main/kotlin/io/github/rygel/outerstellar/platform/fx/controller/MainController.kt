package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.beans.binding.Bindings
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainController(private val onLogout: () -> Unit) : KoinComponent {

    private val viewModel: FxSyncViewModel by inject()
    private val statusLabel = Label()
    private val userNameLabel = Label()
    private val userRoleLabel = Label()
    private val onlineLabel = Label()
    private val syncButton = Button("Sync Now")
    private val logoutButton = Button("Logout")

    fun createScene(): Scene {
        val header =
            VBox(5.0).apply {
                padding = Insets(20.0)
                alignment = Pos.CENTER
                children.addAll(
                    Label("Outerstellar").apply { style = "-fx-font-size: 20px; -fx-font-weight: bold;" },
                    userNameLabel,
                    userRoleLabel,
                )
            }

        val statusSection =
            VBox(10.0).apply {
                padding = Insets(20.0)
                children.addAll(
                    Label("Sync Status").apply { style = "-fx-font-weight: bold;" },
                    statusLabel,
                    onlineLabel,
                )
            }

        val buttonBar =
            HBox(10.0).apply {
                alignment = Pos.CENTER
                padding = Insets(10.0)
                children.addAll(syncButton, logoutButton)
            }

        val centerBox =
            VBox(20.0).apply {
                alignment = Pos.TOP_CENTER
                padding = Insets(20.0)
                children.addAll(statusSection, buttonBar)
            }

        val root =
            BorderPane().apply {
                top = header
                center = centerBox
            }

        val scene = Scene(root, 800.0, 600.0)

        statusLabel.textProperty().bind(viewModel.status)
        onlineLabel.textProperty().bind(Bindings.`when`(viewModel.isOnline).then("Online").otherwise("Offline"))
        userNameLabel.textProperty().bind(viewModel.userName)
        userRoleLabel.textProperty().bind(viewModel.userRole)
        syncButton.setOnAction { viewModel.sync().runInBackground() }
        logoutButton.setOnAction { onLogout() }

        return scene
    }
}
