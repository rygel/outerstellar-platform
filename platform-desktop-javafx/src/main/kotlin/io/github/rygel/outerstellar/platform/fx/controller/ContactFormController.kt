package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage

class ContactFormController(
    private val syncId: String?,
    private val viewModel: FxSyncViewModel,
    private val themeManager: FxThemeManager,
) {
    fun showAndWait() {
        val nameField = TextField().apply { promptText = "Name *" }
        val emailsField = TextField().apply { promptText = "Emails (comma separated)" }
        val phonesField = TextField().apply { promptText = "Phones (comma separated)" }
        val socialField = TextField().apply { promptText = "Social Media (comma separated)" }
        val companyField = TextField().apply { promptText = "Company" }
        val addressField = TextField().apply { promptText = "Address" }
        val departmentField = TextField().apply { promptText = "Department" }

        val errorLabel =
            Label().apply {
                style = "-fx-text-fill: red;"
                isVisible = false
            }

        if (syncId != null) {
            val contact = viewModel.contacts.find { it.syncId == syncId }
            if (contact != null) {
                nameField.text = contact.name
                emailsField.text = contact.emails.joinToString(", ")
                phonesField.text = contact.phones.joinToString(", ")
                socialField.text = contact.socialMedia.joinToString(", ")
                companyField.text = contact.company
                addressField.text = contact.companyAddress
                departmentField.text = contact.department
            }
        }

        val dialog =
            Stage().apply {
                initModality(Modality.APPLICATION_MODAL)
                title = if (syncId != null) "Edit Contact" else "Create Contact"
            }

        val saveBtn =
            Button("Save").apply {
                setOnAction {
                    val name = nameField.text.trim()
                    if (name.isBlank()) {
                        errorLabel.text = "Name is required"
                        errorLabel.isVisible = true
                        return@setOnAction
                    }
                    errorLabel.isVisible = false

                    val emails = emailsField.text.trim().split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val phones = phonesField.text.trim().split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val social = socialField.text.trim().split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val company = companyField.text.trim()
                    val address = addressField.text.trim()
                    val department = departmentField.text.trim()

                    val task =
                        if (syncId != null) {
                            viewModel.updateContact(syncId, name, emails, phones, social, company, address, department)
                        } else {
                            viewModel.createContact(name, emails, phones, social, company, address, department)
                        }

                    task.setOnSucceeded {
                        task.value
                            .onSuccess { dialog.close() }
                            .onFailure {
                                errorLabel.text = it.message ?: "Save failed"
                                errorLabel.isVisible = true
                            }
                    }
                    task.setOnFailed { event ->
                        errorLabel.text = event.source.exception.message ?: "Save failed"
                        errorLabel.isVisible = true
                    }
                    task.runInBackground()
                }
            }

        val cancelBtn = Button("Cancel").apply { setOnAction { dialog.close() } }

        val form =
            VBox(8.0).apply {
                padding = Insets(12.0)
                children.addAll(
                    nameField,
                    emailsField,
                    phonesField,
                    socialField,
                    companyField,
                    addressField,
                    departmentField,
                    errorLabel,
                    HBox(8.0).apply {
                        children.addAll(saveBtn, cancelBtn)
                        alignment = Pos.CENTER_RIGHT
                    },
                )
            }

        dialog.scene = Scene(form).also { themeManager.setScene(it) }
        dialog.showAndWait()
    }
}
