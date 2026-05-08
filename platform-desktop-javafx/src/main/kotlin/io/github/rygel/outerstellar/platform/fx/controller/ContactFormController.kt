package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.service.ContactService
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class ContactFormController : KoinComponent {

    private val logger = LoggerFactory.getLogger(ContactFormController::class.java)
    private val contactService: ContactService by inject()

    @FXML private lateinit var nameField: TextField
    @FXML private lateinit var companyField: TextField
    @FXML private lateinit var departmentField: TextField
    @FXML private lateinit var addressField: TextField
    @FXML private lateinit var emailList: ListView<String>
    @FXML private lateinit var phoneList: ListView<String>
    @FXML private lateinit var socialList: ListView<String>

    var saved: Boolean = false
        private set

    private var syncId: String? = null

    fun setSyncId(id: String?) {
        syncId = id
        if (id != null) {
            loadContact(id)
        }
    }

    @FXML
    fun initialize() {
        emailList.items = FXCollections.observableArrayList()
        phoneList.items = FXCollections.observableArrayList()
        socialList.items = FXCollections.observableArrayList()
    }

    @FXML
    fun onAddEmail() {
        val input = showInputDialog("Add Email")
        if (input != null && input.isNotBlank()) {
            emailList.items.add(input.trim())
        }
    }

    @FXML
    fun onRemoveEmail() {
        val selected = emailList.selectionModel.selectedIndex
        if (selected >= 0) emailList.items.removeAt(selected)
    }

    @FXML
    fun onAddPhone() {
        val input = showInputDialog("Add Phone")
        if (input != null && input.isNotBlank()) {
            phoneList.items.add(input.trim())
        }
    }

    @FXML
    fun onRemovePhone() {
        val selected = phoneList.selectionModel.selectedIndex
        if (selected >= 0) phoneList.items.removeAt(selected)
    }

    @FXML
    fun onAddSocial() {
        val input = showInputDialog("Add Social Media")
        if (input != null && input.isNotBlank()) {
            socialList.items.add(input.trim())
        }
    }

    @FXML
    fun onRemoveSocial() {
        val selected = socialList.selectionModel.selectedIndex
        if (selected >= 0) socialList.items.removeAt(selected)
    }

    @FXML
    fun onSave() {
        val name = nameField.text.trim()
        if (name.isBlank()) return
        val emails = emailList.items.toList()
        val phones = phoneList.items.toList()
        val socials = socialList.items.toList()
        val company = companyField.text.trim()
        val address = addressField.text.trim()
        val department = departmentField.text.trim()

        try {
            val id = syncId
            if (id != null) {
                val existing = contactService.getContactBySyncId(id)
                if (existing != null) {
                    contactService.updateContact(
                        existing.copy(
                            name = name,
                            emails = emails,
                            phones = phones,
                            socialMedia = socials,
                            company = company,
                            companyAddress = address,
                            department = department,
                        )
                    )
                }
            } else {
                contactService.createContact(name, emails, phones, socials, company, address, department)
            }
            saved = true
            close()
        } catch (e: Exception) {
            logger.warn("Save contact failed: {}", e.message)
        }
    }

    @FXML
    fun onCancel() {
        close()
    }

    private fun loadContact(id: String) {
        Thread {
                try {
                    val contact = contactService.getContactBySyncId(id) ?: return@Thread
                    Platform.runLater {
                        nameField.text = contact.name
                        companyField.text = contact.company
                        departmentField.text = contact.department
                        addressField.text = contact.companyAddress
                        emailList.items.setAll(contact.emails)
                        phoneList.items.setAll(contact.phones)
                        socialList.items.setAll(contact.socialMedia)
                    }
                } catch (e: Exception) {
                    logger.warn("Load contact failed: {}", e.message)
                }
            }
            .also { it.isDaemon = true }
            .start()
    }

    private fun showInputDialog(title: String): String? {
        val dialog = javafx.scene.control.TextInputDialog()
        dialog.title = title
        dialog.headerText = null
        (nameField.scene.window as? Stage)?.let { dialog.initOwner(it) }
        val result = dialog.showAndWait()
        return result.orElse(null)
    }

    private fun close() {
        (nameField.scene.window as? Stage)?.close()
    }
}
