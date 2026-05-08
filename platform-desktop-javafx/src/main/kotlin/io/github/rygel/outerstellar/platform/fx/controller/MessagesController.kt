package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncService
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

class MessagesController : KoinComponent {

    private val messageService: MessageService by inject()
    private val syncService: SyncService by inject()
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
                                "${item.author}: ${item.content.take(80)}${if (item.content.length > 80) "..." else ""}"
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
        try {
            messageService.createLocalMessage(author, content)
            authorField.clear()
            contentArea.clear()
            loadMessages()
        } catch (_: Exception) {}
    }

    @FXML
    fun onSync() {
        Thread {
                try {
                    syncService.sync()
                    Platform.runLater { loadMessages() }
                } catch (_: Exception) {}
            }
            .also { it.isDaemon = true }
            .start()
    }

    private fun loadMessages() {
        try {
            val result = messageService.listMessages()
            allMessages.setAll(result.items)
            messagesList.items.setAll(result.items)
        } catch (_: Exception) {}
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
