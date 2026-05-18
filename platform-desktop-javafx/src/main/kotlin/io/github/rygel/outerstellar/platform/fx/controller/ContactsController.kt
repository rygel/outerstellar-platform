package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.ContactSummary
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContactsController : KoinComponent {

    private val viewModel: FxSyncViewModel by inject()
    private val themeManager: FxThemeManager by inject()

    fun createView(): Parent {
        val header =
            HBox(12.0).apply {
                padding = Insets(10.0)
                alignment = Pos.CENTER_LEFT

                val title = Label("Contacts").apply { style = "-fx-font-size: 18px; -fx-font-weight: bold;" }

                val createBtn = Button("Create Contact").apply { setOnAction { showContactFormDialog(null) } }

                children.addAll(title, createBtn)
            }

        val nameCol = TableColumn<ContactSummary, String>("Name")
        nameCol.setCellValueFactory { SimpleStringProperty(it.value.name) }

        val emailCol = TableColumn<ContactSummary, String>("Emails")
        emailCol.setCellValueFactory { SimpleStringProperty(it.value.emails.joinToString(", ")) }

        val phoneCol = TableColumn<ContactSummary, String>("Phones")
        phoneCol.setCellValueFactory { SimpleStringProperty(it.value.phones.joinToString(", ")) }

        val companyCol = TableColumn<ContactSummary, String>("Company")
        companyCol.setCellValueFactory { SimpleStringProperty(it.value.company) }

        val deptCol = TableColumn<ContactSummary, String>("Department")
        deptCol.setCellValueFactory { SimpleStringProperty(it.value.department) }

        val table =
            TableView<ContactSummary>().apply {
                itemsProperty().bind(SimpleObjectProperty(viewModel.contacts))
                columns.addAll(nameCol, emailCol, phoneCol, companyCol, deptCol)
                setOnMouseClicked { event ->
                    if (event.clickCount == 2) {
                        val selected = selectionModel.selectedItem
                        if (selected != null) {
                            showContactFormDialog(selected.syncId)
                        }
                    }
                }
                BorderPane.setMargin(this, Insets(0.0, 10.0, 10.0, 10.0))
            }

        viewModel.loadContacts().runInBackground()

        return BorderPane().apply {
            top = header
            center = table
        }
    }

    private fun showContactFormDialog(syncId: String?) {
        val controller = ContactFormController(syncId, viewModel, themeManager)
        controller.showAndWait()
        viewModel.loadContacts().runInBackground()
    }
}
