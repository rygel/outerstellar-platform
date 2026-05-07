package io.github.rygel.outerstellar.platform.swing

import com.formdev.flatlaf.FlatLightLaf
import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.desktopModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.ThemeCatalog
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.NoOpMessageCache
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.SyncProvider
import io.github.rygel.outerstellar.platform.swing.analytics.PersistentBatchingAnalyticsService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
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
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
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
import net.miginfocom.swing.MigLayout
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val FRAME_WIDTH = 1000
private const val FRAME_HEIGHT = 750
private const val CONFLICT_ICON_SIZE = 16
private const val DIALOG_WIDTH = 600
private const val DIALOG_HEIGHT = 400

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
        ConnectivityChecker(healthUrl = "${desktop.config.serverBaseUrl}/health").also { it.start() }

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
    FlatLightLaf.setup()

    val themeManager = ThemeManager()
    val startupTheme =
        savedState?.themeId?.let { themeId -> ThemeCatalog.allThemes().find { it.id == themeId } }
            ?: ThemeCatalog.findTheme("default")
    themeManager.applyTheme(startupTheme)

    val notifier = SystemTrayNotifier(i18nService)
    val viewModel =
        SyncViewModel(
            DesktopComponent.messageService,
            DesktopComponent.contactService,
            DesktopComponent.syncService,
            i18nService,
            notifier,
            analytics ?: NoOpAnalyticsService(),
            connectivityChecker,
        )
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

    private lateinit var searchPanel: JPanel
    private lateinit var footerPanel: JPanel
    private lateinit var messagesScrollPane: JScrollPane
    private lateinit var contentScrollPane: JScrollPane
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
        val state =
            DesktopState(
                windowBounds = frame.bounds,
                isMaximized = (frame.extendedState and JFrame.MAXIMIZED_BOTH) != 0,
                lastSearchQuery = viewModel.searchQuery.takeIf { it.isNotBlank() },
                themeId = ThemeCatalog.allThemes().find { it.name == themeName }?.id,
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

        navUsersBtn.isEnabled = viewModel.isLoggedIn && viewModel.userRole == "ADMIN"
        navProfileBtn.isEnabled = viewModel.isLoggedIn

        usersModel.rowCount = 0
        viewModel.adminUsers.forEach { user ->
            usersModel.addRow(arrayOf(user.username, user.email, user.role, user.enabled.toString(), user.id))
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
                    viewModel.connectivityChecker?.stop()
                }
            }
        )
        frame.minimumSize = Dimension(FRAME_WIDTH, FRAME_HEIGHT)
        frame.setLocationRelativeTo(null)
        frame.jMenuBar = createMenuBar()

        mainCardPanel = JPanel(mainLayout)
        sidebarPanel = createSidebar()

        messagesPanel = createMessagesView()
        mainCardPanel.add(messagesPanel, "MESSAGES")

        contactsPanel = createContactsView()
        mainCardPanel.add(contactsPanel, "CONTACTS")

        usersPanel = createUsersAdminView()
        mainCardPanel.add(usersPanel, "USERS")

        notificationsPanel = createNotificationsView()
        mainCardPanel.add(notificationsPanel, "NOTIFICATIONS")

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

    private fun createSidebar(): JPanel {
        val panel =
            JPanel(MigLayout("fillx, ins 10, gap 10", "[grow]", "[]10[]10[grow]")).apply { name = "sidebarPanel" }

        navMessagesBtn.addActionListener { mainLayout.show(mainCardPanel, "MESSAGES") }
        navContactsBtn.addActionListener { mainLayout.show(mainCardPanel, "CONTACTS") }
        navUsersBtn.addActionListener {
            viewModel.loadUsers()
            mainLayout.show(mainCardPanel, "USERS")
        }
        navNotificationsBtn.addActionListener {
            viewModel.loadNotifications()
            mainLayout.show(mainCardPanel, "NOTIFICATIONS")
        }
        navProfileBtn.addActionListener {
            mainLayout.show(mainCardPanel, "PROFILE")
            viewModel.loadProfile { _, _ -> }
        }

        panel.add(navMessagesBtn, "growx, h 100!, wrap")
        panel.add(navContactsBtn, "growx, h 100!, wrap")
        panel.add(navUsersBtn, "growx, h 100!, wrap")
        panel.add(navNotificationsBtn, "growx, h 100!, wrap")
        panel.add(navProfileBtn, "growx, h 100!, wrap")
        panel.add(Box.createVerticalGlue(), "growy")

        return panel
    }

    private fun createMessagesView(): JPanel {
        val panel = JPanel(MigLayout("fill, ins 20, gap 15", "[grow]", "[][grow]"))

        searchPanel = JPanel(MigLayout("fillx, ins 0", "[][grow][]", "[]"))
        searchPanel.add(searchLabel)
        searchPanel.add(searchField, "growx")
        searchPanel.add(syncButton, "w 120!")
        panel.add(searchPanel, "growx, wrap")

        messagesList.cellRenderer =
            object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: javax.swing.JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean,
                ) =
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).also { c ->
                        val msg = value as MessageSummary
                        val dangerColor = (UIManager.getColor("Theme.danger") ?: Color.RED).toHtml()
                        val accentColor = (UIManager.getColor("Theme.accent") ?: Color.BLUE).toHtml()
                        val conflictMarker =
                            if (msg.hasConflict) " <font color='$dangerColor'>[CONFLICT]</font>" else ""
                        val localMarker = if (msg.dirty) " <font color='$accentColor'>(Local)</font>" else ""
                        (c as JLabel).text =
                            "<html><b>${msg.author}</b>$localMarker$conflictMarker &mdash; " +
                                "${msg.updatedAtLabel()}<br/>${msg.content}</html>"
                        if (msg.hasConflict) {
                            c.icon = RemixIcon.get("system/error-warning-line", CONFLICT_ICON_SIZE)
                        } else {
                            c.icon = null
                        }
                    }
            }
        messagesScrollPane = JScrollPane(messagesList)
        panel.add(messagesScrollPane, "grow, wrap")

        footerPanel = JPanel(MigLayout("fillx, ins 0", "[grow][]", "[][]"))
        footerPanel.add(authorLabel, "split 2")
        footerPanel.add(authorField, "growx, wrap")
        contentScrollPane = JScrollPane(contentArea)
        footerPanel.add(contentScrollPane, "grow, h 80!, span, wrap")
        footerPanel.add(createButton, "w 180!")
        panel.add(footerPanel, "growx")

        return panel
    }

    private fun createContactsView(): JPanel {
        val panel = JPanel(MigLayout("fill, ins 20", "[grow]", "[][grow]"))

        val contactsHeaderPanel = JPanel(MigLayout("fillx, ins 0", "[grow][]", "[]"))
        contactsHeaderPanel.add(JLabel("Contacts Directory").apply { font = font.deriveFont(Font.BOLD, 18f) })

        val createContactBtn =
            JButton("Create Contact").apply {
                icon = RemixIcon.get("system/add-box-line")
                addActionListener { showContactFormDialog(null) }
            }
        contactsHeaderPanel.add(createContactBtn, "right")

        panel.add(contactsHeaderPanel, "wrap, growx, gapbottom 10")
        panel.add(JScrollPane(contactsTable), "grow")

        contactsTable.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val row = contactsTable.rowAtPoint(e.point)
                        if (row >= 0) {
                            val syncId = contactsModel.getValueAt(row, 7).toString()
                            showContactFormDialog(syncId)
                        }
                    }
                }
            }
        )

        return panel
    }

    private fun createUsersAdminView(): JPanel {
        val panel = JPanel(MigLayout("fill, ins 20", "[grow]", "[][grow][]"))

        val usersHeaderPanel = JPanel(MigLayout("fillx, ins 0", "[grow]", "[]"))
        usersHeaderPanel.add(
            JLabel(i18nService.translate("swing.admin.users.title")).apply { font = font.deriveFont(Font.BOLD, 18f) }
        )
        panel.add(usersHeaderPanel, "wrap, growx, gapbottom 10")
        panel.add(JScrollPane(usersTable), "grow, wrap")

        val userActionsPanel = JPanel(MigLayout("ins 0, fillx", "[][]", "[]"))
        val toggleEnabledBtn =
            JButton(i18nService.translate("swing.admin.toggle.enabled")).apply {
                addActionListener {
                    val row = usersTable.selectedRow
                    if (row >= 0) {
                        val userId = usersModel.getValueAt(row, 4).toString()
                        val enabled = usersModel.getValueAt(row, 3).toString() == "true"
                        viewModel.toggleUserEnabled(userId, enabled)
                    }
                }
            }
        val toggleRoleBtn =
            JButton(i18nService.translate("swing.admin.toggle.role")).apply {
                addActionListener {
                    val row = usersTable.selectedRow
                    if (row >= 0) {
                        val userId = usersModel.getValueAt(row, 4).toString()
                        val role = usersModel.getValueAt(row, 2).toString()
                        viewModel.toggleUserRole(userId, role)
                    }
                }
            }
        userActionsPanel.add(toggleEnabledBtn)
        userActionsPanel.add(toggleRoleBtn)
        panel.add(userActionsPanel, "growx")

        return panel
    }

    private fun createNotificationsView(): JPanel {
        val panel = JPanel(MigLayout("fill, ins 20", "[grow]", "[][grow][]"))

        val notifHeaderPanel = JPanel(MigLayout("fillx, ins 0", "[grow][]", "[]"))
        notifHeaderPanel.add(
            JLabel(i18nService.translate("swing.notifications.title")).apply {
                font = font.deriveFont(Font.BOLD, 18f)
                name = "notifTitleLabel"
            }
        )
        val markAllReadBtn =
            JButton(i18nService.translate("swing.notifications.mark.all.read")).apply {
                name = "markAllReadBtn"
                addActionListener { viewModel.markAllNotificationsRead() }
            }
        notifHeaderPanel.add(markAllReadBtn, "right")
        panel.add(notifHeaderPanel, "wrap, growx, gapbottom 10")

        val notifListModel =
            javax.swing.DefaultListModel<io.github.rygel.outerstellar.platform.model.NotificationSummary>()
        val notifList =
            javax.swing.JList(notifListModel).apply {
                name = "notificationsList"
                cellRenderer =
                    object : javax.swing.DefaultListCellRenderer() {
                        override fun getListCellRendererComponent(
                            list: javax.swing.JList<*>?,
                            value: Any?,
                            index: Int,
                            isSelected: Boolean,
                            cellHasFocus: Boolean,
                        ): java.awt.Component {
                            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                            val n =
                                value as? io.github.rygel.outerstellar.platform.model.NotificationSummary ?: return this
                            val unreadMark = if (!n.read) "● " else "  "
                            text =
                                "<html><b>$unreadMark${n.title}</b><br/><span style='font-size:10px'>${n.body}</span></html>"
                            return this
                        }
                    }
            }
        panel.add(JScrollPane(notifList), "grow, wrap")

        val notifActionsPanel = JPanel(MigLayout("ins 0, fillx", "[]", "[]"))
        val markReadBtn =
            JButton(i18nService.translate("swing.notifications.mark.read")).apply {
                name = "markReadBtn"
                addActionListener {
                    val idx = notifList.selectedIndex
                    if (idx >= 0) {
                        val notifId = notifListModel.getElementAt(idx).id
                        viewModel.markNotificationRead(notifId)
                    }
                }
            }
        notifActionsPanel.add(markReadBtn)
        panel.add(notifActionsPanel, "growx")

        viewModel.addObserver {
            javax.swing.SwingUtilities.invokeLater {
                val unread = viewModel.unreadNotificationCount
                navNotificationsBtn.text =
                    if (unread > 0) i18nService.translate("swing.notifications.nav.unread", unread.toString())
                    else i18nService.translate("swing.notifications.nav")
                navNotificationsBtn.isEnabled = viewModel.isLoggedIn

                notifListModel.clear()
                viewModel.notifications.forEach { notifListModel.addElement(it) }
                if (viewModel.notifications.isEmpty()) {
                    markAllReadBtn.isEnabled = false
                }
            }
        }

        return panel
    }

    private fun createProfileView(): JPanel {
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

    private fun createProfileInfoPanel(): Triple<JPanel, Array<JTextField>, JLabel> {
        val panel = JPanel(MigLayout("fillx, ins 10, gap 8", "[120!][grow]", "[][][][] "))
        panel.border = javax.swing.BorderFactory.createTitledBorder(i18nService.translate("swing.profile.section.info"))

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

    private fun createNotifPrefsPanel(): Triple<JPanel, JCheckBox, JCheckBox> {
        val panel = JPanel(MigLayout("fillx, ins 10, gap 8", "[grow]", "[][]"))
        panel.border =
            javax.swing.BorderFactory.createTitledBorder(i18nService.translate("swing.profile.section.notifications"))

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

    private fun createDangerPanel(): JPanel {
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
                        mainLayout.show(mainCardPanel, "MESSAGES")
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
                        JOptionPane.ERROR_MESSAGE,
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

        val introText =
            "<html>A sync conflict was detected for this message.<br/>" + "Please choose which version to keep:</html>"
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
            "grow, h 100!",
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

    private fun createListManageButton(label: String, list: MutableList<String>): JButton =
        JButton("Manage $label (${list.size})").apply {
            addActionListener {
                showListEditDialog(label, list)
                text = "Manage $label (${list.size})"
            }
        }

    private fun showContactFormDialog(syncId: String?) {
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

    private fun showListEditDialog(title: String, list: MutableList<String>) {
        val dialog = JDialog(frame, "Manage $title", true)
        dialog.layout = MigLayout("fill, ins 20, gap 10", "[grow][]", "[][grow][]")

        val listModel = DefaultListModel<String>()
        list.forEach { listModel.addElement(it) }
        val jList = JList(listModel)

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

    private fun showSettingsDialog() {
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
        val allThemes = ThemeCatalog.allThemes().sortedBy { it.name }
        val themeCombo = JComboBox<String>(allThemes.map { it.name }.toTypedArray()).apply { name = "themeCombo" }
        val currentThemeName = UIManager.get("current_theme_name") as? String
        themeCombo.selectedIndex = allThemes.indexOfFirst { it.name == currentThemeName }.coerceAtLeast(0)
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

        fun updatePreview(theme: io.github.rygel.outerstellar.platform.model.ThemeDefinition) {
            val colors = theme.colors
            val bg = themeManager.decodeColor(colors["background"]) ?: Color.WHITE
            val fg = themeManager.decodeColor(colors["foreground"]) ?: Color.BLACK
            val compBg = themeManager.decodeColor(colors["componentBackground"]) ?: Color.WHITE
            val accent = themeManager.decodeColor(colors["accent"]) ?: Color.BLUE
            val border = themeManager.decodeColor(colors["borderColor"]) ?: Color.GRAY

            previewPanel.background = bg
            previewPanel.border =
                BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(border),
                    "Theme Preview: ${theme.name}",
                    TitledBorder.DEFAULT_JUSTIFICATION,
                    TitledBorder.DEFAULT_POSITION,
                    null,
                    fg,
                )
            sampleLabel.foreground = fg
            sampleField.background = compBg
            sampleField.foreground = fg
            sampleButton.background = accent
            sampleButton.foreground = fg
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

    private fun showChangePasswordDialog() {
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

    private fun clearComposer() {
        authorField.text = i18nService.translate("swing.author.default")
        contentArea.text = ""
    }

    private fun showMenuPlaceholder(key: String) {
        JOptionPane.showMessageDialog(
            frame,
            i18nService.translate("swing.menu.placeholder", i18nService.translate(key)),
            i18nService.translate("swing.menu.placeholder.title"),
            JOptionPane.INFORMATION_MESSAGE,
        )
    }

    private fun showHelpDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.view"),
            message = i18nService.translate("swing.help.message"),
            icon = RemixIcon.get("system/question-line", 24),
        )
    }

    private fun showAboutDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.about", i18nService.translate("swing.app.name")),
            message = i18nService.translate("swing.about.message", i18nService.translate("swing.app.name"), appVersion),
            icon = RemixIcon.get("system/information-line", 24),
        )
    }

    private fun showFeedbackDialog() {
        showInfoDialog(
            title = i18nService.translate("swing.menu.help.feedback"),
            message = i18nService.translate("swing.feedback.message"),
            icon = RemixIcon.get("communication/chat-smile-3-line", 24),
        )
    }

    private fun showUpdateCheckDialog() {
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
                // Show "checking" feedback immediately, update finishes asynchronously
                showInfoDialog(
                    title = i18nService.translate("swing.menu.help.updates"),
                    message = i18nService.translate("swing.updates.checking"),
                    icon = RemixIcon.get("system/refresh-line", 24),
                )
                svc.checkForUpdates()
            }
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

    private fun createThemedDialog(title: String, columns: String, rows: String): JDialog =
        JDialog(frame, title, true).apply {
            layout = MigLayout("fill, ins 24, gap 10", columns, rows)
            minimumSize = java.awt.Dimension(MIN_DIALOG_WIDTH, MIN_DIALOG_HEIGHT)
        }

    companion object {
        private const val MIN_DIALOG_WIDTH = 420
        private const val MIN_DIALOG_HEIGHT = 250
        private val COLOR_DANGER = Color(0xCC, 0x44, 0x44)
        private val COLOR_SUCCESS = Color(0x22, 0x77, 0x22)
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
        statusBar.add(offlineBadge)
        statusBar.addSeparator(Dimension(10, 0))
        statusBar.add(statusHintLabel)
        statusBar.addSeparator(Dimension(10, 0))
        statusBar.add(statusMetaLabel)
    }

    private fun applyTranslations() {
        frame.title = "${i18nService.translate("swing.app.title")} — v$appVersion"
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
        changePasswordItem.text = i18nService.translate("swing.password.change")
        navMessagesBtn.text = i18nService.translate("swing.nav.messages")
        navContactsBtn.text = i18nService.translate("swing.contact.nav")
        navUsersBtn.text = i18nService.translate("swing.admin.users.nav")
        navNotificationsBtn.text = i18nService.translate("swing.notifications.nav")
        navProfileBtn.text = i18nService.translate("swing.profile.nav")
        contactsModel.setColumnIdentifiers(
            arrayOf(
                i18nService.translate("swing.contact.table.name"),
                i18nService.translate("swing.contact.table.emails"),
                i18nService.translate("swing.contact.table.phones"),
                i18nService.translate("swing.contact.table.socials"),
                i18nService.translate("swing.contact.table.company"),
                i18nService.translate("swing.contact.table.company.address"),
                i18nService.translate("swing.contact.table.department"),
                "SyncID",
            )
        )
        usersModel.setColumnIdentifiers(
            arrayOf(
                i18nService.translate("swing.admin.users.header.username"),
                i18nService.translate("swing.admin.users.header.email"),
                i18nService.translate("swing.admin.users.header.role"),
                i18nService.translate("swing.admin.users.header.enabled"),
                i18nService.translate("swing.admin.users.header.id"),
            )
        )
        statusHintLabel.text = ""
        statusMetaLabel.text = i18nService.translate("swing.statusbar.version", appVersion)
        if (statusLabel.text.isBlank()) {
            statusLabel.text = i18nService.translate("swing.status.ready")
            statusLabel.toolTipText = statusLabel.text
        }
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
