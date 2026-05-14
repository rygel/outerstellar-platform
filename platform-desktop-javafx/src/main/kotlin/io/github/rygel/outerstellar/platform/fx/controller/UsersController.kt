package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class UsersController : KoinComponent {

    private val logger = LoggerFactory.getLogger(UsersController::class.java)
    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var usersTable: TableView<UserSummary>
    @FXML private lateinit var usernameColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var emailColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var roleColumn: TableColumn<UserSummary, String>
    @FXML private lateinit var enabledColumn: TableColumn<UserSummary, String>

    @FXML
    fun initialize() {
        usersTable.itemsProperty().bind(SimpleObjectProperty(viewModel.adminUsers))
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
        viewModel
            .setUserEnabled(selected.id, !selected.enabled)
            .also { task ->
                task.setOnSucceeded {
                    task.value
                        .onSuccess { loadUsers() }
                        .onFailure { logger.warn("Toggle user enabled failed: {}", it.message) }
                }
            }
            .runInBackground()
    }

    @FXML
    fun onToggleRole() {
        val selected = usersTable.selectionModel.selectedItem ?: return
        val newRole = if (selected.role == UserRole.ADMIN) UserRole.USER.name else UserRole.ADMIN.name
        viewModel
            .setUserRole(selected.id, newRole)
            .also { task ->
                task.setOnSucceeded {
                    task.value
                        .onSuccess { loadUsers() }
                        .onFailure { logger.warn("Toggle user role failed: {}", it.message) }
                }
            }
            .runInBackground()
    }

    private fun loadUsers() {
        viewModel.loadUsers().runInBackground()
    }
}
