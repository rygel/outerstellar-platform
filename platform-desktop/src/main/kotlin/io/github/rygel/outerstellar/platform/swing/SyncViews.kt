package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.Box
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.table.DefaultTableModel
import net.miginfocom.swing.MigLayout

private const val CONFLICT_ICON_SIZE = 16

private fun Color.toHtml() = String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue)

internal class SyncTableComponents(
    val messagesModel: DefaultListModel<MessageSummary>,
    val messagesList: JList<MessageSummary>,
    val contactsModel: DefaultTableModel,
    val contactsTable: JTable,
    val usersModel: DefaultTableModel,
    val usersTable: JTable,
)

internal class SyncNavComponents(
    val navMessagesBtn: JButton,
    val navContactsBtn: JButton,
    val navUsersBtn: JButton,
    val navNotificationsBtn: JButton,
    val navProfileBtn: JButton,
    val syncButton: JButton,
    val createButton: JButton,
)

internal class SyncEditorComponents(
    val contentArea: JTextArea,
    val searchField: JTextField,
    val authorField: JTextField,
    val searchLabel: JLabel,
    val authorLabel: JLabel,
    val offlineBadge: JLabel,
    val statusHintLabel: JLabel,
    val statusMetaLabel: JLabel,
)

internal class SyncViews(
    private var i18nService: I18nService,
    private val viewModel: SyncViewModel,
    private val tables: SyncTableComponents,
    private val nav: SyncNavComponents,
    private val editor: SyncEditorComponents,
    private val appVersion: String,
    private val colorDanger: Color,
    private val colorSuccess: Color,
    private val onNavigate: (String) -> Unit,
    private val onShowContactFormDialog: (String?) -> Unit,
) {
    private val messagesModel
        get() = tables.messagesModel

    private val messagesList
        get() = tables.messagesList

    private val contactsModel
        get() = tables.contactsModel

    private val contactsTable
        get() = tables.contactsTable

    private val usersModel
        get() = tables.usersModel

    private val usersTable
        get() = tables.usersTable

    private val navMessagesBtn
        get() = nav.navMessagesBtn

    private val navContactsBtn
        get() = nav.navContactsBtn

    private val navUsersBtn
        get() = nav.navUsersBtn

    private val navNotificationsBtn
        get() = nav.navNotificationsBtn

    private val navProfileBtn
        get() = nav.navProfileBtn

    private val syncButton
        get() = nav.syncButton

    private val createButton
        get() = nav.createButton

    private val contentArea
        get() = editor.contentArea

    private val searchField
        get() = editor.searchField

    private val authorField
        get() = editor.authorField

    private val searchLabel
        get() = editor.searchLabel

    private val authorLabel
        get() = editor.authorLabel

    private val offlineBadge
        get() = editor.offlineBadge

    private val statusHintLabel
        get() = editor.statusHintLabel

    private val statusMetaLabel
        get() = editor.statusMetaLabel

    fun updateI18n(newI18n: I18nService) {
        i18nService = newI18n
        applyViewTranslations()
    }

    private fun applyViewTranslations() {
        syncButton.text = i18nService.translate("swing.button.sync")
        createButton.text = i18nService.translate("swing.button.create")
        searchLabel.text = i18nService.translate("swing.label.search")
        authorLabel.text = i18nService.translate("swing.label.author")
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
    }

    fun createSidebar(): JPanel {
        val panel =
            JPanel(MigLayout("fillx, ins 10, gap 10", "[grow]", "[]10[]10[grow]")).apply { name = "sidebarPanel" }

        navMessagesBtn.addActionListener { onNavigate("MESSAGES") }
        navContactsBtn.addActionListener { onNavigate("CONTACTS") }
        navUsersBtn.addActionListener {
            viewModel.loadUsers()
            onNavigate("USERS")
        }
        navNotificationsBtn.addActionListener {
            viewModel.loadNotifications()
            onNavigate("NOTIFICATIONS")
        }
        navProfileBtn.addActionListener {
            onNavigate("PROFILE")
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

    fun createMessagesView(): JPanel {
        val panel = JPanel(MigLayout("fill, ins 20, gap 15", "[grow]", "[][grow]"))

        val searchPanel = JPanel(MigLayout("fillx, ins 0", "[][grow][]", "[]"))
        searchPanel.add(searchLabel)
        searchPanel.add(searchField, "growx")
        searchPanel.add(syncButton, "w 120!")
        panel.add(searchPanel, "growx, wrap")

        messagesList.cellRenderer =
            object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
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
        val messagesScrollPane = JScrollPane(messagesList)
        panel.add(messagesScrollPane, "grow, wrap")

        val footerPanel = JPanel(MigLayout("fillx, ins 0", "[grow][]", "[][]"))
        footerPanel.add(authorLabel, "split 2")
        footerPanel.add(authorField, "growx, wrap")
        val contentScrollPane = JScrollPane(contentArea)
        footerPanel.add(contentScrollPane, "grow, h 80!, span, wrap")
        footerPanel.add(createButton, "w 180!")
        panel.add(footerPanel, "growx")

        return panel
    }

    fun createContactsView(): JPanel {
        val panel = JPanel(MigLayout("fill, ins 20", "[grow]", "[][grow]"))

        val contactsHeaderPanel = JPanel(MigLayout("fillx, ins 0", "[grow][]", "[]"))
        contactsHeaderPanel.add(JLabel("Contacts Directory").apply { font = font.deriveFont(Font.BOLD, 18f) })

        val createContactBtn =
            JButton("Create Contact").apply {
                icon = RemixIcon.get("system/add-box-line")
                addActionListener { onShowContactFormDialog(null) }
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
                            onShowContactFormDialog(syncId)
                        }
                    }
                }
            }
        )

        return panel
    }

    fun createUsersAdminView(): JPanel {
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

    fun createNotificationsView(): JPanel {
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

        val notifListModel = DefaultListModel<NotificationSummary>()
        val notifList =
            JList(notifListModel).apply {
                name = "notificationsList"
                cellRenderer =
                    object : DefaultListCellRenderer() {
                        override fun getListCellRendererComponent(
                            list: JList<*>?,
                            value: Any?,
                            index: Int,
                            isSelected: Boolean,
                            cellHasFocus: Boolean,
                        ): Component {
                            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                            val n = value as? NotificationSummary ?: return this
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
            SwingUtilities.invokeLater {
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
}
