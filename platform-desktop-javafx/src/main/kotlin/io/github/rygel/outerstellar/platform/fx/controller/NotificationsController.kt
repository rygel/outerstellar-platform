package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class NotificationsController : KoinComponent {

    private val logger = LoggerFactory.getLogger(NotificationsController::class.java)
    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var notificationsList: ListView<NotificationSummary>

    @FXML
    fun initialize() {
        notificationsList.itemsProperty().bind(SimpleObjectProperty(viewModel.notifications))
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
        viewModel
            .markNotificationRead(selected.id)
            .also { task -> task.setOnSucceeded { loadNotifications() } }
            .runInBackground()
    }

    @FXML
    fun onMarkAllRead() {
        viewModel
            .markAllNotificationsRead()
            .also { task -> task.setOnSucceeded { loadNotifications() } }
            .runInBackground()
    }

    @FXML
    fun onRefresh() {
        loadNotifications()
    }

    private fun loadNotifications() {
        viewModel.loadNotifications().runInBackground()
    }
}
