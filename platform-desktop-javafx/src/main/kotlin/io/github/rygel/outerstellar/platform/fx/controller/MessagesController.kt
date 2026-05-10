package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
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

class MessagesController : KoinComponent {

    private companion object {
        const val MESSAGE_PREVIEW_LENGTH = 80
    }

    private val logger = LoggerFactory.getLogger(MessagesController::class.java)
    private val engine: DesktopSyncEngine by inject()
    private val themeManager: FxThemeManager by inject()

    @FXML private lateinit var searchField: TextField
    @FXML private lateinit var messagesList: ListView<MessageSummary>
    @FXML private lateinit var authorField: TextField
    @FXML private lateinit var contentArea: TextArea

    private val allMessages: ObservableList<MessageSummary> = FXCollections.observableArrayList()

    @FXML
    fun initialize() {
        messagesList.setCellFactory {
            object : javafx.scene.control.ListCell<MessageSummary>() {
                override fun updateItem(item: MessageSummary?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        graphic = null
                    } else {
                        graphic =
                            Label(
                                "${item.author}: ${item.content.take(MESSAGE_PREVIEW_LENGTH)}${if (item.content.length > MESSAGE_PREVIEW_LENGTH) "..." else ""}"
                            )
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
        searchField.textProperty().addListener { _, _, newValue -> filterMessages(newValue) }
        loadMessages()
    }

    @FXML
    fun onCreateMessage() {
        val author = authorField.text.trim()
        val content = contentArea.text.trim()
        if (author.isBlank() || content.isBlank()) return
        Thread {
                engine
                    .createLocalMessage(author, content)
                    .onSuccess {
                        Platform.runLater {
                            authorField.clear()
                            contentArea.clear()
                            loadMessages()
                        }
                    }
                    .onFailure { logger.warn("Create message failed: {}", it.message) }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onSync() {
        Thread {
                engine
                    .sync()
                    .onSuccess { Platform.runLater { loadMessages() } }
                    .onFailure { logger.warn("Sync failed: {}", it.message) }
            }
            .also { it.isDaemon = true }
            .start()
    }

    private fun loadMessages() {
        engine.loadMessages()
        val items = engine.state.messages
        allMessages.setAll(items)
        messagesList.items.setAll(items)
    }

    private fun filterMessages(query: String?) {
        if (query.isNullOrBlank()) {
            messagesList.items.setAll(allMessages)
        } else {
            val lower = query.lowercase()
            messagesList.items.setAll(
                allMessages.filter { it.author.lowercase().contains(lower) || it.content.lowercase().contains(lower) }
            )
        }
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
