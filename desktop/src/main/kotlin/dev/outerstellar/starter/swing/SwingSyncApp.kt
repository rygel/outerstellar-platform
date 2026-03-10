package dev.outerstellar.starter.swing

import com.formdev.flatlaf.FlatLightLaf
import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.di.apiClientModule
import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.desktopModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.ThemeDefinition
import dev.outerstellar.starter.model.ThemeCatalog
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.swing.DesktopState
import dev.outerstellar.starter.swing.DesktopStateProvider
import dev.outerstellar.starter.swing.SystemTrayNotifier
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Locale
import javax.sql.DataSource
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.SwingSyncApp")
private const val frameWidth = 1000
private const val frameHeight = 750

object DesktopComponent : KoinComponent {
  val config: SwingAppConfig by inject()
  val dataSource: DataSource by inject()
  val messageService: MessageService by inject()
  val syncService: SyncService by inject()
}

fun main() {
  System.setProperty("swing.aatext", "true")
  System.setProperty("awt.useSystemAAFontSettings", "lcd")

  // Show Programmatic Splash Screen
  val splash = showSplash()

  startKoin {
    modules(persistenceModule, coreModule, apiClientModule, desktopModule)
  }

  val desktop = DesktopComponent
  migrate(desktop.dataSource)

  val savedState = DesktopStateProvider.loadState()
  val initialLocale = savedState?.language?.let { Locale(it) } ?: Locale.getDefault()
  Locale.setDefault(initialLocale)

  val i18nService = I18nService.create("messages").also { it.setLocale(initialLocale) }

    SwingUtilities.invokeLater {
    FlatLightLaf.setup()
    
    val themeManager = ThemeManager()
    savedState?.themeId?.let { themeId ->
        ThemeCatalog.allThemes().find { it.id == themeId }?.let { theme ->
            themeManager.applyTheme(theme)
        }
    }

    val notifier = SystemTrayNotifier(i18nService)
    val viewModel = SyncViewModel(desktop.messageService, desktop.syncService, i18nService, notifier)
    val window = SyncWindow(viewModel, themeManager, i18nService)

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
    splash.dispose() // Hide splash once main window is ready
  }
}

