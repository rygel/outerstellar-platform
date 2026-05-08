package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.sync.SyncService
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class UsersController : KoinComponent {

    private val logger = LoggerFactory.getLogger(UsersController::class.java)
    private val syncService: SyncService by inject()

    @FXML private lateinit var usersTable: TableView<UserSummary>
    @FXML private lateinit var usernameColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var emailColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var roleColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var enabledColumn: TableColumn<UserSummary, String>

    @FXML
    fun initialize() {
        usernameColumn.setCellValueFactory { SimpleStringProperty(it.value.username) }
        emailColumn.setCellValueFactory { SimpleStringProperty(it.value.email) }
        roleColumn.setCellValueFactory { SimpleStringProperty(it.value.role) }
        enabledColumn.setCellValueFactory { SimpleStringProperty(it.value.enabled.toString()) }
        loadUsers()
    }

    @FXML
    fun onRefresh() {
        loadUsers()
    }

    @FXML
    fun onToggleEnabled() {
        val selected = usersTable.selectionModel.selectedItem ?: return
        Thread {
                try {
                    syncService.setUserEnabled(selected.id, !selected.enabled)
                    Platform.runLater { loadUsers() }
                } catch (e: Exception) {
                    logger.warn("Toggle user enabled failed: {}", e.message)
                }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onToggleRole() {
        val selected = usersTable.selectionModel.selectedItem ?: return
        val newRole = if (selected.role == "ADMIN") "USER" else "ADMIN"
        Thread {
                try {
                    syncService.setUserRole(selected.id, newRole)
                    Platform.runLater { loadUsers() }
                } catch (e: Exception) {
                    logger.warn("Toggle user role failed: {}", e.message)
                }
            }
            .also { it.isDaemon = true }
            .start()
    }

    private fun loadUsers() {
        Thread {
                try {
                    val users = syncService.listUsers()
                    Platform.runLater { usersTable.items.setAll(users) }
                } catch (e: Exception) {
                    logger.warn("Load users failed: {}", e.message)
                }
            }
            .also { it.isDaemon = true }
            .start()
    }
}
