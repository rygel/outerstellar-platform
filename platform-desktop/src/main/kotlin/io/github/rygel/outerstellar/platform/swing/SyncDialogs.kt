package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.TitledBorder
import net.miginfocom.swing.MigLayout

@Suppress("TooManyFunctions")
internal class SyncDialogs(
    private val frame: JFrame,
    private val viewModel: SyncViewModel,
    private val themeManager: ThemeManager,
    private var i18nService: I18nService,
    private val appVersion: String,
    private val updateUrl: String,
    private val onRefreshTranslations: (I18nService) -> Unit,
    private val onSaveState: () -> Unit,
) {
    fun updateI18n(newI18n: I18nService) {
        i18nService = newI18n
    }

    fun showLoginDialog() {
        val dialog = createThemedDialog(i18nService.translate("swing.auth.dialog.title"), "[][grow]", "[][][]")

        dialog.add(JLabel(i18nService.translate("swing.auth.username")))
        val userField = JTextField().apply { name = "username" }
        dialog.add(userField, "growx, wrap")

        dialog.add(JLabel(i18nService.translate("swing.auth.password")))
        val passField = JPasswordField().apply { name = "password" }
        dialog.add(passField, "growx, wrap")

        val loginBtn = JButton(i18nService.translate("swing.auth.signin")).apply { name = "loginBtn" }
        loginBtn.addActionListener {
            viewModel.login(userField.text, String(passField.password)) { success, error ->
                if (success) {
                    dialog.dispose()
                } else {
                    JOptionPane.showMessageDialog(
                        dialog,
                        error,
                        i18nService.translate("swing.auth.failed.title"),
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            }
        }

        dialog.add(createActionRow(loginBtn), "span, growx")
        showDialog(dialog)
    }

    fun showConflictDialog(msg: MessageSummary) {
        val dialog = JDialog(frame, "Resolve Sync Conflict", true)
        dialog.layout = MigLayout("fill, ins 20, gap 10", "[grow][grow]", "[][grow][]")

        val introText =
            "<html>A sync conflict was detected for this message.<br/>Please choose which version to keep:</html>"
        dialog.add(JLabel(introText), "span, wrap, gapbottom 15")

        val localPanel = JPanel(MigLayout("fillx, ins 10", "[grow]", "[][]"))
        localPanel.border = BorderFactory.createTitledBorder("My Local Version")
        localPanel.add(JLabel("Author: ${msg.author}"), "wrap")
        localPanel.add(
            JScrollPane(
                JTextArea(msg.content).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                }
            ),
            "grow, h 100!",
        )

        val serverPanel = JPanel(MigLayout("fillx, ins 10", "[grow]", "[][]"))
        serverPanel.border = BorderFactory.createTitledBorder("Server Version")
        serverPanel.add(JLabel("Server has a newer version."), "wrap")
        serverPanel.add(JLabel("Accepting it will overwrite your local changes."), "grow, wrap")

        dialog.add(localPanel, "grow")
        dialog.add(serverPanel, "grow, wrap")

        val mineBtn = JButton("Keep My Version")
        val serverBtn = JButton("Accept Server Version")

        mineBtn.addActionListener {
            viewModel.resolveConflict(msg.syncId, ConflictStrategy.MINE)
            dialog.dispose()
        }
        serverBtn.addActionListener {
            viewModel.resolveConflict(msg.syncId, ConflictStrategy.SERVER)
            dialog.dispose()
        }

        dialog.add(mineBtn, "split 2, span, center")
        dialog.add(serverBtn)

        dialog.pack()
        dialog.setSize(DIALOG_WIDTH, DIALOG_HEIGHT)
        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    fun showContactFormDialog(syncId: String?) {
        val isEditing = syncId != null
        val title = if (isEditing) "Edit Contact" else "Create Contact"
        val dialog = createThemedDialog(title, "[][grow]", "[][][][][][][][][]")
        dialog.setSize(550, 500)

        val contact = if (isEditing) viewModel.contacts.find { it.syncId == syncId } else null

        val nameField = JTextField(contact?.name ?: "")
        val emails = contact?.emails?.toMutableList() ?: mutableListOf<String>()
        val phones = contact?.phones?.toMutableList() ?: mutableListOf<String>()
        val socials = contact?.socialMedia?.toMutableList() ?: mutableListOf<String>()
        val companyField = JTextField(contact?.company ?: "")
        val departmentField = JTextField(contact?.department ?: "")
        val addressField = JTextField(contact?.companyAddress ?: "")

        dialog.add(JLabel("Name:"), "right")
        dialog.add(nameField, "growx, wrap")

        dialog.add(JLabel("Emails:"), "right")
        dialog.add(createListManageButton("Emails", emails), "growx, wrap")

        dialog.add(JLabel("Phones:"), "right")
        dialog.add(createListManageButton("Phones", phones), "growx, wrap")

        dialog.add(JLabel("Social Media:"), "right")
        dialog.add(createListManageButton("Socials", socials), "growx, wrap")

        dialog.add(JLabel("Company:"), "right")
        dialog.add(companyField, "growx, wrap")
        dialog.add(JLabel("Department:"), "right")
        dialog.add(departmentField, "growx, wrap")
        dialog.add(JLabel("Address:"), "right")
        dialog.add(addressField, "growx, wrap")

        val saveBtn = JButton(if (isEditing) "Update Contact" else "Save Contact")
        val cancelBtn = JButton(i18nService.translate("swing.settings.button.cancel"))

        saveBtn.addActionListener {
            if (nameField.text.isBlank()) {
                JOptionPane.showMessageDialog(
                    dialog,
                    "Name is required",
                    i18nService.translate("swing.error.title"),
                    JOptionPane.ERROR_MESSAGE,
                )
                return@addActionListener
            }

            val onValidationError: (String) -> Unit = { error ->
                JOptionPane.showMessageDialog(
                    dialog,
                    error,
                    i18nService.translate("swing.error.title"),
                    JOptionPane.ERROR_MESSAGE,
                )
            }

            if (syncId != null) {
                viewModel.updateContact(
                    syncId = syncId,
                    name = nameField.text,
                    emails = emails,
                    phones = phones,
                    socialMedia = socials,
                    company = companyField.text,
                    companyAddress = addressField.text,
                    department = departmentField.text,
                    onValidationError = onValidationError,
                )
            } else {
                viewModel.createContact(
                    name = nameField.text,
                    emails = emails,
                    phones = phones,
                    socialMedia = socials,
                    company = companyField.text,
                    companyAddress = addressField.text,
                    department = departmentField.text,
                    onValidationError = onValidationError,
                )
            }
            dialog.dispose()
        }

        cancelBtn.addActionListener { dialog.dispose() }

        dialog.add(createActionRow(cancelBtn, saveBtn), "span, growx, gaptop 20")

        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    fun showListEditDialog(title: String, list: MutableList<String>) {
        val dialog = JDialog(frame, "Manage $title", true)
        dialog.layout = MigLayout("fill, ins 20, gap 10", "[grow][]", "[][grow][]")

        val listModel = DefaultListModel<String>()
        list.forEach { listModel.addElement(it) }
        val jList = javax.swing.JList(listModel)

        val inputField = JTextField()
        val addLabel = i18nService.translate("swing.button.add")
        val updateLabel = i18nService.translate("swing.button.update")
        val addBtn = JButton(addLabel)

        fun updateItem() {
            val index = jList.selectedIndex
            if (index >= 0 && inputField.text.isNotBlank()) {
                val newValue = inputField.text.trim()
                list[index] = newValue
                listModel.set(index, newValue)
                inputField.text = ""
                jList.clearSelection()
                addBtn.text = addLabel
            }
        }

        addBtn.addActionListener {
            if (addBtn.text == updateLabel) {
                updateItem()
            } else if (inputField.text.isNotBlank()) {
                val valToAdd = inputField.text.trim()
                list.add(valToAdd)
                listModel.addElement(valToAdd)
                inputField.text = ""
            }
        }

        val editBtn = JButton("Edit Selected")
        editBtn.addActionListener {
            val index = jList.selectedIndex
            if (index >= 0) {
                inputField.text = listModel.getElementAt(index)
                addBtn.text = updateLabel
                inputField.requestFocusInWindow()
            }
        }

        val removeBtn = JButton("Remove Selected")
        removeBtn.addActionListener {
            val index = jList.selectedIndex
            if (index >= 0) {
                list.removeAt(index)
                listModel.remove(index)
                inputField.text = ""
                addBtn.text = addLabel
            }
        }

        val topPanel = JPanel(MigLayout("ins 0, fillx", "[grow][]", "[]"))
        topPanel.isOpaque = false
        topPanel.add(inputField, "growx")
        topPanel.add(addBtn, "w 80!")

        dialog.add(JLabel("Add or edit $title:"), "span, wrap")
        dialog.add(topPanel, "span, growx, wrap")
        dialog.add(JScrollPane(jList), "grow")

        val sideBtnPanel = JPanel(MigLayout("ins 0", "[]", "[]10[]"))
        sideBtnPanel.isOpaque = false
        sideBtnPanel.add(editBtn, "growx, wrap")
        sideBtnPanel.add(removeBtn, "growx")
        dialog.add(sideBtnPanel, "top")

        val okBtn = JButton("Done")
        okBtn.addActionListener { dialog.dispose() }

        dialog.add(okBtn, "span, right, gaptop 10")

        dialog.setSize(450, 400)
        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    fun showSettingsDialog() {
        val dialog = createThemedDialog(i18nService.translate("swing.settings.title"), "[][grow]", "[][][]")
        dialog.name = "settingsDialog"

        dialog.add(JLabel(i18nService.translate("swing.settings.language")))
        val languages =
            arrayOf(
                "en" to i18nService.translate("swing.language.en"),
                "fr" to i18nService.translate("swing.language.fr"),
            )
        val langCombo = JComboBox<String>(languages.map { it.second }.toTypedArray()).apply { name = "langCombo" }
        langCombo.selectedIndex = languages.indexOfFirst { it.first == Locale.getDefault().language }.coerceAtLeast(0)
        dialog.add(langCombo, "growx, wrap")

        dialog.add(JLabel(i18nService.translate("swing.settings.theme")))
        val allThemes = DesktopTheme.entries.sortedBy { it.label }
        val themeCombo = JComboBox<String>(allThemes.map { it.label }.toTypedArray()).apply { name = "themeCombo" }
        val currentThemeName = UIManager.get("current_theme_name") as? String
        themeCombo.selectedIndex = allThemes.indexOfFirst { it.label == currentThemeName }.coerceAtLeast(0)
        dialog.add(themeCombo, "growx, wrap")

        val previewPanel =
            JPanel(MigLayout("fillx, ins 15, gap 10", "[grow]", "[][][]")).apply { name = "previewPanel" }
        val sampleLabel = JLabel("Sample Label Text")
        val sampleField = JTextField("Sample Input Text")
        val sampleButton = JButton("Sample Button")
        previewPanel.add(sampleLabel, "wrap")
        previewPanel.add(sampleField, "growx, wrap")
        previewPanel.add(sampleButton, "center")
        dialog.add(previewPanel, "span, growx, wrap, gaptop 15")

        fun updatePreview(theme: DesktopTheme) {
            theme.lafSetup()
            val bg = UIManager.getColor("Panel.background") ?: Color.WHITE
            val fg = UIManager.getColor("Label.foreground") ?: Color.BLACK
            val compBg = UIManager.getColor("TextField.background") ?: Color.WHITE

            previewPanel.background = bg
            previewPanel.border =
                BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") ?: Color.GRAY),
                    "Theme Preview: ${theme.label}",
                    TitledBorder.DEFAULT_JUSTIFICATION,
                    TitledBorder.DEFAULT_POSITION,
                    null,
                    fg,
                )
            sampleLabel.foreground = fg
            sampleField.background = compBg
            sampleField.foreground = fg
            sampleButton.foreground = fg

            val savedTheme =
                DesktopTheme.entries.firstOrNull { it.label == UIManager.get("current_theme_name") as? String }
            if (savedTheme != null) savedTheme.lafSetup()
        }

        themeCombo.addActionListener { updatePreview(allThemes[themeCombo.selectedIndex]) }
        updatePreview(allThemes[themeCombo.selectedIndex])

        val applyButton = JButton(i18nService.translate("swing.settings.button.apply")).apply { name = "applyButton" }
        val cancelButton =
            JButton(i18nService.translate("swing.settings.button.cancel")).apply { name = "cancelButton" }

        applyButton.addActionListener {
            val selectedTheme = allThemes[themeCombo.selectedIndex]
            themeManager.applyTheme(selectedTheme)

            val selectedLang = languages[langCombo.selectedIndex].first
            val newLocale = Locale.of(selectedLang)
            Locale.setDefault(newLocale)
            onRefreshTranslations(I18nService.create("messages").also { it.setLocale(newLocale) })
            onSaveState()
            frame.revalidate()
            frame.repaint()
            dialog.dispose()
        }
        cancelButton.addActionListener { dialog.dispose() }

        dialog.add(createActionRow(cancelButton, applyButton), "span, growx")
        showDialog(dialog)
    }

    fun showRegisterDialog() {
        val dialog =
            createThemedDialog(i18nService.translate("swing.auth.register.dialog.title"), "[][grow]", "[][][][]")

        dialog.add(JLabel(i18nService.translate("swing.auth.username")))
        val userField = JTextField().apply { name = "registerUsername" }
        dialog.add(userField, "growx, wrap")

        dialog.add(JLabel(i18nService.translate("swing.auth.password")))
        val passField = JPasswordField().apply { name = "registerPassword" }
        dialog.add(passField, "growx, wrap")

        dialog.add(JLabel(i18nService.translate("swing.auth.password.confirm")))
        val confirmField = JPasswordField().apply { name = "registerPasswordConfirm" }
        dialog.add(confirmField, "growx, wrap")

        val registerBtn = JButton(i18nService.translate("swing.auth.register.submit")).apply { name = "registerBtn" }
        registerBtn.addActionListener {
            val password = String(passField.password)
            val confirmPassword = String(confirmField.password)
            if (password != confirmPassword) {
                JOptionPane.showMessageDialog(
                    dialog,
                    i18nService.translate("swing.auth.password.mismatch"),
                    i18nService.translate("swing.auth.register.failed.title"),
                    JOptionPane.ERROR_MESSAGE,
                )
                return@addActionListener
            }
            viewModel.register(userField.text, password) { success, error ->
                if (success) {
                    dialog.dispose()
                } else {
                    JOptionPane.showMessageDialog(
                        dialog,
                        error,
                        i18nService.translate("swing.auth.register.failed.title"),
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            }
        }

        dialog.add(createActionRow(registerBtn), "span, growx")
        showDialog(dialog)
    }

    fun showChangePasswordDialog() {
        val dialog = createThemedDialog(i18nService.translate("swing.password.dialog.title"), "[][grow]", "[][][][]")

        dialog.add(JLabel(i18nService.translate("swing.password.current")))
        val currentField = JPasswordField().apply { name = "currentPassword" }
        dialog.add(currentField, "growx, wrap")

        dialog.add(JLabel(i18nService.translate("swing.password.new")))
        val newField = JPasswordField().apply { name = "newPassword" }
        dialog.add(newField, "growx, wrap")

        dialog.add(JLabel(i18nService.translate("swing.password.confirm")))
        val confirmField = JPasswordField().apply { name = "confirmPassword" }
        dialog.add(confirmField, "growx, wrap")

        val changeBtn = JButton(i18nService.translate("swing.password.submit")).apply { name = "changePasswordBtn" }
        changeBtn.addActionListener {
            val newPassword = String(newField.password)
            val confirmPassword = String(confirmField.password)
            if (newPassword != confirmPassword) {
                JOptionPane.showMessageDialog(
                    dialog,
                    i18nService.translate("swing.auth.password.mismatch"),
                    i18nService.translate("swing.password.error.title"),
                    JOptionPane.ERROR_MESSAGE,
                )
                return@addActionListener
            }
            viewModel.changePassword(String(currentField.password), newPassword) { success, error ->
                if (success) {
                    JOptionPane.showMessageDialog(
                        dialog,
                        i18nService.translate("swing.password.success"),
                        i18nService.translate("swing.password.dialog.title"),
                        JOptionPane.INFORMATION_MESSAGE,
                    )
                    dialog.dispose()
                } else {
                    JOptionPane.showMessageDialog(
                        dialog,
                        error,
                        i18nService.translate("swing.password.error.title"),
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            }
        }

        dialog.add(createActionRow(changeBtn), "span, growx")
        showDialog(dialog)
    }

    fun clearComposer(authorField: JTextField, contentArea: JTextArea) {
        authorField.text = i18nService.translate("swing.author.default")
        contentArea.text = ""
    }

    fun showMenuPlaceholder(key: String) {
        JOptionPane.showMessageDialog(
            frame,
            i18nService.translate("swing.menu.placeholder", i18nService.translate(key)),
            i18nService.translate("swing.menu.placeholder.title"),
            JOptionPane.INFORMATION_MESSAGE,
        )
    }

    fun showHelpDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.view"),
            message = i18nService.translate("swing.help.message"),
            icon = RemixIcon.get("system/question-line", 24),
        )
    }

    fun showAboutDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.about", i18nService.translate("swing.app.name")),
            message = i18nService.translate("swing.about.message", i18nService.translate("swing.app.name"), appVersion),
            icon = RemixIcon.get("system/information-line", 24),
        )
    }

    fun showFeedbackDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.feedback"),
            message = i18nService.translate("swing.feedback.message"),
            icon = RemixIcon.get("communication/chat-smile-3-line", 24),
        )
    }

    fun showUpdateCheckDialog() {
        if (updateUrl.isBlank()) {
            showInfoDialog(
                title = i18nService.translate("swing.menu.help.updates"),
                message = i18nService.translate("swing.updates.message", appVersion),
                icon = RemixIcon.get("system/refresh-line", 24),
            )
            return
        }

        UpdateService(
                currentVersion = appVersion,
                updateUrl = updateUrl,
                onUpdateAvailable = { latestVersion ->
                    SwingUtilities.invokeLater {
                        showInfoDialog(
                            title = i18nService.translate("swing.updates.available.title"),
                            message =
                                i18nService.translate("swing.updates.available.message", latestVersion, appVersion),
                            icon = RemixIcon.get("system/refresh-line", 24),
                        )
                    }
                },
            )
            .also { svc ->
                showInfoDialog(
                    title = i18nService.translate("swing.menu.help.updates"),
                    message = i18nService.translate("swing.updates.checking"),
                    icon = RemixIcon.get("system/refresh-line", 24),
                )
                svc.checkForUpdates()
            }
    }

    fun showInfoDialog(title: String, message: String, icon: javax.swing.Icon?) {
        val dialog = buildInfoDialog(title, message, icon)
        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    fun buildInfoDialog(title: String, message: String, icon: javax.swing.Icon?): JDialog {
        val dialog = createThemedDialog(title, "[grow]", "[grow][]")

        val contentPanel = JPanel(MigLayout("fill, ins 0, gap 15", "[][grow]", "[grow]"))
        contentPanel.name = "infoDialogContentPanel"
        contentPanel.isOpaque = false

        if (icon != null) {
            val logoLabel = JLabel(icon)
            contentPanel.add(logoLabel, "top, left")
        }

        val messageArea =
            JTextArea(message).apply {
                name = "infoDialogMessageArea"
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                border = null
                font = Font("Inter", Font.PLAIN, 13)
            }

        contentPanel.add(messageArea, "grow, push")
        dialog.add(contentPanel, "grow, push, wrap")

        val closeButton = JButton(i18nService.translate("swing.button.close")).apply { name = "infoDialogCloseButton" }
        closeButton.addActionListener { dialog.dispose() }

        dialog.add(createActionRow(closeButton), "growx")

        dialog.setSize(460, 280)
        return dialog
    }

    fun createListManageButton(label: String, list: MutableList<String>): JButton =
        JButton("Manage $label (${list.size})").apply {
            addActionListener {
                showListEditDialog(label, list)
                text = "Manage $label (${list.size})"
            }
        }

    internal fun createThemedDialog(title: String, columns: String, rows: String): JDialog =
        JDialog(frame, title, true).apply {
            layout = MigLayout("fill, ins 24, gap 10", columns, rows)
            minimumSize = Dimension(MIN_DIALOG_WIDTH, MIN_DIALOG_HEIGHT)
        }

    internal fun createActionRow(vararg buttons: JButton): JPanel =
        JPanel(MigLayout("ins 0, fillx", "[grow]${"[]".repeat(buttons.size)}", "[]")).apply {
            isOpaque = false
            buttons.forEach { add(it, "right") }
        }

    internal fun showDialog(dialog: JDialog) {
        dialog.pack()
        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    companion object {
        const val MIN_DIALOG_WIDTH = 420
        const val MIN_DIALOG_HEIGHT = 250
        val COLOR_DANGER = Color(0xCC, 0x44, 0x44)
        val COLOR_SUCCESS = Color(0x22, 0x77, 0x22)
        private const val DIALOG_WIDTH = 600
        private const val DIALOG_HEIGHT = 400
    }
}
