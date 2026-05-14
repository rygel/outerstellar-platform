package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.MessageSummary
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ConflictController : KoinComponent {

    private val viewModel: FxSyncViewModel by inject()

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
        viewModel
            .resolveConflict(id, ConflictStrategy.MINE)
            .also { task ->
                task.setOnSucceeded {
                    conflictStrategy = ConflictStrategy.MINE
                    close()
                }
            }
            .runInBackground()
    }

    @FXML
    fun onAcceptServer() {
        val id = syncId ?: return
        viewModel
            .resolveConflict(id, ConflictStrategy.SERVER)
            .also { task ->
                task.setOnSucceeded {
                    conflictStrategy = ConflictStrategy.SERVER
                    close()
                }
            }
            .runInBackground()
    }

    private fun close() {
        (localAuthorLabel.scene.window as? Stage)?.close()
    }
}
