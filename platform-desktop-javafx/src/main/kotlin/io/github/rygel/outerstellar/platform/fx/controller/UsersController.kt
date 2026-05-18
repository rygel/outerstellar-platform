package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class UsersController : KoinComponent {

    private val logger = LoggerFactory.getLogger(UsersController::class.java)
    private val viewModel: FxSyncViewModel by inject()

    fun createView(): Parent {
        val usernameColumn = TableColumn<UserSummary, String>("Username")
        val emailColumn = TableColumn<UserSummary, String>("Email")
        val roleColumn = TableColumn<UserSummary, String>("Role")
        val enabledColumn = TableColumn<UserSummary, String>("Enabled")

        val usersTable = TableView<UserSummary>()
        usersTable.columns.addAll(usernameColumn, emailColumn, roleColumn, enabledColumn)
        usersTable.itemsProperty().bind(SimpleObjectProperty(viewModel.adminUsers))

        usernameColumn.setCellValueFactory { SimpleStringProperty(it.value.username) }
        emailColumn.setCellValueFactory { SimpleStringProperty(it.value.email) }
        roleColumn.setCellValueFactory { SimpleStringProperty(it.value.role.name) }
        enabledColumn.setCellValueFactory { SimpleStringProperty(it.value.enabled.toString()) }

        val toggleEnabledBtn = Button("Toggle Enabled")
        toggleEnabledBtn.setOnAction {
            val selected = usersTable.selectionModel.selectedItem ?: return@setOnAction
            viewModel
                .setUserEnabled(selected.id, !selected.enabled)
                .also { task ->
                    task.setOnSucceeded {
                        task.value
                            .onSuccess { viewModel.loadUsers().runInBackground() }
                            .onFailure { logger.warn("Toggle user enabled failed: {}", it.message) }
                    }
                }
                .runInBackground()
        }

        val toggleRoleBtn = Button("Toggle Role")
        toggleRoleBtn.setOnAction {
            val selected = usersTable.selectionModel.selectedItem ?: return@setOnAction
            val newRole = if (selected.role == UserRole.ADMIN) UserRole.USER.name else UserRole.ADMIN.name
            viewModel
                .setUserRole(selected.id, newRole)
                .also { task ->
                    task.setOnSucceeded {
                        task.value
                            .onSuccess { viewModel.loadUsers().runInBackground() }
                            .onFailure { logger.warn("Toggle user role failed: {}", it.message) }
                    }
                }
                .runInBackground()
        }

        val buttons = HBox(10.0, toggleEnabledBtn, toggleRoleBtn)
        val root = VBox(10.0, usersTable, buttons)
        viewModel.loadUsers().runInBackground()
        return root
    }
}
