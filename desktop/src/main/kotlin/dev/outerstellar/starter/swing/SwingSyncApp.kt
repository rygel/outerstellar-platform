package dev.outerstellar.starter.swing

import com.formdev.flatlaf.FlatLightLaf
import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.desktopModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.ThemeCatalog
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.NoOpMessageCache
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.service.SyncProvider
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import net.miginfocom.swing.MigLayout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Locale
import javax.sql.DataSource
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToolBar
import javax.swing.JWindow
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

private const val FRAME_WIDTH = 1000
private const val FRAME_HEIGHT = 750
private const val CONFLICT_ICON_SIZE = 16
private const val DIALOG_WIDTH = 600
private const val DIALOG_HEIGHT = 400

object DesktopComponent : KoinComponent {
    val config: SwingAppConfig by inject()
    val dataSource: DataSource by inject()
    val messageService: MessageService by inject()
    val contactService: dev.outerstellar.starter.service.ContactService by inject()
    val syncService: SyncService by inject()
}

fun main() {
    System.setProperty("swing.aatext", "true")
    System.setProperty("awt.useSystemAAFontSettings", "lcd")

    val splash = showSplash()

    startKoin {
        modules(swingRuntimeModules())
    }

    val desktop = DesktopComponent
    migrate(desktop.dataSource)

    val savedState = DesktopStateProvider.loadState()
    val initialLocale = savedState?.language?.let { Locale.of(it) } ?: Locale.getDefault()
    Locale.setDefault(initialLocale)

    val i18nService = I18nService.create("messages").also { it.setLocale(initialLocale) }

    SwingUtilities.invokeLater {
        FlatLightLaf.setup()

        val themeManager = ThemeManager()
        val startupTheme = savedState?.themeId
            ?.let { themeId -> ThemeCatalog.allThemes().find { it.id == themeId } }
            ?: ThemeCatalog.findTheme("default")
        themeManager.applyTheme(startupTheme)

        val notifier = SystemTrayNotifier(i18nService)
        val viewModel = SyncViewModel(desktop.messageService, desktop.contactService, desktop.syncService, i18nService, notifier)
        val window = SyncWindow(viewModel, themeManager, i18nService, desktop.config.version)

        DeepLinkHandler.setup(
            onSearch = { query ->
                SwingUtilities.invokeLater {
                    viewModel.searchQuery = query
                    window.updateSearchField(query)
                }
            },
            onSync = {
                SwingUtilities.invokeLater {
                    viewModel.sync()
                }
            }
        )

        window.show()
        splash.dispose()
    }
}

private const val SPLASH_WIDTH = 400
private const val SPLASH_HEIGHT = 300
private const val SPLASH_LOGO_SIZE = 64
private const val SPLASH_TITLE_SIZE = 28
private const val SPLASH_STATUS_SIZE = 14
private const val LAYOUT_GAP = 20

private fun Color.toHtml() = String.format("#%02x%02x%02x", red, green, blue)

private fun showSplash(): JWindow {
    val window = JWindow()
    val borderColor = UIManager.getColor("Component.borderColor") ?: Color.GRAY
    val panelColor = UIManager.getColor("Panel.background") ?: Color.WHITE
    val labelColor = UIManager.getColor("Label.foreground") ?: Color.DARK_GRAY
    val content = JPanel(MigLayout("fill, ins 24, gap $LAYOUT_GAP", "[grow]", "[][grow][]"))
    content.border = javax.swing.BorderFactory.createLineBorder(borderColor)
    content.background = panelColor

    val logo = RemixIcon.get("system/planet-fill", SPLASH_LOGO_SIZE)
    val label = JLabel("Outerstellar", logo, SwingConstants.CENTER)
    label.font = Font("Inter", Font.BOLD, SPLASH_TITLE_SIZE)
    label.verticalTextPosition = SwingConstants.BOTTOM
    label.horizontalTextPosition = SwingConstants.CENTER
    label.foreground = labelColor

    val status = JLabel("Starting application...", SwingConstants.CENTER)
    status.font = Font("Inter", Font.PLAIN, SPLASH_STATUS_SIZE)
    status.foreground = labelColor

    content.add(JPanel().apply { isOpaque = false }, "growx, wrap")
    content.add(label, "grow, center, wrap")
    content.add(status, "growx")

    window.contentPane = content
    window.size = Dimension(SPLASH_WIDTH, SPLASH_HEIGHT)
    window.setLocationRelativeTo(null)
    window.isVisible = true
    return window
}

