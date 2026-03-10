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
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
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
private const val defaultComposerRows = 5
private const val defaultComposerColumns = 40
private const val frameWidth = 960
private const val frameHeight = 700
private const val frameGap = 16
private const val panelGap = 8

object DesktopComponent : KoinComponent {
  val config: SwingAppConfig by inject()
  val dataSource: DataSource by inject()
  val messageService: MessageService by inject()
  val syncService: SyncService by inject()
}

fun main() {
  System.setProperty("swing.aatext", "true")
  System.setProperty("awt.useSystemAAFontSettings", "lcd")

  startKoin {
    modules(persistenceModule, coreModule, apiClientModule, desktopModule)
  }

  val desktop = DesktopComponent
  migrate(desktop.dataSource)

  val i18nService = I18nService.create("swing-messages").also { it.setLocale(Locale.getDefault()) }

    SwingUtilities.invokeLater {
    FlatLightLaf.setup()
    val notifier = SystemTrayNotifier(i18nService)
    val viewModel = SyncViewModel(desktop.messageService, desktop.syncService, i18nService, notifier)
    
    val window = SyncWindow(viewModel, ThemeManager(), i18nService)

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

    val updateService = UpdateService(
        currentVersion = desktop.config.version,
        updateUrl = desktop.config.updateUrl,
        i18nService = i18nService,
        onUpdateAvailable = { latestVersion ->
            SwingUtilities.invokeLater {
                window.showUpdateNotification(latestVersion)
            }
        }
    )

    window.show()
    updateService.checkForUpdates()
  }
}

