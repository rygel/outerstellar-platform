package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.border.TitledBorder
import net.miginfocom.swing.MigLayout

class SyncProfilePanel(
    private var i18nService: I18nService,
    private val viewModel: SyncViewModel,
    private val frame: JFrame,
) {
    private var onNavigateToMessages: (() -> Unit)? = null

    fun setOnNavigateToMessages(callback: () -> Unit) {
        onNavigateToMessages = callback
    }

    fun createProfileView(navProfileBtn: JButton): JPanel {
        val panel = JPanel(MigLayout("fill, ins 20, gap 15", "[grow]", "[][][][grow]"))

        panel.add(
            JLabel(i18nService.translate("swing.profile.title")).apply { font = font.deriveFont(Font.BOLD, 18f) },
            "wrap, gapbottom 10",
        )

        val (profileInfoPanel, profileFields, _) = createProfileInfoPanel()
        panel.add(profileInfoPanel, "growx, wrap, gapbottom 12")

        val (notifPanel, emailCheckbox, pushCheckbox) = createNotifPrefsPanel()
        panel.add(notifPanel, "growx, wrap, gapbottom 12")

        val dangerPanel = createDangerPanel()
        panel.add(dangerPanel, "growx, wrap")

        viewModel.addObserver {
            javax.swing.SwingUtilities.invokeLater {
                navProfileBtn.isEnabled = viewModel.isLoggedIn
                if (viewModel.isLoggedIn) {
                    profileFields[0].text = viewModel.userName
                    profileFields[1].text = viewModel.userEmail
                    profileFields[2].text = viewModel.userAvatarUrl ?: ""
                    emailCheckbox.isSelected = viewModel.emailNotificationsEnabled
                    pushCheckbox.isSelected = viewModel.pushNotificationsEnabled
                }
            }
        }

        return panel
    }

    fun createProfileInfoPanel(): Triple<JPanel, Array<JTextField>, JLabel> {
        val panel = JPanel(MigLayout("fillx, ins 10, gap 8", "[120!][grow]", "[][][][] "))
        panel.border = BorderFactory.createTitledBorder(i18nService.translate("swing.profile.section.info"))

        val emailField = JTextField().apply { name = "profileEmailField" }
        val usernameField = JTextField().apply { name = "profileUsernameField" }
        val avatarUrlField = JTextField().apply { name = "profileAvatarUrlField" }
        val statusLabel =
            JLabel().apply {
                name = "profileStatusLabel"
                foreground = COLOR_SUCCESS
            }

        panel.add(JLabel(i18nService.translate("swing.profile.label.email")))
        panel.add(emailField, "growx, wrap")
        panel.add(JLabel(i18nService.translate("swing.profile.label.username")))
        panel.add(usernameField, "growx, wrap")
        panel.add(JLabel(i18nService.translate("swing.profile.label.avatar")))
        panel.add(avatarUrlField, "growx, wrap")
        panel.add(statusLabel, "skip 1, wrap")

        val saveBtn =
            JButton(i18nService.translate("swing.profile.save")).apply {
                name = "saveProfileBtn"
                icon = RemixIcon.get("system/save-line")
            }
        saveBtn.addActionListener {
            val email = emailField.text.trim()
            val username = usernameField.text.trim().takeIf { it.isNotBlank() }
            val avatarUrl = avatarUrlField.text.trim().takeIf { it.isNotBlank() }
            saveBtn.isEnabled = false
            viewModel.updateProfile(email, username, avatarUrl) { success, error ->
                saveBtn.isEnabled = true
                if (success) {
                    statusLabel.foreground = COLOR_SUCCESS
                    statusLabel.text = i18nService.translate("swing.profile.saved")
                } else {
                    statusLabel.foreground = COLOR_DANGER
                    statusLabel.text = error ?: i18nService.translate("swing.profile.save.failed")
                }
            }
        }
        panel.add(saveBtn, "skip 1, wrap")

        return Triple(panel, arrayOf(emailField, usernameField, avatarUrlField), statusLabel)
    }

    fun createNotifPrefsPanel(): Triple<JPanel, JCheckBox, JCheckBox> {
        val panel = JPanel(MigLayout("fillx, ins 10, gap 8", "[grow]", "[][]"))
        panel.border = BorderFactory.createTitledBorder(i18nService.translate("swing.profile.section.notifications"))

        val emailCheckbox =
            JCheckBox(i18nService.translate("swing.profile.notif.email")).apply { name = "emailNotifCheckbox" }
        val pushCheckbox =
            JCheckBox(i18nService.translate("swing.profile.notif.push")).apply { name = "pushNotifCheckbox" }
        val statusLabel =
            JLabel().apply {
                name = "notifStatusLabel"
                foreground = COLOR_SUCCESS
            }

        panel.add(emailCheckbox, "wrap")
        panel.add(pushCheckbox, "wrap")
        panel.add(statusLabel, "wrap")

        val saveBtn = JButton(i18nService.translate("swing.profile.notif.save")).apply { name = "saveNotifBtn" }
        saveBtn.addActionListener {
            saveBtn.isEnabled = false
            viewModel.updateNotificationPreferences(emailCheckbox.isSelected, pushCheckbox.isSelected) { success, error
                ->
                saveBtn.isEnabled = true
                if (success) {
                    statusLabel.foreground = COLOR_SUCCESS
                    statusLabel.text = i18nService.translate("swing.profile.notif.saved")
                } else {
                    statusLabel.foreground = COLOR_DANGER
                    statusLabel.text = error ?: i18nService.translate("swing.profile.save.failed")
                }
            }
        }
        panel.add(saveBtn, "wrap")

        return Triple(panel, emailCheckbox, pushCheckbox)
    }

    fun createDangerPanel(): JPanel {
        val panel = JPanel(MigLayout("fillx, ins 10, gap 8", "[grow]", "[][]"))
        panel.border =
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_DANGER, 1),
                i18nService.translate("swing.profile.section.danger"),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                COLOR_DANGER,
            )

        panel.add(JLabel(i18nService.translate("swing.profile.danger.description")), "wrap")
        val deleteAccountBtn =
            JButton(i18nService.translate("swing.profile.delete")).apply {
                name = "deleteAccountBtn"
                foreground = COLOR_DANGER
            }
        deleteAccountBtn.addActionListener {
            val confirmed =
                JOptionPane.showConfirmDialog(
                    frame,
                    i18nService.translate("swing.profile.delete.confirm"),
                    i18nService.translate("swing.profile.delete.confirm.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                )
            if (confirmed == JOptionPane.YES_OPTION) {
                deleteAccountBtn.isEnabled = false
                viewModel.deleteAccount { success, error ->
                    if (success) {
                        JOptionPane.showMessageDialog(
                            frame,
                            i18nService.translate("swing.profile.delete.success"),
                            i18nService.translate("swing.profile.delete.success.title"),
                            JOptionPane.INFORMATION_MESSAGE,
                        )
                        onNavigateToMessages?.invoke()
                    } else {
                        deleteAccountBtn.isEnabled = true
                        JOptionPane.showMessageDialog(
                            frame,
                            error ?: i18nService.translate("swing.profile.delete.failed"),
                            i18nService.translate("swing.profile.delete.error.title"),
                            JOptionPane.ERROR_MESSAGE,
                        )
                    }
                }
            }
        }
        panel.add(deleteAccountBtn, "wrap")
        return panel
    }

    fun updateI18n(service: I18nService) {
        i18nService = service
    }

    companion object {
        val COLOR_DANGER = Color(0xCC, 0x44, 0x44)
        val COLOR_SUCCESS = Color(0x22, 0x77, 0x22)
    }
}
