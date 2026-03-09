package dev.outerstellar.starter.swing

import com.formdev.flatlaf.FlatLightLaf
import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.di.apiClientModule
import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.desktopModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.model.MessageSummary
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
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
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
    SyncWindow(viewModel, ThemeManager(), i18nService).show()
  }
}

private class SyncWindow(
  private val viewModel: SyncViewModel,
  private val themeManager: ThemeManager,
  private val i18nService: I18nService,
) {
  private val frame = JFrame(i18nService.translate("swing.app.title"))
  private val messagesModel = DefaultListModel<MessageSummary>()
  private val messagesList = JList(messagesModel)
  private val statusLabel = JLabel()
  private val authorField = JTextField()
  private val searchField = JTextField()
  private val contentArea = JTextArea(defaultComposerRows, defaultComposerColumns)
  private val syncButton = JButton(i18nService.translate("swing.button.sync"))

  fun show() {
    configureFrame()
    setupBinding()
    restoreState()
    viewModel.loadMessages()
    frame.isVisible = true
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
    val themeMenu = JMenu(i18nService.translate("swing.theme.menu"))
    val lightItem = JMenuItem(i18nService.translate("swing.theme.light"))
    val darkItem = JMenuItem(i18nService.translate("swing.theme.dark"))
    val outerstellarItem = JMenuItem(i18nService.translate("swing.theme.outerstellar"))

    lightItem.addActionListener { themeManager.setLightTheme() }
    darkItem.addActionListener { themeManager.setDarkTheme() }
    outerstellarItem.addActionListener { themeManager.setOuterstellarTheme() }

    themeMenu.add(lightItem)
    themeMenu.add(darkItem)
    themeMenu.add(outerstellarItem)
    menuBar.add(themeMenu)
    return menuBar
  }

  private fun createToolbar(): JPanel {
    val panel = JPanel(BorderLayout(panelGap, panelGap))
    val searchPanel = JPanel(BorderLayout(panelGap, panelGap))
    searchPanel.add(JLabel(i18nService.translate("swing.label.search")), BorderLayout.WEST)
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
    val saveButton = JButton(i18nService.translate("swing.button.create"))

    contentArea.lineWrap = true
    contentArea.wrapStyleWord = true

    fieldsPanel.add(authorField, BorderLayout.NORTH)
    fieldsPanel.add(JScrollPane(contentArea), BorderLayout.CENTER)

    saveButton.addActionListener { 
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
    panel.add(saveButton, BorderLayout.EAST)
    return panel
  }
}
