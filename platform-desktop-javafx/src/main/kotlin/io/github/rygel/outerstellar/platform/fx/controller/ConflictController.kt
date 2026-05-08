package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.service.MessageService
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ConflictController : KoinComponent {

    private val messageService: MessageService by inject()

    @FXML private lateinit var localAuthorLabel: Label
    @FXML private lateinit var localContent: TextArea

    var conflictStrategy: ConflictStrategy? = null
        private set

    private var syncId: String? = null

    fun setMessage(msg: MessageSummary) {
        syncId = msg.syncId
        localAuthorLabel.text = "Author: ${msg.author}"
        localContent.text = msg.content
    }

    @FXML
    fun onKeepMine() {
        val id = syncId ?: return
        messageService.resolveConflict(id, ConflictStrategy.MINE)
        conflictStrategy = ConflictStrategy.MINE
        close()
    }

    @FXML
    fun onAcceptServer() {
        val id = syncId ?: return
        messageService.resolveConflict(id, ConflictStrategy.SERVER)
        conflictStrategy = ConflictStrategy.SERVER
        close()
    }

    private fun close() {
        (localAuthorLabel.scene.window as? Stage)?.close()
    }
}
