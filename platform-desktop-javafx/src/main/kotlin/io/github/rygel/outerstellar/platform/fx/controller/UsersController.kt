package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
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
    private val engine: DesktopSyncEngine by inject()

    @FXML private lateinit var usersTable: TableView<UserSummary>
    @FXML private lateinit var usernameColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var emailColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var roleColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var enabledColumn: TableColumn<UserSummary, String>

    @FXML
    fun initialize() {
        usernameColumn.setCellValueFactory { SimpleStringProperty(it.value.username) }
        emailColumn.setCellValueFactory { SimpleStringProperty(it.value.email) }
        roleColumn.setCellValueFactory { SimpleStringProperty(it.value.role.name) }
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
                engine
                    .setUserEnabled(selected.id, !selected.enabled)
                    .onSuccess { Platform.runLater { loadUsers() } }
                    .onFailure { logger.warn("Toggle user enabled failed: {}", it.message) }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onToggleRole() {
        val selected = usersTable.selectionModel.selectedItem ?: return
        val newRole = if (selected.role == UserRole.ADMIN) UserRole.USER.name else UserRole.ADMIN.name
        Thread {
                engine
                    .setUserRole(selected.id, newRole)
                    .onSuccess { Platform.runLater { loadUsers() } }
                    .onFailure { logger.warn("Toggle user role failed: {}", it.message) }
            }
            .also { it.isDaemon = true }
            .start()
    }

    private fun loadUsers() {
        Thread {
                engine.loadUsers()
                Platform.runLater { usersTable.items.setAll(engine.state.adminUsers) }
            }
            .also { it.isDaemon = true }
            .start()
    }
}
