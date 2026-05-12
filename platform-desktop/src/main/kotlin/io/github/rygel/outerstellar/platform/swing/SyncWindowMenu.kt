package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.KeyStroke

internal class MenuDialogActions(
    val showSettings: () -> Unit,
    val showLogin: () -> Unit,
    val showRegister: () -> Unit,
    val showChangePassword: () -> Unit,
    val showHelp: () -> Unit,
    val showFeedback: () -> Unit,
    val showUpdateCheck: () -> Unit,
    val showAbout: () -> Unit,
    val clearComposer: () -> Unit,
    val showMenuPlaceholder: (String) -> Unit,
)

internal class SyncWindowMenu(
    private val viewModel: SyncViewModel,
    private var i18nService: I18nService,
    private val frame: javax.swing.JFrame,
    private val dialogs: MenuDialogActions,
) {

    val appMenu: JMenu = JMenu(i18nService.translate("swing.menu.file")).apply { name = "appMenu" }
    val helpMenu: JMenu = JMenu(i18nService.translate("swing.menu.help")).apply { name = "helpMenu" }

    val settingsItem: JMenuItem =
        JMenuItem(i18nService.translate("swing.menu.settings")).apply { name = "settingsItem" }
    val loginItem: JMenuItem = JMenuItem(i18nService.translate("swing.auth.login")).apply { name = "loginItem" }
    val logoutItem: JMenuItem =
        JMenuItem(i18nService.translate("swing.auth.logout.simple")).apply { name = "logoutItem" }
    val registerItem: JMenuItem =
        JMenuItem(i18nService.translate("swing.auth.register")).apply { name = "registerItem" }
    val changePasswordItem: JMenuItem =
        JMenuItem(i18nService.translate("swing.password.change")).apply {
            name = "changePasswordItem"
            icon = RemixIcon.get("system/lock-password-line")
            isEnabled = false
        }

    val newItem: JMenuItem = JMenuItem(i18nService.translate("swing.menu.file.new")).apply { name = "newItem" }
    val openItem: JMenuItem = JMenuItem(i18nService.translate("swing.menu.file.open")).apply { name = "openItem" }
    val saveItem: JMenuItem = JMenuItem(i18nService.translate("swing.menu.file.save")).apply { name = "saveItem" }
    val saveAsItem: JMenuItem = JMenuItem(i18nService.translate("swing.menu.file.saveAs")).apply { name = "saveAsItem" }
    val exitItem: JMenuItem = JMenuItem(i18nService.translate("swing.menu.file.exit")).apply { name = "exitItem" }
    val viewHelpItem: JMenuItem =
        JMenuItem(i18nService.translate("swing.menu.help.view")).apply { name = "viewHelpItem" }
    val sendFeedbackItem: JMenuItem =
        JMenuItem(i18nService.translate("swing.menu.help.feedback")).apply { name = "sendFeedbackItem" }
    val checkUpdatesItem: JMenuItem =
        JMenuItem(i18nService.translate("swing.menu.help.updates")).apply { name = "checkUpdatesItem" }
    val aboutItem: JMenuItem =
        JMenuItem(i18nService.translate("swing.menu.help.about", i18nService.translate("swing.app.name"))).apply {
            name = "aboutItem"
        }

    private val menuBar: JMenuBar by lazy { createMenuBar() }

    private fun createMenuBar(): JMenuBar {
        val menuBar = JMenuBar()

        settingsItem.addActionListener { dialogs.showSettings() }
        loginItem.addActionListener { dialogs.showLogin() }
        logoutItem.addActionListener { viewModel.logout() }
        registerItem.addActionListener { dialogs.showRegister() }
        changePasswordItem.addActionListener { dialogs.showChangePassword() }
        newItem.addActionListener { dialogs.clearComposer() }
        openItem.addActionListener { dialogs.showMenuPlaceholder("swing.menu.file.open") }
        saveItem.addActionListener { dialogs.showMenuPlaceholder("swing.menu.file.save") }
        saveAsItem.addActionListener { dialogs.showMenuPlaceholder("swing.menu.file.saveAs") }
        exitItem.addActionListener { frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING)) }
        viewHelpItem.addActionListener { dialogs.showHelp() }
        sendFeedbackItem.addActionListener { dialogs.showFeedback() }
        checkUpdatesItem.addActionListener { dialogs.showUpdateCheck() }
        aboutItem.addActionListener { dialogs.showAbout() }

        val menuMask = InputEvent.CTRL_DOWN_MASK
        newItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask)
        openItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask)
        saveItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask)
        saveAsItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask or InputEvent.SHIFT_DOWN_MASK)
        exitItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK)

        appMenu.add(newItem)
        appMenu.add(openItem)
        appMenu.add(saveItem)
        appMenu.add(saveAsItem)
        appMenu.addSeparator()
        appMenu.add(loginItem)
        appMenu.add(logoutItem)
        appMenu.add(registerItem)
        appMenu.add(changePasswordItem)
        appMenu.addSeparator()
        appMenu.add(settingsItem)
        appMenu.addSeparator()
        appMenu.add(exitItem)

        helpMenu.add(viewHelpItem)
        helpMenu.add(sendFeedbackItem)
        helpMenu.add(checkUpdatesItem)
        helpMenu.addSeparator()
        helpMenu.add(aboutItem)

        menuBar.add(appMenu)
        menuBar.add(helpMenu)
        return menuBar
    }

    fun buildMenuBar(): JMenuBar = menuBar

    fun updateI18n(newI18n: I18nService) {
        i18nService = newI18n
    }

    fun updateAuthState(isLoggedIn: Boolean) {
        loginItem.isEnabled = !isLoggedIn
        logoutItem.isEnabled = isLoggedIn
        registerItem.isEnabled = !isLoggedIn
        changePasswordItem.isEnabled = isLoggedIn
    }

    fun applyTranslations() {
        appMenu.text = i18nService.translate("swing.menu.file")
        helpMenu.text = i18nService.translate("swing.menu.help")
        settingsItem.text = i18nService.translate("swing.menu.settings")
        loginItem.text = i18nService.translate("swing.auth.login")
        logoutItem.text = i18nService.translate("swing.auth.logout.simple")
        registerItem.text = i18nService.translate("swing.auth.register")
        newItem.text = i18nService.translate("swing.menu.file.new")
        openItem.text = i18nService.translate("swing.menu.file.open")
        saveItem.text = i18nService.translate("swing.menu.file.save")
        saveAsItem.text = i18nService.translate("swing.menu.file.saveAs")
        exitItem.text = i18nService.translate("swing.menu.file.exit")
        viewHelpItem.text = i18nService.translate("swing.menu.help.view")
        sendFeedbackItem.text = i18nService.translate("swing.menu.help.feedback")
        checkUpdatesItem.text = i18nService.translate("swing.menu.help.updates")
        aboutItem.text = i18nService.translate("swing.menu.help.about", i18nService.translate("swing.app.name"))
        changePasswordItem.text = i18nService.translate("swing.password.change")
    }
}
