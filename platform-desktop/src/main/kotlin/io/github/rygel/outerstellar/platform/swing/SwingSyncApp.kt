package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.desktopModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.NoOpMessageCache
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.SyncProvider
import io.github.rygel.outerstellar.platform.swing.analytics.PersistentBatchingAnalyticsService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker
import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
import io.github.rygel.outerstellar.platform.sync.engine.HttpConnectivityChecker
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
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
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
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel
import net.miginfocom.swing.MigLayout
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val FRAME_WIDTH = 1000
private const val FRAME_HEIGHT = 750

object DesktopComponent : KoinComponent {
    val config: SwingAppConfig = get()
    val messageService: MessageService = get()
    val contactService: io.github.rygel.outerstellar.platform.service.ContactService = get()
    val syncService: SyncService = get()
}

fun main() {
    System.setProperty("swing.aatext", "true")
    System.setProperty("awt.useSystemAAFontSettings", "lcd")

    val splash = showSplash()

    startKoin { modules(swingRuntimeModules()) }

    val desktop = DesktopComponent

    val analytics =
        if (desktop.config.analyticsEnabled && desktop.config.segmentWriteKey.isNotBlank()) {
            PersistentBatchingAnalyticsService(
                writeKey = desktop.config.segmentWriteKey,
                dataDir = Path.of("./data"),
                maxFileSizeBytes = desktop.config.analyticsMaxFileSizeKb * 1024,
                maxEventAgeDays = desktop.config.analyticsMaxEventAgeDays,
            )
        } else {
            null
        }

    // Start connectivity checker early so analytics scheduler can skip flush when offline
    val connectivityChecker =
        HttpConnectivityChecker(healthUrl = "${desktop.config.serverBaseUrl}/health").also { it.start() }

    // Flush any events left over from the previous session, then schedule daily flushes
    val analyticsScheduler =
        if (analytics != null) {
            Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "analytics-flush").also { it.isDaemon = true } }
                .also { scheduler ->
                    scheduler.execute { if (connectivityChecker.isOnline) analytics.flush() }
                    scheduler.scheduleAtFixedRate(
                        { if (connectivityChecker.isOnline) analytics.flush() },
                        desktop.config.analyticsFlushIntervalHours,
                        desktop.config.analyticsFlushIntervalHours,
                        TimeUnit.HOURS,
                    )
                }
        } else {
            null
        }

    Runtime.getRuntime()
        .addShutdownHook(
            Thread(
                {
                    connectivityChecker.stop()
                    analyticsScheduler?.shutdown()
                    if (connectivityChecker.isOnline) analytics?.flush()
                },
                "analytics-shutdown",
            )
        )

    val savedState = DesktopStateProvider.loadState()
    val initialLocale = savedState?.language?.let { Locale.of(it) } ?: Locale.getDefault()
    Locale.setDefault(initialLocale)

    val i18nService = I18nService.create("messages").also { it.setLocale(initialLocale) }

    SwingUtilities.invokeLater { initializeUi(splash, analytics, connectivityChecker, savedState, i18nService) }
}

private fun initializeUi(
    splash: JWindow,
    analytics: PersistentBatchingAnalyticsService?,
    connectivityChecker: ConnectivityChecker,
    savedState: DesktopState?,
    i18nService: I18nService,
) {
    val themeManager = ThemeManager()
    val startupTheme =
        savedState?.themeId?.let { themeId ->
            DesktopTheme.entries.firstOrNull { it.name.equals(themeId, ignoreCase = true) }
        } ?: DesktopTheme.DARK
    themeManager.applyTheme(startupTheme)

    val notifier = SystemTrayNotifier(i18nService)
    val engine =
        DesktopSyncEngine(
            DesktopComponent.syncService,
            DesktopComponent.messageService,
            DesktopComponent.contactService,
            analytics ?: NoOpAnalyticsService(),
            connectivityChecker,
            notifier,
        )
    val viewModel = SyncViewModel(engine, i18nService, DesktopComponent.contactService)
    val window =
        SyncWindow(
            viewModel,
            themeManager,
            i18nService,
            DesktopComponent.config.version,
            DesktopComponent.config.updateUrl,
        )

    DeepLinkHandler.setup(
        onSearch = { query ->
            SwingUtilities.invokeLater {
                viewModel.searchQuery = query
                window.updateSearchField(query)
            }
        },
        onSync = { SwingUtilities.invokeLater { viewModel.sync() } },
    )

    window.show()
    splash.dispose()

    if (
        DesktopComponent.config.devMode &&
            DesktopComponent.config.devUsername.isNotBlank() &&
            DesktopComponent.config.devPassword.isNotBlank()
    ) {
        viewModel.login(DesktopComponent.config.devUsername, DesktopComponent.config.devPassword) { success, error ->
            if (!success) {
                println("Dev auto-login failed: $error")
            }
        }
    }
}

