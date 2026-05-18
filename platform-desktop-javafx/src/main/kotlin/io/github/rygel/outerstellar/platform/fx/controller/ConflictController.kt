package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.MessageSummary
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage

class ConflictController {

    var conflictStrategy: ConflictStrategy? = null
        private set

    private lateinit var message: MessageSummary

    fun setMessage(msg: MessageSummary) {
        message = msg
    }

    fun showAndWait() {
        val stage =
            Stage().apply {
                initModality(Modality.APPLICATION_MODAL)
                title = "Resolve Sync Conflict"
            }

        val localContent =
            TextArea().apply {
                text = message.content
                isEditable = false
                prefRowCount = 5
                prefColumnCount = 30
                isWrapText = true
            }
        VBox.setVgrow(localContent, Priority.ALWAYS)

        val localPane =
            VBox(8.0).apply {
                padding = Insets(12.0)
                children.addAll(
                    Label("My Local Version").apply { style = "-fx-font-weight: bold" },
                    Label("Author: ${message.author}"),
                    localContent,
                )
            }

        val serverContent =
            TextArea().apply {
                text = message.content
                isEditable = false
                prefRowCount = 5
                prefColumnCount = 30
                isWrapText = true
            }
        VBox.setVgrow(serverContent, Priority.ALWAYS)

        val serverPane =
            VBox(8.0).apply {
                padding = Insets(12.0)
                children.addAll(
                    Label("Server Version").apply { style = "-fx-font-weight: bold" },
                    Label("Author: ${message.author}"),
                    serverContent,
                    Label("Accepting it will overwrite your local changes").apply {
                        style = "-fx-text-fill: gray; -fx-font-style: italic"
                    },
                )
            }

        val columns = HBox(8.0).apply { children.addAll(localPane, serverPane) }

        val keepMineBtn =
            Button("Keep Mine").apply {
                setOnAction {
                    conflictStrategy = ConflictStrategy.MINE
                    stage.close()
                }
            }

        val acceptServerBtn =
            Button("Accept Server").apply {
                setOnAction {
                    conflictStrategy = ConflictStrategy.SERVER
                    stage.close()
                }
            }

        val buttonBar =
            HBox(8.0).apply {
                alignment = Pos.CENTER
                padding = Insets(4.0, 12.0, 12.0, 12.0)
                children.addAll(keepMineBtn, acceptServerBtn)
            }

        val root =
            VBox(8.0).apply {
                padding = Insets(8.0)
                children.addAll(columns, buttonBar)
            }

        stage.scene = Scene(root)
        stage.showAndWait()
    }
}