class SyncWindow(
  private val viewModel: SyncViewModel,
  private val themeManager: ThemeManager,
  private var i18nService: I18nService,
) {
  val frame = JFrame(i18nService.translate("swing.app.title"))
  private val messagesModel = DefaultListModel<MessageSummary>()
  private val messagesList = JList(messagesModel).apply { name = "messagesList" }
  private val statusLabel = JLabel().apply { name = "statusLabel" }
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
  }
  
  private val appMenu = JMenu(i18nService.translate("swing.menu.application")).apply { name = "appMenu" }
  private val themeMenu = JMenu(i18nService.translate("swing.theme.menu")).apply { 
      name = "themeMenu"
      icon = RemixIcon.get("others/palette-line")
  }
  private val settingsItem = JMenuItem(i18nService.translate("swing.menu.settings")).apply { 
      name = "settingsItem"
      icon = RemixIcon.get("system/settings-3-line")
  }
  private val loginItem = JMenuItem("Login").apply {
      name = "loginItem"
      icon = RemixIcon.get("system/lock-password-line")
  }

  fun show() {
    configureFrame()
    setupBinding()
    restoreState()
    viewModel.loadMessages()
    frame.isVisible = true
  }

  fun refreshTranslations(newI18n: I18nService) {
    this.i18nService = newI18n
    frame.title = i18nService.translate("swing.app.title")
    syncButton.text = i18nService.translate("swing.button.sync")
    createButton.text = i18nService.translate("swing.button.create")
    appMenu.text = i18nService.translate("swing.menu.application")
    themeMenu.text = i18nService.translate("swing.theme.menu")
    settingsItem.text = i18nService.translate("swing.menu.settings")
    
    viewModel.refreshTranslations(newI18n)
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
    val state = DesktopState(
        windowBounds = frame.bounds,
        isMaximized = (frame.extendedState and JFrame.MAXIMIZED_BOTH) != 0,
        lastSearchQuery = viewModel.searchQuery.takeIf { it.isNotBlank() },
        themeId = ThemeCatalog.allThemes().find { it.name == (UIManager.get("current_theme_name") as? String) }?.id,
        language = Locale.getDefault().language
    )
    DesktopStateProvider.saveState(state)
  }

  private fun setupBinding() {
    viewModel.addObserver {
      updateUI()
    }

    authorField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) { viewModel.author = authorField.text }
      override fun removeUpdate(e: DocumentEvent?) { viewModel.author = authorField.text }
      override fun changedUpdate(e: DocumentEvent?) { viewModel.author = authorField.text }
    })

    contentArea.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) { viewModel.content = contentArea.text }
      override fun removeUpdate(e: DocumentEvent?) { viewModel.content = contentArea.text }
      override fun changedUpdate(e: DocumentEvent?) { viewModel.content = contentArea.text }
    })
    
    authorField.text = viewModel.author
    contentArea.text = viewModel.content
    
    searchField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) { viewModel.searchQuery = searchField.text }
      override fun removeUpdate(e: DocumentEvent?) { viewModel.searchQuery = searchField.text }
      override fun changedUpdate(e: DocumentEvent?) { viewModel.searchQuery = searchField.text }
    })
  }

  private fun updateUI() {
    messagesModel.clear()
    viewModel.messages.forEach(messagesModel::addElement)
    statusLabel.text = viewModel.status
    syncButton.isEnabled = !viewModel.isSyncing
    
    if (contentArea.text != viewModel.content) {
        contentArea.text = viewModel.content
    }
    if (searchField.text != viewModel.searchQuery) {
        searchField.text = viewModel.searchQuery
    }
    
    loginItem.text = if (viewModel.isLoggedIn) "Logout (${viewModel.userName})" else "Login"
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
    frame.minimumSize = Dimension(frameWidth, frameHeight)
    frame.setLocationRelativeTo(null)
    frame.jMenuBar = createMenuBar()

    // --- Main Layout with MigLayout ---
    val mainPanel = JPanel(MigLayout("fill, ins 20, gap 15", "[grow]", "[][grow][]"))
    
    // 1. Search Bar
    val searchPanel = JPanel(MigLayout("fillx, ins 0", "[][grow][]", "[]"))
    searchPanel.add(JLabel(i18nService.translate("swing.label.search")))
    searchPanel.add(searchField, "growx")
    searchPanel.add(syncButton, "w 120!")
    mainPanel.add(searchPanel, "growx, wrap")

    // 2. Message List
    messagesList.cellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean) =
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).also { c ->
            val msg = value as MessageSummary
            (c as JLabel).text = "<html><b>${msg.author}</b> &mdash; ${msg.updatedAtLabel()}<br/>${msg.content}</html>"
          }
    }
    mainPanel.add(JScrollPane(messagesList), "grow, wrap")

    // 3. Composer & Status
    val footerPanel = JPanel(MigLayout("fillx, ins 0", "[grow][]", "[][]"))
    footerPanel.add(JLabel("Author:"), "split 2")
    footerPanel.add(authorField, "growx, wrap")
    footerPanel.add(JScrollPane(contentArea), "grow, h 80!, span, wrap")
    footerPanel.add(statusLabel, "growx")
    footerPanel.add(createButton, "w 180!")
    mainPanel.add(footerPanel, "growx")

    frame.contentPane = mainPanel
  }

  private fun createMenuBar(): JMenuBar {
    val menuBar = JMenuBar()
    settingsItem.addActionListener { showSettingsDialog() }
    loginItem.addActionListener { 
        if (viewModel.isLoggedIn) viewModel.logout()
        else showLoginDialog() 
    }
    
    appMenu.add(loginItem)
    appMenu.addSeparator()
    appMenu.add(settingsItem)
    
    val standardThemes = JMenu("Standard")
    val lightItem = JMenuItem(i18nService.translate("swing.theme.light"))
    val darkItem = JMenuItem(i18nService.translate("swing.theme.dark"))
    lightItem.addActionListener { themeManager.setLightTheme() }
    darkItem.addActionListener { themeManager.setDarkTheme() }
    standardThemes.add(lightItem)
    standardThemes.add(darkItem)
    themeMenu.add(standardThemes)
    themeMenu.addSeparator()

    val outerstellarThemes: List<ThemeDefinition> = ThemeCatalog.allThemes().sortedBy { it.name }
    outerstellarThemes.forEach { theme ->
        val item = JMenuItem(theme.name)
        item.addActionListener { 
            themeManager.applyTheme(theme)
            saveState()
        }
        themeMenu.add(item)
    }

    menuBar.add(appMenu)
    menuBar.add(themeMenu)
    return menuBar
  }

  private fun showLoginDialog() {
    val dialog = JDialog(frame, "Login", true)
    dialog.layout = MigLayout("fillx, ins 20, gap 10", "[][grow]", "[][][]")
    
    dialog.add(JLabel("Username:"))
    val userField = JTextField().apply { name = "username" }
    dialog.add(userField, "growx, wrap")
    
    dialog.add(JLabel("Password:"))
    val passField = JPasswordField().apply { name = "password" }
    dialog.add(passField, "growx, wrap")
    
    val loginBtn = JButton("Sign In").apply { name = "loginBtn" }
    loginBtn.addActionListener {
        viewModel.login(userField.text, String(passField.password)) { success, error ->
            if (success) dialog.dispose()
            else JOptionPane.showMessageDialog(dialog, error, "Login Failed", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    dialog.add(loginBtn, "span, center")
    dialog.pack()
    dialog.setLocationRelativeTo(frame)
    dialog.isVisible = true
  }

  private fun showSettingsDialog() {
    val dialog = JDialog(frame, i18nService.translate("swing.settings.title"), true)
    dialog.name = "settingsDialog"
    dialog.layout = MigLayout("fillx, ins 20, gap 10", "[][grow]", "[][][]")
    
    dialog.add(JLabel(i18nService.translate("swing.settings.language")))
    val languages = arrayOf("en" to i18nService.translate("swing.language.en"), "fr" to i18nService.translate("swing.language.fr"))
    val langCombo = JComboBox<String>(languages.map { it.second }.toTypedArray()).apply { name = "langCombo" }
    langCombo.selectedIndex = languages.indexOfFirst { it.first == Locale.getDefault().language }.coerceAtLeast(0)
    dialog.add(langCombo, "growx, wrap")
    
    dialog.add(JLabel(i18nService.translate("swing.settings.theme")))
    val allThemes = ThemeCatalog.allThemes().sortedBy { it.name }
    val themeCombo = JComboBox<String>(allThemes.map { it.name }.toTypedArray()).apply { name = "themeCombo" }
    val currentThemeName = UIManager.get("current_theme_name") as? String
    themeCombo.selectedIndex = allThemes.indexOfFirst { it.name == currentThemeName }.coerceAtLeast(0)
    dialog.add(themeCombo, "growx, wrap")
    
    val applyButton = JButton(i18nService.translate("swing.settings.button.apply")).apply { name = "applyButton" }
    val cancelButton = JButton(i18nService.translate("swing.settings.button.cancel")).apply { name = "cancelButton" }
    
    applyButton.addActionListener {
        val selectedLang = languages[langCombo.selectedIndex].first
        val newLocale = Locale(selectedLang)
        Locale.setDefault(newLocale)
        refreshTranslations(I18nService.create("messages").also { it.setLocale(newLocale) })
        themeManager.applyTheme(allThemes[themeCombo.selectedIndex])
        saveState()
        dialog.dispose()
    }
    cancelButton.addActionListener { dialog.dispose() }
    
    dialog.add(cancelButton, "tag cancel, span, split 2, right")
    dialog.add(applyButton, "tag ok")
    
    dialog.pack()
    dialog.setLocationRelativeTo(frame)
    dialog.isVisible = true
  }
}

private fun showSplash(): JWindow {
    val window = JWindow()
    val content = JPanel(BorderLayout(20, 20))
    content.border = BorderFactory.createLineBorder(Color.GRAY)
    content.background = Color.WHITE
    
    val label = JLabel("Outerstellar", RemixIcon.get("system/planet-fill", 64), SwingConstants.CENTER)
    label.font = Font("Inter", Font.BOLD, 28)
    label.verticalTextPosition = SwingConstants.BOTTOM
    label.horizontalTextPosition = SwingConstants.CENTER
    
    val status = JLabel("Starting application...", SwingConstants.CENTER)
    status.font = Font("Inter", Font.PLAIN, 14)
    status.foreground = Color.DARK_GRAY
    
    content.add(label, BorderLayout.CENTER)
    content.add(status, BorderLayout.SOUTH)
    
    window.contentPane = content
    window.size = Dimension(400, 300)
    window.setLocationRelativeTo(null)
    window.isVisible = true
    return window
}
