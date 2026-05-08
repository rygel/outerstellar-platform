package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.sync.SyncService
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class NotificationsController : KoinComponent {

    private val logger = LoggerFactory.getLogger(NotificationsController::class.java)
    private val syncService: SyncService by inject()

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
                try {
                    syncService.markNotificationRead(selected.id)
                    Platform.runLater { loadNotifications() }
                } catch (e: Exception) {
                    logger.warn("Mark notification read failed: {}", e.message)
                }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onMarkAllRead() {
        Thread {
                try {
                    syncService.markAllNotificationsRead()
                    Platform.runLater { loadNotifications() }
                } catch (e: Exception) {
                    logger.warn("Mark all notifications read failed: {}", e.message)
                }
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
                try {
                    val notifications = syncService.listNotifications()
                    Platform.runLater { notificationsList.items.setAll(notifications) }
                } catch (e: Exception) {
                    logger.warn("Load notifications failed: {}", e.message)
                }
            }
            .also { it.isDaemon = true }
            .start()
    }
}
