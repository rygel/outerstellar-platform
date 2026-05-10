package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class NotificationsController : KoinComponent {

    private val logger = LoggerFactory.getLogger(NotificationsController::class.java)
    private val engine: DesktopSyncEngine by inject()

    @FXML private lateinit var notificationsList: ListView<NotificationSummary>

    @FXML
    fun initialize() {
        notificationsList.setCellFactory {
            object : ListCell<NotificationSummary>() {
                override fun updateItem(item: NotificationSummary?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        style = null
                    } else {
                        text = "${item.title}: ${item.body}"
                        style = if (item.read) "-fx-opacity: 0.6;" else "-fx-font-weight: bold;"
                    }
                }
            }
        }
        loadNotifications()
    }

    @FXML
    fun onMarkRead() {
        val selected = notificationsList.selectionModel.selectedItem ?: return
        Thread {
                engine.markNotificationRead(selected.id)
                Platform.runLater { loadNotifications() }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onMarkAllRead() {
        Thread {
                engine.markAllNotificationsRead()
                Platform.runLater { loadNotifications() }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onRefresh() {
        loadNotifications()
    }

    private fun loadNotifications() {
        Thread {
                engine.loadNotifications()
                Platform.runLater { notificationsList.items.setAll(engine.state.notifications) }
            }
            .also { it.isDaemon = true }
            .start()
    }
}
