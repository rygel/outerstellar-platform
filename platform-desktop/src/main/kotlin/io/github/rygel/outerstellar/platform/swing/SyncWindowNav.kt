package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.UserRole
import javax.swing.JButton
import javax.swing.SwingConstants

class SyncWindowNav(private var i18nService: I18nService) {
    val navMessagesBtn: JButton =
        JButton(i18nService.translate("swing.nav.messages")).apply {
            name = "navMessagesBtn"
            icon = RemixIcon.get("communication/chat-3-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
        }

    val navContactsBtn: JButton =
        JButton(i18nService.translate("swing.contact.nav")).apply {
            name = "navContactsBtn"
            icon = RemixIcon.get("user/user-3-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
        }

    val navUsersBtn: JButton =
        JButton(i18nService.translate("swing.admin.users.nav")).apply {
            name = "navUsersBtn"
            icon = RemixIcon.get("user/group-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
            isEnabled = false
        }

    val navNotificationsBtn: JButton =
        JButton(i18nService.translate("swing.notifications.nav")).apply {
            name = "navNotificationsBtn"
            icon = RemixIcon.get("system/notification-3-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
            isEnabled = false
        }

    val navProfileBtn: JButton =
        JButton(i18nService.translate("swing.profile.nav")).apply {
            name = "navProfileBtn"
            icon = RemixIcon.get("user/account-circle-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
            isEnabled = false
        }

    fun updateAuthState(isLoggedIn: Boolean, userRole: String?) {
        navUsersBtn.isEnabled = isLoggedIn && userRole == UserRole.ADMIN.name
        navProfileBtn.isEnabled = isLoggedIn
    }

    fun applyTranslations() {
        navMessagesBtn.text = i18nService.translate("swing.nav.messages")
        navContactsBtn.text = i18nService.translate("swing.contact.nav")
        navUsersBtn.text = i18nService.translate("swing.admin.users.nav")
        navNotificationsBtn.text = i18nService.translate("swing.notifications.nav")
        navProfileBtn.text = i18nService.translate("swing.profile.nav")
    }

    fun updateI18n(newI18n: I18nService) {
        // Translation refresh is handled by applyTranslations()
    }
}