internal class SyncWindow(
  private val viewModel: SyncViewModel,
  private val themeManager: ThemeManager,
  private var i18nService: I18nService,
) {
  val frame = JFrame(i18nService.translate("swing.app.title"))
  private val messagesModel = DefaultListModel<MessageSummary>()
  private val messagesList = JList(messagesModel).apply { name = "messagesList" }
  private val statusLabel = JLabel().apply { name = "statusLabel" }
  private val searchLabel = JLabel(i18nService.translate("swing.label.search"))
  private val authorField = JTextField().apply { name = "authorField" }
  private val searchField = JTextField().apply { name = "searchField" }
  private val contentArea = JTextArea(defaultComposerRows, defaultComposerColumns).apply { name = "contentArea" }
  private val syncButton = JButton(i18nService.translate("swing.button.sync")).apply { name = "syncButton" }
  private val createButton = JButton(i18nService.translate("swing.button.create")).apply { name = "createButton" }
  
  private val appMenu = JMenu(i18nService.translate("swing.menu.application")).apply { name = "appMenu" }
  private val themeMenu = JMenu(i18nService.translate("swing.theme.menu")).apply { name = "themeMenu" }
  private val settingsItem = JMenuItem(i18nService.translate("swing.menu.settings")).apply { name = "settingsItem" }

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
    searchLabel.text = i18nService.translate("swing.label.search")
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
        lastSearchQuery = viewModel.searchQuery.takeIf { it.isNotBlank() }
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
    val selectedIndex = messagesList.selectedIndex
    messagesModel.clear()
    viewModel.messages.forEach(messagesModel::addElement)
    if (selectedIndex >= 0 && selectedIndex < messagesModel.size) {
        messagesList.selectedIndex = selectedIndex
    }

    statusLabel.text = viewModel.status
    syncButton.isEnabled = !viewModel.isSyncing
    
    if (contentArea.text != viewModel.content) {
        contentArea.text = viewModel.content
    }

    if (searchField.text != viewModel.searchQuery) {
        searchField.text = viewModel.searchQuery
    }
  }

  fun updateSearchField(query: String) {
    searchField.text = query
  }

  fun showUpdateNotification(latestVersion: String) {
    JOptionPane.showMessageDialog(
        frame,
        i18nService.translate("swing.update.available", latestVersion),
        i18nService.translate("swing.update.title"),
        JOptionPane.INFORMATION_MESSAGE
    )
  }

  private fun configureFrame() {
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent?) {
            saveState()
        }
    })
    frame.minimumSize = Dimension(frameWidth, frameHeight)
    frame.setLocationRelativeTo(null)
    frame.layout = BorderLayout(frameGap, frameGap)
    frame.jMenuBar = createMenuBar()

    messagesList.selectionMode = ListSelectionModel.SINGLE_SELECTION
    messagesList.cellRenderer =
      object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
          list: JList<*>?,
          value: Any?,
          index: Int,
          isSelected: Boolean,
          cellHasFocus: Boolean,
        ) =
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).also {
            component ->
            val message = value as MessageSummary
            (component as JLabel).text =
              "<html><b>${message.author}</b> &mdash; ${message.updatedAtLabel()} " +
                "[${message.syncStatusLabel()}]<br/>${message.content}</html>"
          }
      }

    frame.add(createToolbar(), BorderLayout.NORTH)
    frame.add(JScrollPane(messagesList), BorderLayout.CENTER)
    frame.add(createComposer(), BorderLayout.SOUTH)
  }

  private fun createMenuBar(): JMenuBar {
    val menuBar = JMenuBar()
    
    settingsItem.addActionListener { showSettingsDialog() }
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

    val outerstellarThemes: List<ThemeDefinition> = themeManager.availableThemes().sortedBy { it.name }
    outerstellarThemes.forEach { theme: ThemeDefinition ->
        val item = JMenuItem(theme.name)
        item.addActionListener { themeManager.applyTheme(theme) }
        themeMenu.add(item)
    }

    menuBar.add(appMenu)
    menuBar.add(themeMenu)
    return menuBar
  }

  private fun showSettingsDialog() {
    val dialog = JDialog(frame, i18nService.translate("swing.settings.title"), true)
    dialog.name = "settingsDialog"
    dialog.layout = BorderLayout(10, 10)
    
    val formPanel = JPanel(java.awt.GridLayout(2, 2, 10, 10))
    formPanel.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
    
    formPanel.add(JLabel(i18nService.translate("swing.settings.language")))
    val languages = arrayOf(
        "en" to i18nService.translate("swing.language.en"),
        "fr" to i18nService.translate("swing.language.fr")
    )
    val langNames = languages.map { it.second }.toTypedArray()
    val langCombo = JComboBox<String>(langNames).apply { name = "langCombo" }
    val currentLang = if (Locale.getDefault().language == "fr") "fr" else "en"
    langCombo.selectedIndex = languages.indexOfFirst { it.first == currentLang }
    formPanel.add(langCombo)
    
    formPanel.add(JLabel(i18nService.translate("swing.settings.theme")))
    val allThemes: List<ThemeDefinition> = themeManager.availableThemes().sortedBy { it.name }
    val themeNames = allThemes.map { it.name }.toTypedArray()
    val themeCombo = JComboBox<String>(themeNames).apply { name = "themeCombo" }
    formPanel.add(themeCombo)
    
    val buttonPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT))
    val applyButton = JButton(i18nService.translate("swing.settings.button.apply")).apply { name = "applyButton" }
    val cancelButton = JButton(i18nService.translate("swing.settings.button.cancel")).apply { name = "cancelButton" }
    
    applyButton.addActionListener {
        val selectedLang = languages[langCombo.selectedIndex].first
        val newLocale = Locale(selectedLang)
        Locale.setDefault(newLocale)
        val newI18n = I18nService.create("swing-messages").also { it.setLocale(newLocale) }
        refreshTranslations(newI18n)
        
        val selectedTheme = allThemes[themeCombo.selectedIndex]
        themeManager.applyTheme(selectedTheme)
        
        dialog.dispose()
    }
    
    cancelButton.addActionListener { dialog.dispose() }
    
    buttonPanel.add(cancelButton)
    buttonPanel.add(applyButton)
    
    dialog.add(formPanel, BorderLayout.CENTER)
    dialog.add(buttonPanel, BorderLayout.SOUTH)
    dialog.pack()
    dialog.setLocationRelativeTo(frame)
    dialog.isVisible = true
  }

  private fun createToolbar(): JPanel {
    val panel = JPanel(BorderLayout(panelGap, panelGap))
    val searchPanel = JPanel(BorderLayout(panelGap, panelGap))
    searchPanel.add(searchLabel, BorderLayout.WEST)
    searchPanel.add(searchField, BorderLayout.CENTER)
    
    syncButton.addActionListener { viewModel.sync() }
    panel.add(searchPanel, BorderLayout.NORTH)
    panel.add(statusLabel, BorderLayout.CENTER)
    panel.add(syncButton, BorderLayout.EAST)
    return panel
  }

  private fun createComposer(): JPanel {
    val panel = JPanel(BorderLayout(panelGap, panelGap))
    val fieldsPanel = JPanel(BorderLayout(panelGap, panelGap))

    contentArea.lineWrap = true
    contentArea.wrapStyleWord = true

    fieldsPanel.add(authorField, BorderLayout.NORTH)
    fieldsPanel.add(JScrollPane(contentArea), BorderLayout.CENTER)

    createButton.addActionListener { 
        viewModel.createMessage { errorMessage ->
            JOptionPane.showMessageDialog(
                frame,
                errorMessage,
                i18nService.translate("swing.validation.title"),
                JOptionPane.WARNING_MESSAGE,
            )
        }
    }

    panel.add(fieldsPanel, BorderLayout.CENTER)
    panel.add(createButton, BorderLayout.EAST)
    return panel
  }
}
