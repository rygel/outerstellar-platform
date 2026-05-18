package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.MessageSummary
import javafx.fxml.FXMLLoader
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class MessagesController : KoinComponent {

    private companion object {
        const val MESSAGE_PREVIEW_LENGTH = 80
        const val CONTENT_AREA_PREF_HEIGHT = 80.0
    }

    private val logger = LoggerFactory.getLogger(MessagesController::class.java)
    private val viewModel: FxSyncViewModel by inject()
    private val searchField = TextField()
    private val messagesList = ListView<MessageSummary>()
    private val authorField = TextField()
    private val contentArea = TextArea()
    private val createButton = Button("Create")

    fun createView(): Parent =
        VBox(10.0).apply {
            padding = Insets(10.0)

            children.add(
                HBox(10.0).apply {
                    children.addAll(
                        Label("Search:"),
                        searchField.apply { HBox.setHgrow(this, Priority.ALWAYS) },
                        Button("Sync").apply { setOnAction { onSync() } },
                    )
                    alignment = Pos.CENTER_LEFT
                }
            )

            children.add(messagesList.apply { VBox.setVgrow(this, Priority.ALWAYS) })

            children.add(
                VBox(5.0).apply {
                    children.addAll(
                        HBox(10.0).apply {
                            children.addAll(
                                Label("Author:"),
                                authorField.apply { HBox.setHgrow(this, Priority.ALWAYS) },
                            )
                            alignment = Pos.CENTER_LEFT
                        },
                        contentArea.apply {
                            prefHeight = CONTENT_AREA_PREF_HEIGHT
                            isWrapText = true
                        },
                        HBox(10.0).apply {
                            children.add(createButton)
                            alignment = Pos.CENTER_RIGHT
                        },
                    )
                }
            )

            messagesList.items = viewModel.messages
            searchField.textProperty().bindBidirectional(viewModel.searchQuery)
            authorField.textProperty().bindBidirectional(viewModel.author)
            contentArea.textProperty().bindBidirectional(viewModel.content)

            messagesList.setCellFactory {
                object : javafx.scene.control.ListCell<MessageSummary>() {
                    override fun updateItem(item: MessageSummary?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            text = null
                            graphic = null
                            style = null
                        } else {
                            val preview =
                                item.content.take(MESSAGE_PREVIEW_LENGTH) +
                                    if (item.content.length > MESSAGE_PREVIEW_LENGTH) "..." else ""
                            val label = Label("${item.author}: $preview")
                            when {
                                item.hasConflict -> {
                                    label.style = "-fx-text-fill: red;"
                                    label.text = "[CONFLICT] ${label.text}"
                                }
                                item.syncId.isBlank() -> {
                                    label.style = "-fx-text-fill: gray; -fx-font-style: italic;"
                                    label.text = "(Local) ${label.text}"
                                }
                            }
                            graphic = label
                        }
                    }
                }
            }

            messagesList.setOnMouseClicked { event ->
                if (event.clickCount == 2) {
                    val selected = messagesList.selectionModel.selectedItem
                    if (selected != null && selected.hasConflict) {
                        showConflictDialog(selected)
                    }
                }
            }

            searchField.textProperty().addListener { _, _, _ -> viewModel.loadMessages().runInBackground() }
            createButton.setOnAction { onCreateMessage() }

            viewModel.loadMessages().runInBackground()
        }

    private fun onCreateMessage() {
        val author = authorField.text.trim()
        val content = contentArea.text.trim()
        if (author.isBlank() || content.isBlank()) return
        viewModel
            .createLocalMessage(author, content)
            .also { task ->
                task.setOnSucceeded {
                    task.value
                        .onSuccess {
                            authorField.clear()
                            contentArea.clear()
                            viewModel.loadMessages().runInBackground()
                        }
                        .onFailure { logger.warn("Create message failed: {}", it.message) }
                }
            }
            .runInBackground()
    }

    private fun onSync() {
        viewModel
            .sync()
            .also { task ->
                task.setOnSucceeded {
                    task.value
                        .onSuccess { viewModel.loadMessages().runInBackground() }
                        .onFailure { logger.warn("Sync failed: {}", it.message) }
                }
            }
            .runInBackground()
    }

    private fun showConflictDialog(msg: MessageSummary) {
        val loader = FXMLLoader(javaClass.getResource("/fxml/ConflictDialog.fxml"))
        val root = loader.load<Parent>()
        val controller = loader.getController<ConflictController>()
        controller.setMessage(msg)
        val stage = Stage()
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.title = "Resolve Sync Conflict"
        stage.scene = Scene(root)
        stage.showAndWait()
        if (controller.conflictStrategy != null) {
            viewModel.loadMessages().runInBackground()
        }
    }
}
