package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class NotificationsController : KoinComponent {

    private val logger = LoggerFactory.getLogger(NotificationsController::class.java)
    private val viewModel: FxSyncViewModel by inject()

    fun createView(): Parent {
        val titleLabel = Label("Notifications").also { it.font = Font.font(null, FontWeight.BOLD, 18.0) }

        val markAllReadBtn =
            Button("Mark All Read").also { it.setOnAction { viewModel.markAllNotificationsRead().runInBackground() } }

        val header =
            HBox(10.0, titleLabel, markAllReadBtn).also {
                it.alignment = Pos.CENTER_LEFT
                it.padding = Insets(10.0)
            }

        val notificationsList =
            ListView<NotificationSummary>().also {
                VBox.setVgrow(it, Priority.ALWAYS)
                it.itemsProperty().set(viewModel.notifications)
                it.setCellFactory {
                    object : ListCell<NotificationSummary>() {
                        override fun updateItem(item: NotificationSummary?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (empty || item == null) {
                                text = null
                                graphic = null
                            } else {
                                val dot = if (item.read) "" else "\u25CF "
                                val titlePart =
                                    Label("$dot${item.title}").also { lbl ->
                                        if (!item.read) {
                                            lbl.font = Font.font(null, FontWeight.BOLD, lbl.font.size)
                                        }
                                    }
                                val bodyPart = Label(item.body).also { it.font = Font.font(it.font.size - 2.0) }
                                val cellContent = VBox(2.0, titlePart, bodyPart)
                                graphic = cellContent
                                text = null
                            }
                        }
                    }
                }
            }

        val markReadBtn =
            Button("Mark Read").also {
                it.setOnAction {
                    val selected = notificationsList.selectionModel.selectedItem ?: return@setOnAction
                    viewModel.markNotificationRead(selected.id).runInBackground()
                }
            }

        val bottom =
            HBox(markReadBtn).also {
                it.padding = Insets(10.0)
                it.alignment = Pos.CENTER_RIGHT
            }

        val root =
            BorderPane().also {
                it.top = header
                it.center = notificationsList
                it.bottom = bottom
            }

        viewModel.loadNotifications().runInBackground()
        return root
    }
}
