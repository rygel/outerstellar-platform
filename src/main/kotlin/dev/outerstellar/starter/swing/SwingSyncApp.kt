package dev.outerstellar.starter.swing

import com.formdev.flatlaf.FlatLightLaf
import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.createDataSource
import dev.outerstellar.starter.migrate
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.sync.SyncService
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Locale
import java.util.concurrent.ExecutionException
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
import javax.swing.SwingWorker
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.SwingSyncApp")
private const val defaultComposerRows = 5
private const val defaultComposerColumns = 40
private const val frameWidth = 960
private const val frameHeight = 700
private const val frameGap = 16
private const val panelGap = 8

fun main() {
  System.setProperty("swing.aatext", "true")
  System.setProperty("awt.useSystemAAFontSettings", "lcd")

  val config = SwingAppConfig.fromEnvironment()
  val dataSource = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)
  migrate(dataSource)

  val repository = JooqMessageRepository(DSL.using(dataSource, SQLDialect.H2))
  val syncService = SyncService(repository, config.serverBaseUrl)
  val i18nService = I18nService.create("swing-messages").also { it.setLocale(Locale.getDefault()) }

  SwingUtilities.invokeLater {
    FlatLightLaf.setup()
    SyncWindow(repository, syncService, ThemeManager(), i18nService).show()
  }
}

private class SyncWindow(
  private val repository: MessageRepository,
  private val syncService: SyncService,
  private val themeManager: ThemeManager,
  private val i18nService: I18nService,
) {
  private val frame = JFrame(i18nService.translate("swing.app.title"))
  private val messagesModel = DefaultListModel<MessageSummary>()
  private val messagesList = JList(messagesModel)
  private val statusLabel = JLabel()
  private val authorField = JTextField(i18nService.translate("swing.author.default"))
  private val contentArea = JTextArea(defaultComposerRows, defaultComposerColumns)

  fun show() {
    configureFrame()
    refreshMessages(i18nService.translate("swing.status.ready"))
    frame.isVisible = true
  }

  private fun configureFrame() {
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
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
    val panel = JPanel(BorderLayout())
    val syncButton = JButton(i18nService.translate("swing.button.sync"))
    syncButton.addActionListener { syncNow(syncButton) }
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

    saveButton.addActionListener { createLocalMessage() }

    panel.add(fieldsPanel, BorderLayout.CENTER)
    panel.add(saveButton, BorderLayout.EAST)
    return panel
  }

  private fun createLocalMessage() {
    val author = authorField.text.ifBlank { i18nService.translate("swing.author.default") }
    val content = contentArea.text.trim()

    if (content.isEmpty()) {
      JOptionPane.showMessageDialog(
        frame,
        i18nService.translate("swing.validation.messageRequired"),
        i18nService.translate("swing.validation.title"),
        JOptionPane.WARNING_MESSAGE,
      )
      return
    }

    repository.createLocalMessage(author, content)
    contentArea.text = ""
    refreshMessages(i18nService.translate("swing.status.created"))
  }

  private fun syncNow(syncButton: JButton) {
    syncButton.isEnabled = false
    statusLabel.text = i18nService.translate("swing.status.syncing")

    object : SwingWorker<String, Unit>() {
        override fun doInBackground(): String {
          val stats = syncService.sync()
          return i18nService.translate(
            "swing.status.complete",
            stats.pushedCount,
            stats.pulledCount,
            stats.conflictCount,
          )
        }

        override fun done() {
          syncButton.isEnabled = true
          try {
            refreshMessages(get())
          } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warn("Sync was interrupted.", exception)
            statusLabel.text = i18nService.translate("swing.status.interrupted")
          } catch (exception: ExecutionException) {
            logger.error("Sync failed.", exception.cause)
            statusLabel.text =
              i18nService.translate(
                "swing.status.failed",
                exception.cause?.message ?: exception.message ?: "unknown error",
              )
          }
        }
      }
      .execute()
  }

  private fun refreshMessages(status: String) {
    messagesModel.clear()
    repository.listMessages().forEach(messagesModel::addElement)
    statusLabel.text =
      i18nService.translate(
        "swing.status.summary",
        status,
        messagesModel.size,
        repository.listDirtyMessages().size,
      )
  }
}