private const val SPLASH_WIDTH = 400
private const val SPLASH_HEIGHT = 300
private const val SPLASH_LOGO_SIZE = 64
private const val SPLASH_TITLE_SIZE = 28
private const val SPLASH_STATUS_SIZE = 14
private const val LAYOUT_GAP = 20

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

@Suppress("TooManyFunctions")
class SyncWindow(
    private val viewModel: SyncViewModel,
    private val themeManager: ThemeManager,
    private var i18nService: I18nService,
    private val appVersion: String = "dev",
    private val updateUrl: String = "",
) {
    val frame = JFrame(i18nService.translate("swing.app.title"))
    private lateinit var rootPanel: JPanel
    private lateinit var mainCardPanel: JPanel
    private val mainLayout = CardLayout()

    private lateinit var messagesPanel: JPanel
    private lateinit var contactsPanel: JPanel
    private lateinit var usersPanel: JPanel
    private lateinit var notificationsPanel: JPanel
    private lateinit var profilePanel: JPanel

    private val messagesModel = DefaultListModel<MessageSummary>()
    private val messagesList = JList(messagesModel).apply { name = "messagesList" }

    private val contactsModel =
        DefaultTableModel(
            arrayOf(
                i18nService.translate("swing.contact.table.name"),
                i18nService.translate("swing.contact.table.emails"),
                i18nService.translate("swing.contact.table.phones"),
                i18nService.translate("swing.contact.table.socials"),
                i18nService.translate("swing.contact.table.company"),
                i18nService.translate("swing.contact.table.company.address"),
                i18nService.translate("swing.contact.table.department"),
                "SyncID",
            ),
            0,
        )
    private val contactsTable =
        JTable(contactsModel).apply {
            name = "contactsTable"
            // Hide the SyncID column
            columnModel.getColumn(7).minWidth = 0
            columnModel.getColumn(7).maxWidth = 0
            columnModel.getColumn(7).preferredWidth = 0
        }

    private val statusLabel =
        JLabel(i18nService.translate("swing.status.ready")).apply {
            name = "statusLabel"
            toolTipText = i18nService.translate("swing.status.ready")
        }
    private val offlineBadge =
        JLabel().apply {
            name = "offlineBadge"
            isVisible = false
            foreground = COLOR_DANGER
            font = font.deriveFont(Font.BOLD, 11f)
        }
    private val statusHintLabel = JLabel().apply { name = "statusHintLabel" }
    private val statusMetaLabel = JLabel().apply { name = "statusMetaLabel" }
    private val searchLabel = JLabel().apply { name = "searchLabel" }
    private val authorLabel = JLabel().apply { name = "authorLabel" }
    private val statusBar =
        JToolBar().apply {
            name = "statusBarPanel"
            isFloatable = false
            isRollover = false
        }
    private val searchField = JTextField().apply { name = "searchField" }
    private val authorField = JTextField().apply { name = "authorField" }
    private val contentArea =
        JTextArea().apply {
            name = "contentArea"
            lineWrap = true
            wrapStyleWord = true
        }
    private val syncButton =
        JButton(i18nService.translate("swing.button.sync")).apply {
            name = "syncButton"
            icon = RemixIcon.get("system/refresh-line")
        }
    private val createButton =
        JButton(i18nService.translate("swing.button.create")).apply {
            name = "createButton"
            icon = RemixIcon.get("system/add-box-line")
            addActionListener { viewModel.createMessage {} }
        }

    private val appMenu = JMenu(i18nService.translate("swing.menu.file")).apply { name = "appMenu" }
    private val helpMenu = JMenu(i18nService.translate("swing.menu.help")).apply { name = "helpMenu" }
    private val settingsItem =
        JMenuItem(i18nService.translate("swing.menu.settings")).apply {
            name = "settingsItem"
            icon = RemixIcon.get("system/settings-3-line")
        }
    private val loginItem =
        JMenuItem(i18nService.translate("swing.auth.login")).apply {
            name = "loginItem"
            icon = RemixIcon.get("system/lock-password-line")
        }
    private val logoutItem =
        JMenuItem(i18nService.translate("swing.auth.logout.simple")).apply {
            name = "logoutItem"
            icon = RemixIcon.get("system/logout-box-r-line")
        }
    private val registerItem =
        JMenuItem(i18nService.translate("swing.auth.register")).apply {
            name = "registerItem"
            icon = RemixIcon.get("system/user-add-line")
        }
    private val newItem = JMenuItem(i18nService.translate("swing.menu.file.new")).apply { name = "newItem" }
    private val openItem = JMenuItem(i18nService.translate("swing.menu.file.open")).apply { name = "openItem" }
    private val saveItem = JMenuItem(i18nService.translate("swing.menu.file.save")).apply { name = "saveItem" }
    private val saveAsItem = JMenuItem(i18nService.translate("swing.menu.file.saveAs")).apply { name = "saveAsItem" }
    private val exitItem = JMenuItem(i18nService.translate("swing.menu.file.exit")).apply { name = "exitItem" }
    private val viewHelpItem = JMenuItem(i18nService.translate("swing.menu.help.view")).apply { name = "viewHelpItem" }
    private val sendFeedbackItem =
        JMenuItem(i18nService.translate("swing.menu.help.feedback")).apply { name = "sendFeedbackItem" }
    private val checkUpdatesItem =
        JMenuItem(i18nService.translate("swing.menu.help.updates")).apply { name = "checkUpdatesItem" }
    private val aboutItem =
        JMenuItem(i18nService.translate("swing.menu.help.about", i18nService.translate("swing.app.name"))).apply {
            name = "aboutItem"
        }

    private val dialogs =
        SyncDialogs(
            frame,
            viewModel,
            themeManager,
            i18nService,
            appVersion,
            updateUrl,
            ::refreshTranslations,
            ::saveState,
        )
    private val profilePanelCreator = SyncProfilePanel(i18nService, viewModel, frame)

    private lateinit var sidebarPanel: JPanel

    private val navMessagesBtn =
        JButton(i18nService.translate("swing.nav.messages")).apply {
            name = "navMessagesBtn"
            icon = RemixIcon.get("communication/chat-3-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
        }

    private val navContactsBtn =
        JButton(i18nService.translate("swing.contact.nav")).apply {
            name = "navContactsBtn"
            icon = RemixIcon.get("user/user-3-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
        }

    private val navUsersBtn =
        JButton(i18nService.translate("swing.admin.users.nav")).apply {
            name = "navUsersBtn"
            icon = RemixIcon.get("user/group-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
            isEnabled = false
        }

    private val navNotificationsBtn =
        JButton(i18nService.translate("swing.notifications.nav")).apply {
            name = "navNotificationsBtn"
            icon = RemixIcon.get("system/notification-3-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
            isEnabled = false
        }

    private val navProfileBtn =
        JButton(i18nService.translate("swing.profile.nav")).apply {
            name = "navProfileBtn"
            icon = RemixIcon.get("user/account-circle-line", 32)
            font = font.deriveFont(16f)
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty("JButton.buttonType", "square")
            isEnabled = false
        }

    private val changePasswordItem =
        JMenuItem(i18nService.translate("swing.password.change")).apply {
            name = "changePasswordItem"
            icon = RemixIcon.get("system/lock-password-line")
            isEnabled = false
        }

    private val usersModel =
        DefaultTableModel(
            arrayOf(
                i18nService.translate("swing.admin.users.header.username"),
                i18nService.translate("swing.admin.users.header.email"),
                i18nService.translate("swing.admin.users.header.role"),
                i18nService.translate("swing.admin.users.header.enabled"),
                i18nService.translate("swing.admin.users.header.id"),
            ),
            0,
        )
    private val usersTable =
        JTable(usersModel).apply {
            name = "usersTable"
            columnModel.getColumn(4).minWidth = 0
            columnModel.getColumn(4).maxWidth = 0
            columnModel.getColumn(4).preferredWidth = 0
        }

    private val views =
        SyncViews(
            i18nService,
            viewModel,
            SyncTableComponents(messagesModel, messagesList, contactsModel, contactsTable, usersModel, usersTable),
            SyncNavComponents(
                navMessagesBtn,
                navContactsBtn,
                navUsersBtn,
                navNotificationsBtn,
                navProfileBtn,
                syncButton,
                createButton,
            ),
            SyncEditorComponents(
                contentArea,
                searchField,
                authorField,
                searchLabel,
                authorLabel,
                offlineBadge,
                statusHintLabel,
                statusMetaLabel,
            ),
            appVersion,
            COLOR_DANGER,
            COLOR_SUCCESS,
            onNavigate = { mainLayout.show(mainCardPanel, it) },
            onShowContactFormDialog = { showContactFormDialog(it) },
        )

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
        dialogs.updateI18n(newI18n)
        profilePanelCreator.updateI18n(newI18n)
        views.updateI18n(newI18n)
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
        val state =
            DesktopState(
                windowBounds = frame.bounds,
                isMaximized = (frame.extendedState and JFrame.MAXIMIZED_BOTH) != 0,
                lastSearchQuery = viewModel.searchQuery.takeIf { it.isNotBlank() },
                themeId = DesktopTheme.entries.firstOrNull { it.label == themeName }?.name,
                language = Locale.getDefault().language,
            )
        DesktopStateProvider.saveState(state)
    }

    private fun setupBinding() {
        viewModel.addObserver { updateUI() }

        val docListener =
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    updateViewModel()
                }

                override fun removeUpdate(e: DocumentEvent?) {
                    updateViewModel()
                }

                override fun changedUpdate(e: DocumentEvent?) {
                    updateViewModel()
                }

                private fun updateViewModel() {
                    viewModel.author = authorField.text
                    viewModel.content = contentArea.text
                }
            }

        authorField.document.addDocumentListener(docListener)
        contentArea.document.addDocumentListener(docListener)

        authorField.text = viewModel.author
        contentArea.text = viewModel.content

        searchField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    viewModel.searchQuery = searchField.text
                }

                override fun removeUpdate(e: DocumentEvent?) {
                    viewModel.searchQuery = searchField.text
                }

                override fun changedUpdate(e: DocumentEvent?) {
                    viewModel.searchQuery = searchField.text
                }
            }
        )

        messagesList.addMouseListener(
            object : MouseAdapter() {
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
            }
        )
    }

    private fun updateUI() {
        messagesModel.clear()
        viewModel.messages.forEach(messagesModel::addElement)

        contactsModel.rowCount = 0
        viewModel.contacts.forEach { contact ->
            contactsModel.addRow(
                arrayOf(
                    contact.name,
                    contact.emails.joinToString(", "),
                    contact.phones.joinToString(", "),
                    contact.socialMedia.joinToString(", "),
                    contact.company,
                    contact.companyAddress,
                    contact.department,
                    contact.syncId,
                )
            )
        }

        statusLabel.text = viewModel.status
        statusLabel.toolTipText = viewModel.status
        syncButton.isEnabled = !viewModel.isSyncing && viewModel.isOnline

        val online = viewModel.isOnline
        offlineBadge.isVisible = !online
        offlineBadge.text = if (!online) i18nService.translate("swing.connectivity.offline") else ""

        if (contentArea.text != viewModel.content) {
            contentArea.text = viewModel.content
        }
        if (searchField.text != viewModel.searchQuery) {
            searchField.text = viewModel.searchQuery
        }

        loginItem.isEnabled = !viewModel.isLoggedIn
        logoutItem.isEnabled = viewModel.isLoggedIn
        registerItem.isEnabled = !viewModel.isLoggedIn
        changePasswordItem.isEnabled = viewModel.isLoggedIn

        navUsersBtn.isEnabled = viewModel.isLoggedIn && viewModel.userRole == UserRole.ADMIN.name
        navProfileBtn.isEnabled = viewModel.isLoggedIn

        usersModel.rowCount = 0
        viewModel.adminUsers.forEach { user ->
            usersModel.addRow(arrayOf(user.username, user.email, user.role.name, user.enabled.toString(), user.id))
        }
    }

    fun updateSearchField(query: String) {
        searchField.text = query
    }

    private fun configureFrame() {
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    saveState()
                    viewModel.stopAutoSync()
                }
            }
        )
        frame.minimumSize = Dimension(FRAME_WIDTH, FRAME_HEIGHT)
        frame.setLocationRelativeTo(null)
        frame.jMenuBar = createMenuBar()

        mainCardPanel = JPanel(mainLayout)
        sidebarPanel = views.createSidebar()

        messagesPanel = views.createMessagesView()
        mainCardPanel.add(messagesPanel, "MESSAGES")

        contactsPanel = views.createContactsView()
        mainCardPanel.add(contactsPanel, "CONTACTS")

        usersPanel = views.createUsersAdminView()
        mainCardPanel.add(usersPanel, "USERS")

        notificationsPanel = views.createNotificationsView()
        mainCardPanel.add(notificationsPanel, "NOTIFICATIONS")

        profilePanelCreator.setOnNavigateToMessages { mainLayout.show(mainCardPanel, "MESSAGES") }

        profilePanel = createProfileView()
        mainCardPanel.add(profilePanel, "PROFILE")

        val splitPane =
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, mainCardPanel).apply {
                dividerLocation = 140
                dividerSize = 1
                border = null
            }

        configureStatusBar()
        rootPanel =
            JPanel(BorderLayout()).apply {
                add(splitPane, BorderLayout.CENTER)
                add(statusBar, BorderLayout.SOUTH)
            }
        frame.contentPane = rootPanel
        applyTranslations()
    }

    private fun createProfileView(): JPanel = profilePanelCreator.createProfileView(navProfileBtn)

    private fun createMenuBar(): JMenuBar {
        val menuBar = JMenuBar()
        settingsItem.addActionListener { showSettingsDialog() }
        loginItem.addActionListener { showLoginDialog() }
        logoutItem.addActionListener { viewModel.logout() }
        registerItem.addActionListener { showRegisterDialog() }
        changePasswordItem.addActionListener { showChangePasswordDialog() }
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

    private fun showLoginDialog() = dialogs.showLoginDialog()

    private fun showConflictDialog(msg: MessageSummary) = dialogs.showConflictDialog(msg)

    private fun createListManageButton(label: String, list: MutableList<String>): JButton =
        dialogs.createListManageButton(label, list)

    private fun showContactFormDialog(syncId: String?) = dialogs.showContactFormDialog(syncId)

    private fun showListEditDialog(title: String, list: MutableList<String>) = dialogs.showListEditDialog(title, list)

    private fun showSettingsDialog() = dialogs.showSettingsDialog()

    private fun showRegisterDialog() = dialogs.showRegisterDialog()

    private fun showChangePasswordDialog() = dialogs.showChangePasswordDialog()

    private fun clearComposer() = dialogs.clearComposer(authorField, contentArea)

    private fun showMenuPlaceholder(key: String) = dialogs.showMenuPlaceholder(key)

    private fun showHelpDialog() = dialogs.showHelpDialog()

    private fun showAboutDialog() = dialogs.showAboutDialog()

    private fun showFeedbackDialog() = dialogs.showFeedbackDialog()

    private fun showUpdateCheckDialog() = dialogs.showUpdateCheckDialog()

    private fun showInfoDialog(title: String, message: String, icon: javax.swing.Icon?) =
        dialogs.showInfoDialog(title, message, icon)

    internal fun buildInfoDialog(title: String, message: String, icon: javax.swing.Icon?): JDialog =
        dialogs.buildInfoDialog(title, message, icon)

    private fun createThemedDialog(title: String, columns: String, rows: String): JDialog =
        dialogs.createThemedDialog(title, columns, rows)

    companion object {
        private val COLOR_DANGER = SyncDialogs.COLOR_DANGER
        private val COLOR_SUCCESS = SyncDialogs.COLOR_SUCCESS
    }

    private fun createActionRow(vararg buttons: JButton): JPanel = dialogs.createActionRow(*buttons)

    private fun showDialog(dialog: JDialog) = dialogs.showDialog(dialog)

    private fun configureStatusBar() {
        statusBar.removeAll()
        statusBar.add(statusLabel)
        statusBar.add(Box.createHorizontalGlue())
        statusBar.add(offlineBadge)
        statusBar.addSeparator(Dimension(10, 0))
        statusBar.add(statusHintLabel)
        statusBar.addSeparator(Dimension(10, 0))
        statusBar.add(statusMetaLabel)
    }

    private fun applyTranslations() {
        frame.title = "${i18nService.translate("swing.app.title")} — v$appVersion"
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
        if (statusLabel.text.isBlank()) {
            statusLabel.text = i18nService.translate("swing.status.ready")
            statusLabel.toolTipText = statusLabel.text
        }
        views.updateI18n(i18nService)
    }
}

internal fun swingRuntimeModules(): List<Module> =
    listOf(
        desktopModule,
        module {
            single {
                val cfg = get<SwingAppConfig>()
                AppConfig(jdbcUrl = cfg.jdbcUrl, jdbcUser = cfg.jdbcUser, jdbcPassword = cfg.jdbcPassword)
            }
            single<MessageCache> { NoOpMessageCache }
            single<SyncService> {
                SyncService(baseUrl = get(named("serverBaseUrl")), repository = get(), transactionManager = get())
            }
            single<SyncProvider> { get<SyncService>() }
        },
        persistenceModule,
        coreModule,
    )
