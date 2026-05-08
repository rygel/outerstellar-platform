package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.service.ContactService
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.stage.Modality
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContactsController : KoinComponent {

    private val contactService: ContactService by inject()
    private val themeManager: FxThemeManager by inject()

    @FXML private lateinit var contactsTable: TableView<ContactSummary>
    @FXML private lateinit var nameColumn: TableColumn<ContactSummary, String>
    @FXML private lateinit var emailColumn: TableColumn<ContactSummary, String>
    @FXML private lateinit var companyColumn: TableColumn<ContactSummary, String>

    @FXML
    fun initialize() {
        nameColumn.setCellValueFactory { SimpleStringProperty(it.value.name) }
        emailColumn.setCellValueFactory { SimpleStringProperty(it.value.emails.joinToString(", ")) }
        companyColumn.setCellValueFactory { SimpleStringProperty(it.value.company) }
        contactsTable.setOnMouseClicked { event ->
            if (event.clickCount == 2) {
                val selected = contactsTable.selectionModel.selectedItem
                if (selected != null) {
                    showContactFormDialog(selected.syncId)
                }
            }
        }
        loadContacts()
    }

    @FXML
    fun onCreateContact() {
        showContactFormDialog(null)
    }

    private fun loadContacts() {
        try {
            val contacts = contactService.listContacts()
            contactsTable.items.setAll(contacts)
        } catch (_: Exception) {}
    }

    private fun showContactFormDialog(syncId: String?) {
        val loader = FXMLLoader(javaClass.getResource("/fxml/ContactFormDialog.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        val controller = loader.getController<ContactFormController>()
        controller.setSyncId(syncId)
        val stage = Stage()
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.title = if (syncId != null) "Edit Contact" else "Create Contact"
        val scene = Scene(root)
        themeManager.setScene(scene)
        stage.scene = scene
        stage.showAndWait()
        loadContacts()
    }
}