data class Contact(
    val name: String,
    val emails: List<String>,
    val phones: List<String>,
    val company: String,
    val companyAddress: String,
    val department: String
)

@Suppress("TooManyFunctions")
class SyncWindow(
    private val viewModel: SyncViewModel,
    private val themeManager: ThemeManager,
    private var i18nService: I18nService,
    private val appVersion: String = "dev",
) {
    val frame = JFrame(i18nService.translate("swing.app.title"))
    private lateinit var rootPanel: JPanel
    private lateinit var mainCardPanel: JPanel
    private val mainLayout = CardLayout()

    private lateinit var messagesPanel: JPanel
    private lateinit var contactsPanel: JPanel

    private lateinit var searchPanel: JPanel
    private lateinit var footerPanel: JPanel
    private lateinit var messagesScrollPane: JScrollPane
    private lateinit var contentScrollPane: JScrollPane
    private val messagesModel = DefaultListModel<MessageSummary>()
    private val messagesList = JList(messagesModel).apply { name = "messagesList" }

    private val contactsModel = DefaultTableModel(arrayOf("Name", "Emails", "Phones", "Company", "Company Address", "Department"), 0)
    private val contactsTable = JTable(contactsModel).apply { name = "contactsTable" }

    private val statusLabel = JLabel("Statusbar Test").apply {
        name = "statusLabel"
        toolTipText = "Statusbar Test"
    }
    private val statusHintLabel = JLabel().apply { name = "statusHintLabel" }
    private val statusMetaLabel = JLabel().apply { name = "statusMetaLabel" }
    private val searchLabel = JLabel().apply { name = "searchLabel" }
    private val authorLabel = JLabel().apply { name = "authorLabel" }
    private val statusBar = JToolBar().apply {
        name = "statusBarPanel"
        isFloatable = false
        isRollover = false
    }
    private val searchField = JTextField().apply { name = "searchField" }
    private val authorField = JTextField().apply { name = "authorField" }
    private val contentArea = JTextArea().apply {
        name = "contentArea"
        lineWrap = true
        wrapStyleWord = true
    }
    private val syncButton = JButton(i18nService.translate("swing.button.sync")).apply {
        name = "syncButton"
        icon = RemixIcon.get("system/refresh-line")
    }
    private val createButton = JButton(i18nService.translate("swing.button.create")).apply {
        name = "createButton"
        icon = RemixIcon.get("system/add-box-line")
        addActionListener {
            viewModel.createMessage { }
        }
    }

    private val appMenu = JMenu(i18nService.translate("swing.menu.file")).apply { name = "appMenu" }
    private val helpMenu = JMenu(i18nService.translate("swing.menu.help")).apply { name = "helpMenu" }
    private val settingsItem = JMenuItem(i18nService.translate("swing.menu.settings")).apply {
        name = "settingsItem"
        icon = RemixIcon.get("system/settings-3-line")
    }
    private val loginItem = JMenuItem(i18nService.translate("swing.auth.login")).apply {
        name = "loginItem"
        icon = RemixIcon.get("system/lock-password-line")
    }
    private val logoutItem = JMenuItem(i18nService.translate("swing.auth.logout.simple")).apply {
        name = "logoutItem"
        icon = RemixIcon.get("system/logout-box-r-line")
    }
    private val registerItem = JMenuItem(i18nService.translate("swing.auth.register")).apply {
        name = "registerItem"
        icon = RemixIcon.get("system/user-add-line")
    }
    private val newItem = JMenuItem(i18nService.translate("swing.menu.file.new")).apply { name = "newItem" }
    private val openItem = JMenuItem(i18nService.translate("swing.menu.file.open")).apply { name = "openItem" }
    private val saveItem = JMenuItem(i18nService.translate("swing.menu.file.save")).apply { name = "saveItem" }
    private val saveAsItem = JMenuItem(i18nService.translate("swing.menu.file.saveAs")).apply { name = "saveAsItem" }
    private val exitItem = JMenuItem(i18nService.translate("swing.menu.file.exit")).apply { name = "exitItem" }
    private val viewHelpItem = JMenuItem(i18nService.translate("swing.menu.help.view")).apply { name = "viewHelpItem" }
    private val sendFeedbackItem = JMenuItem(i18nService.translate("swing.menu.help.feedback")).apply {
        name = "sendFeedbackItem"
    }
    private val checkUpdatesItem = JMenuItem(i18nService.translate("swing.menu.help.updates")).apply {
        name = "checkUpdatesItem"
    }
    private val aboutItem = JMenuItem(
        i18nService.translate("swing.menu.help.about", i18nService.translate("swing.app.name"))
    ).apply {
        name = "aboutItem"
    }

    private lateinit var sidebarPanel: JPanel

    private val navMessagesBtn = JButton("Messages").apply {
        name = "navMessagesBtn"
        icon = RemixIcon.get("communication/chat-3-line", 32)
        font = font.deriveFont(16f)
        verticalTextPosition = SwingConstants.BOTTOM
        horizontalTextPosition = SwingConstants.CENTER
        putClientProperty("JButton.buttonType", "square")
    }

    private val navContactsBtn = JButton("Contacts").apply {
        name = "navContactsBtn"
        icon = RemixIcon.get("user/user-3-line", 32)
        font = font.deriveFont(16f)
        verticalTextPosition = SwingConstants.BOTTOM
        horizontalTextPosition = SwingConstants.CENTER
        putClientProperty("JButton.buttonType", "square")
    }

    fun show() {
        configureFrame()
        setupBinding()
        restoreState()
        viewModel.loadMessages()
        frame.isVisible = true
    }

    fun configureForTest() {
        configureFrame()
        setupBinding()
    }

    fun refreshTranslations(newI18n: I18nService) {
        this.i18nService = newI18n
        viewModel.refreshTranslations(newI18n)
        applyTranslations()
        updateUI()
    }

    private fun restoreState() {
        DesktopStateProvider.loadState()?.let { state ->
            if (state.isMaximized) {
                frame.extendedState = JFrame.MAXIMIZED_BOTH
            } else {
                frame.bounds = state.windowBounds
            }
            state.lastSearchQuery?.let {
                viewModel.searchQuery = it
                searchField.text = it
            }
        }
    }

    private fun saveState() {
        val themeName = UIManager.get("current_theme_name") as? String
        val state = DesktopState(
            windowBounds = frame.bounds,
            isMaximized = (frame.extendedState and JFrame.MAXIMIZED_BOTH) != 0,
            lastSearchQuery = viewModel.searchQuery.takeIf { it.isNotBlank() },
            themeId = ThemeCatalog.allThemes().find { it.name == themeName }?.id,
            language = Locale.getDefault().language
        )
        DesktopStateProvider.saveState(state)
    }

    private fun setupBinding() {
        viewModel.addObserver {
            updateUI()
        }

        val docListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { updateViewModel() }
            override fun removeUpdate(e: DocumentEvent?) { updateViewModel() }
            override fun changedUpdate(e: DocumentEvent?) { updateViewModel() }

            private fun updateViewModel() {
                viewModel.author = authorField.text
                viewModel.content = contentArea.text
            }
        }

        authorField.document.addDocumentListener(docListener)
        contentArea.document.addDocumentListener(docListener)

        authorField.text = viewModel.author
        contentArea.text = viewModel.content

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { viewModel.searchQuery = searchField.text }
            override fun removeUpdate(e: DocumentEvent?) { viewModel.searchQuery = searchField.text }
            override fun changedUpdate(e: DocumentEvent?) { viewModel.searchQuery = searchField.text }
        })

        messagesList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val index = messagesList.locationToIndex(e.point)
                    if (index >= 0) {
                        val msg = messagesModel.getElementAt(index)
                        if (msg.hasConflict) {
                            showConflictDialog(msg)
                        }
                    }
                }
            }
        })
    }

    private fun updateUI() {
        messagesModel.clear()
        viewModel.messages.forEach(messagesModel::addElement)
        
        contactsModel.rowCount = 0
        viewModel.contacts.forEach { contact ->
            contactsModel.addRow(arrayOf(
                contact.name,
                contact.emails.joinToString(", "),
                contact.phones.joinToString(", "),
                contact.company,
                contact.companyAddress,
                contact.department
            ))
        }
        
        statusLabel.text = viewModel.status
        statusLabel.toolTipText = viewModel.status
        syncButton.isEnabled = !viewModel.isSyncing

        if (contentArea.text != viewModel.content) {
            contentArea.text = viewModel.content
        }
        if (searchField.text != viewModel.searchQuery) {
            searchField.text = viewModel.searchQuery
        }

        loginItem.isEnabled = !viewModel.isLoggedIn
        logoutItem.isEnabled = viewModel.isLoggedIn
        registerItem.isEnabled = !viewModel.isLoggedIn
    }

    fun updateSearchField(query: String) {
        searchField.text = query
    }

    private fun configureFrame() {
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                saveState()
                viewModel.stopAutoSync()
            }
        })
        frame.minimumSize = Dimension(FRAME_WIDTH, FRAME_HEIGHT)
        frame.setLocationRelativeTo(null)
        frame.jMenuBar = createMenuBar()

        // SIDEBAR
        sidebarPanel = JPanel(MigLayout("fillx, ins 10, gap 10", "[grow]", "[]10[]10[grow]")).apply {
            name = "sidebarPanel"
        }

        navMessagesBtn.addActionListener { mainLayout.show(mainCardPanel, "MESSAGES") }
        navContactsBtn.addActionListener { mainLayout.show(mainCardPanel, "CONTACTS") }

        sidebarPanel.add(navMessagesBtn, "growx, h 100!, wrap")
        sidebarPanel.add(navContactsBtn, "growx, h 100!, wrap")
        sidebarPanel.add(Box.createVerticalGlue(), "growy")

        // MAIN CONTENT (CARD LAYOUT)
        mainCardPanel = JPanel(mainLayout)

        // --- Messages View ---
        messagesPanel = JPanel(MigLayout("fill, ins 20, gap 15", "[grow]", "[][grow]"))

        searchPanel = JPanel(MigLayout("fillx, ins 0", "[][grow][]", "[]"))
        searchPanel.add(searchLabel)
        searchPanel.add(searchField, "growx")
        searchPanel.add(syncButton, "w 120!")
        messagesPanel.add(searchPanel, "growx, wrap")

        messagesList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).also { c ->
                val msg = value as MessageSummary
                val dangerColor = (UIManager.getColor("Theme.danger") ?: Color.RED).toHtml()
                val accentColor = (UIManager.getColor("Theme.accent") ?: Color.BLUE).toHtml()
                val conflictMarker = if (msg.hasConflict) " <font color='$dangerColor'>[CONFLICT]</font>" else ""
                val localMarker = if (msg.dirty) " <font color='$accentColor'>(Local)</font>" else ""
                (c as JLabel).text = "<html><b>${msg.author}</b>$localMarker$conflictMarker &mdash; " +
                    "${msg.updatedAtLabel()}<br/>${msg.content}</html>"
                if (msg.hasConflict) {
                    c.icon = RemixIcon.get("system/error-warning-line", CONFLICT_ICON_SIZE)
                } else {
                    c.icon = null
                }
            }
        }
        messagesScrollPane = JScrollPane(messagesList)
        messagesPanel.add(messagesScrollPane, "grow, wrap")

        footerPanel = JPanel(MigLayout("fillx, ins 0", "[grow][]", "[][]"))
        footerPanel.add(authorLabel, "split 2")
        footerPanel.add(authorField, "growx, wrap")
        contentScrollPane = JScrollPane(contentArea)
        footerPanel.add(contentScrollPane, "grow, h 80!, span, wrap")
        footerPanel.add(createButton, "w 180!")
        messagesPanel.add(footerPanel, "growx")

        mainCardPanel.add(messagesPanel, "MESSAGES")

        // --- Contacts View ---
        contactsPanel = JPanel(MigLayout("fill, ins 20", "[grow]", "[][grow]"))
        contactsPanel.add(
            JLabel("Contacts Directory").apply { font = font.deriveFont(Font.BOLD, 18f) },
            "wrap, gapbottom 10"
        )
        contactsPanel.add(JScrollPane(contactsTable), "grow")

        contactsTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = contactsTable.rowAtPoint(e.point)
                    if (row >= 0) {
                        val name = contactsModel.getValueAt(row, 0).toString()
                        val emails = contactsModel.getValueAt(row, 1).toString()
                        val phones = contactsModel.getValueAt(row, 2).toString()
                        val company = contactsModel.getValueAt(row, 3).toString()
                        val address = contactsModel.getValueAt(row, 4).toString()
                        val department = contactsModel.getValueAt(row, 5).toString()
                        showContactDialog(name, emails, phones, company, address, department)
                    }
                }
            }
        })
        
        mainCardPanel.add(contactsPanel, "CONTACTS")

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, mainCardPanel).apply {
            dividerLocation = 140
            dividerSize = 1
            border = null
        }

        configureStatusBar()
        rootPanel = JPanel(BorderLayout()).apply {
            add(splitPane, BorderLayout.CENTER)
            add(statusBar, BorderLayout.SOUTH)
        }
        frame.contentPane = rootPanel
        applyTranslations()
    }

    private fun createMenuBar(): JMenuBar {
        val menuBar = JMenuBar()
        settingsItem.addActionListener { showSettingsDialog() }
        loginItem.addActionListener { showLoginDialog() }
        logoutItem.addActionListener { viewModel.logout() }
        registerItem.addActionListener { showRegisterDialog() }
        newItem.addActionListener { clearComposer() }
        openItem.addActionListener { showMenuPlaceholder("swing.menu.file.open") }
        saveItem.addActionListener { showMenuPlaceholder("swing.menu.file.save") }
        saveAsItem.addActionListener { showMenuPlaceholder("swing.menu.file.saveAs") }
        exitItem.addActionListener { frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING)) }
        viewHelpItem.addActionListener { showHelpDialog() }
        sendFeedbackItem.addActionListener { showFeedbackDialog() }
        checkUpdatesItem.addActionListener { showUpdateCheckDialog() }
        aboutItem.addActionListener { showAboutDialog() }

        val menuMask = InputEvent.CTRL_DOWN_MASK
        newItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask)
        openItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask)
        saveItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask)
        saveAsItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask or InputEvent.SHIFT_DOWN_MASK)
        exitItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK)

        appMenu.add(loginItem)
        appMenu.add(logoutItem)
        appMenu.addSeparator()
        appMenu.add(registerItem)
        appMenu.addSeparator()
        appMenu.add(newItem)
        appMenu.add(openItem)
        appMenu.add(saveItem)
        appMenu.add(saveAsItem)
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

    private fun showLoginDialog() {
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
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        dialog.add(createActionRow(loginBtn), "span, growx")
        showDialog(dialog)
    }

    private fun showConflictDialog(msg: MessageSummary) {
        val dialog = JDialog(frame, "Resolve Sync Conflict", true)
        dialog.layout = MigLayout("fill, ins 20, gap 10", "[grow][grow]", "[][grow][]")

        val introText = "<html>A sync conflict was detected for this message.<br/>" +
            "Please choose which version to keep:</html>"
        dialog.add(JLabel(introText), "span, wrap, gapbottom 15")

        val localPanel = JPanel(MigLayout("fillx, ins 10", "[grow]", "[][]"))
        localPanel.border = javax.swing.BorderFactory.createTitledBorder("My Local Version")
        localPanel.add(JLabel("Author: ${msg.author}"), "wrap")
        localPanel.add(
            JScrollPane(
                JTextArea(msg.content).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                }
            ),
            "grow, h 100!"
        )

        val serverPanel = JPanel(MigLayout("fillx, ins 10", "[grow]", "[][]"))
        serverPanel.border = javax.swing.BorderFactory.createTitledBorder("Server Version")
        serverPanel.add(JLabel("Server has a newer version."), "wrap")
        serverPanel.add(JLabel("Accepting it will overwrite your local changes."), "grow, wrap")

        dialog.add(localPanel, "grow")
        dialog.add(serverPanel, "grow, wrap")

        val mineBtn = JButton("Keep My Version")
        val serverBtn = JButton("Accept Server Version")

        mineBtn.addActionListener {
            viewModel.resolveConflict(msg.syncId, "mine")
            dialog.dispose()
        }
        serverBtn.addActionListener {
            viewModel.resolveConflict(msg.syncId, "server")
            dialog.dispose()
        }

        dialog.add(mineBtn, "split 2, span, center")
        dialog.add(serverBtn)

        dialog.pack()
        dialog.setSize(DIALOG_WIDTH, DIALOG_HEIGHT)
        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    private fun showContactDialog(
        name: String, 
        emails: String, 
        phones: String, 
        company: String, 
        companyAddress: String, 
        department: String
    ) {
        val dialog = createThemedDialog("Contact Details", "[grow]", "[][][][][][][]")
        
        dialog.add(JLabel("Name: $name"), "wrap")
        dialog.add(JLabel("Emails: $emails"), "wrap")
        dialog.add(JLabel("Phones: $phones"), "wrap")
        dialog.add(JLabel("Company: $company"), "wrap")
        dialog.add(JLabel("Department: $department"), "wrap")
        dialog.add(JLabel("Address: $companyAddress"), "wrap")
        
        val closeBtn = JButton(i18nService.translate("swing.button.close"))
        closeBtn.addActionListener { dialog.dispose() }
        
        dialog.add(createActionRow(closeBtn), "span, growx, gaptop 15")
        
        dialog.pack()
        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    private fun showSettingsDialog() {
        val dialog = createThemedDialog(i18nService.translate("swing.settings.title"), "[][grow]", "[][][]")
        dialog.name = "settingsDialog"

        dialog.add(JLabel(i18nService.translate("swing.settings.language")))
        val languages = arrayOf(
            "en" to i18nService.translate("swing.language.en"),
            "fr" to i18nService.translate("swing.language.fr")
        )
        val langCombo = JComboBox<String>(languages.map { it.second }.toTypedArray()).apply { name = "langCombo" }
        langCombo.selectedIndex = languages.indexOfFirst { it.first == Locale.getDefault().language }.coerceAtLeast(0)
        dialog.add(langCombo, "growx, wrap")

        dialog.add(JLabel(i18nService.translate("swing.settings.theme")))
        val allThemes = ThemeCatalog.allThemes().sortedBy { it.name }
        val themeCombo = JComboBox<String>(allThemes.map { it.name }.toTypedArray()).apply { name = "themeCombo" }
        val currentThemeName = UIManager.get("current_theme_name") as? String
        themeCombo.selectedIndex = allThemes.indexOfFirst { it.name == currentThemeName }.coerceAtLeast(0)
        dialog.add(themeCombo, "growx, wrap")

        val previewPanel = JPanel(MigLayout("fillx, ins 15, gap 10", "[grow]", "[][][]")).apply {
            name = "previewPanel"
        }
        val sampleLabel = JLabel("Sample Label Text")
        val sampleField = JTextField("Sample Input Text")
        val sampleButton = JButton("Sample Button")
        previewPanel.add(sampleLabel, "wrap")
        previewPanel.add(sampleField, "growx, wrap")
        previewPanel.add(sampleButton, "center")
        dialog.add(previewPanel, "span, growx, wrap, gaptop 15")

        fun updatePreview(theme: dev.outerstellar.starter.model.ThemeDefinition) {
            val colors = theme.colors
            val bg = themeManager.decodeColor(colors["background"]) ?: Color.WHITE
            val fg = themeManager.decodeColor(colors["foreground"]) ?: Color.BLACK
            val compBg = themeManager.decodeColor(colors["componentBackground"]) ?: Color.WHITE
            val accent = themeManager.decodeColor(colors["accent"]) ?: Color.BLUE
            val border = themeManager.decodeColor(colors["borderColor"]) ?: Color.GRAY

            previewPanel.background = bg
            previewPanel.border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(border),
                "Theme Preview: ${theme.name}",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                fg
            )
            sampleLabel.foreground = fg
            sampleField.background = compBg
            sampleField.foreground = fg
            sampleButton.background = accent
            sampleButton.foreground = fg
        }

        themeCombo.addActionListener {
            updatePreview(allThemes[themeCombo.selectedIndex])
        }
        updatePreview(allThemes[themeCombo.selectedIndex])

        val applyButton = JButton(i18nService.translate("swing.settings.button.apply")).apply { name = "applyButton" }
        val cancelButton = JButton(
            i18nService.translate("swing.settings.button.cancel"),
        ).apply { name = "cancelButton" }

        applyButton.addActionListener {
            val selectedTheme = allThemes[themeCombo.selectedIndex]
            themeManager.applyTheme(selectedTheme)

            val selectedLang = languages[langCombo.selectedIndex].first
            val newLocale = Locale.of(selectedLang)
            Locale.setDefault(newLocale)
            refreshTranslations(I18nService.create("messages").also { it.setLocale(newLocale) })
            saveState()
            frame.revalidate()
            frame.repaint()
            dialog.dispose()
        }
        cancelButton.addActionListener { dialog.dispose() }

        dialog.add(createActionRow(cancelButton, applyButton), "span, growx")
        showDialog(dialog)
    }

    private fun showRegisterDialog() {
        val dialog = createThemedDialog(
            i18nService.translate("swing.auth.register.dialog.title"),
            "[][grow]",
            "[][][][]"
        )

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
                    JOptionPane.ERROR_MESSAGE
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
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        dialog.add(createActionRow(registerBtn), "span, growx")
        showDialog(dialog)
    }

    private fun clearComposer() {
        authorField.text = i18nService.translate("swing.author.default")
        contentArea.text = ""
    }

    private fun showMenuPlaceholder(key: String) {
        JOptionPane.showMessageDialog(
            frame,
            i18nService.translate("swing.menu.placeholder", i18nService.translate(key)),
            i18nService.translate("swing.menu.placeholder.title"),
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun showHelpDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.view"),
            message = i18nService.translate("swing.help.message"),
            icon = RemixIcon.get("system/question-line", 24)
        )
    }

    private fun showAboutDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.about", i18nService.translate("swing.app.name")),
            message = i18nService.translate("swing.about.message", i18nService.translate("swing.app.name"), appVersion),
            icon = RemixIcon.get("system/information-line", 24)
        )
    }

    private fun showFeedbackDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.feedback"),
            message = i18nService.translate("swing.feedback.message"),
            icon = RemixIcon.get("communication/chat-smile-3-line", 24)
        )
    }

    private fun showUpdateCheckDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.updates"),
            message = i18nService.translate("swing.updates.message", appVersion),
            icon = RemixIcon.get("system/refresh-line", 24)
        )
    }

    private fun showInfoDialog(title: String, message: String, icon: javax.swing.Icon?) {
        val dialog = buildInfoDialog(title, message, icon)
        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    internal fun buildInfoDialog(title: String, message: String, icon: javax.swing.Icon?): JDialog {
        val dialog = createThemedDialog(title, "[grow]", "[grow][]")

        val contentPanel = JPanel(MigLayout("fill, ins 0, gap 15", "[][grow]", "[grow]"))
        contentPanel.name = "infoDialogContentPanel"
        contentPanel.isOpaque = false

        if (icon != null) {
            val logoLabel = JLabel(icon)
            contentPanel.add(logoLabel, "top, left")
        }

        val messageArea = JTextArea(message).apply {
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

        val closeButton = JButton(i18nService.translate("swing.button.close")).apply {
            name = "infoDialogCloseButton"
        }
        closeButton.addActionListener { dialog.dispose() }

        dialog.add(createActionRow(closeButton), "growx")

        dialog.setSize(460, 280)
        return dialog
    }

    private fun createThemedDialog(title: String, columns: String, rows: String): JDialog =
        JDialog(frame, title, true).apply {
            layout = MigLayout("fill, ins 24, gap 10", columns, rows)
        }

    private fun createActionRow(vararg buttons: JButton): JPanel =
        JPanel(MigLayout("ins 0, fillx", "[grow]${"[]".repeat(buttons.size)}", "[]")).apply {
            isOpaque = false
            buttons.forEach { add(it, "right") }
        }

    private fun showDialog(dialog: JDialog) {
        dialog.pack()
        dialog.setLocationRelativeTo(frame)
        dialog.isVisible = true
    }

    private fun configureStatusBar() {
        statusBar.removeAll()
        statusBar.add(statusLabel)
        statusBar.add(Box.createHorizontalGlue())
        statusBar.add(statusHintLabel)
        statusBar.addSeparator(Dimension(10, 0))
        statusBar.add(statusMetaLabel)
    }

    private fun applyTranslations() {
        frame.title = i18nService.translate("swing.app.title")
        syncButton.text = i18nService.translate("swing.button.sync")
        createButton.text = i18nService.translate("swing.button.create")
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
        searchLabel.text = i18nService.translate("swing.label.search")
        authorLabel.text = i18nService.translate("swing.label.author")
        navMessagesBtn.text = i18nService.translate("swing.menu.file")
        navContactsBtn.text = "Contacts"
        statusHintLabel.text = ""
        statusMetaLabel.text = i18nService.translate("swing.statusbar.version", appVersion)
        if (statusLabel.text.isBlank()) {
            statusLabel.text = "Statusbar Test"
            statusLabel.toolTipText = statusLabel.text
        }
    }
}

internal fun swingRuntimeModules(): List<Module> = listOf(
    desktopModule,
    module {
        single {
            val cfg = get<SwingAppConfig>()
            AppConfig(
                jdbcUrl = cfg.jdbcUrl,
                jdbcUser = cfg.jdbcUser,
                jdbcPassword = cfg.jdbcPassword
            )
        }
        single<MessageCache> { NoOpMessageCache }
        single<SyncService> {
            SyncService(
                baseUrl = get(named("serverBaseUrl")),
                repository = get(),
                transactionManager = get()
            )
        }
        single<SyncProvider> { get<SyncService>() }
    },
    persistenceModule,
    coreModule
)
