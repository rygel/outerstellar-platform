package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.MessageSummary
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.stage.Modality
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class MessagesController : KoinComponent {

    private companion object {
        const val MESSAGE_PREVIEW_LENGTH = 80
    }

    private val logger = LoggerFactory.getLogger(MessagesController::class.java)
    private val viewModel: FxSyncViewModel by inject()
    private val themeManager: FxThemeManager by inject()

    @FXML private lateinit var searchField: TextField
    @FXML private lateinit var messagesList: ListView<MessageSummary>
    @FXML private lateinit var authorField: TextField
    @FXML private lateinit var contentArea: TextArea

    @FXML
    fun initialize() {
        messagesList.itemsProperty().bind(SimpleObjectProperty(viewModel.messages))
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
                        val label =
                            Label(
                                "${item.author}: ${item.content.take(MESSAGE_PREVIEW_LENGTH)}${if (item.content.length > MESSAGE_PREVIEW_LENGTH) "..." else ""}"
                            )
                        if (item.hasConflict) {
                            label.style = "-fx-text-fill: red;"
                            label.text = "[CONFLICT] ${label.text}"
                        } else if (item.syncId.isBlank()) {
                            label.style = "-fx-text-fill: gray; -fx-font-style: italic;"
                            label.text = "(Local) ${label.text}"
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
        loadMessages()
    }

    @FXML
    fun onCreateMessage() {
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
                            loadMessages()
                        }
                        .onFailure { logger.warn("Create message failed: {}", it.message) }
                }
            }
            .runInBackground()
    }

    @FXML
    fun onSync() {
        viewModel
            .sync()
            .also { task ->
                task.setOnSucceeded {
                    task.value.onSuccess { loadMessages() }.onFailure { logger.warn("Sync failed: {}", it.message) }
                }
            }
            .runInBackground()
    }

    private fun loadMessages() {
        viewModel.loadMessages().runInBackground()
    }

    private fun showConflictDialog(msg: MessageSummary) {
        val loader = FXMLLoader(javaClass.getResource("/fxml/ConflictDialog.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        val controller = loader.getController<ConflictController>()
        controller.setMessage(msg)
        val stage = Stage()
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.title = "Resolve Sync Conflict"
        val scene = Scene(root)
        themeManager.setScene(scene)
        stage.scene = scene
        stage.showAndWait()
        if (controller.conflictStrategy != null) {
            loadMessages()
        }
    }
}
