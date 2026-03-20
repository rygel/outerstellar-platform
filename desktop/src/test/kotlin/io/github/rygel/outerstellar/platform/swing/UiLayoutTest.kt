package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.i18n.I18nService
import io.mockk.mockk
import java.awt.Component
import java.awt.Container
import java.util.Locale
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPasswordField
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val MIN_FIELD_WIDTH = 150
private const val MIN_BUTTON_WIDTH = 60
private const val MIN_NAV_BUTTON_SIZE = 60
private const val MIN_TABLE_WIDTH = 200

/**
 * Layout tests that verify Swing components have usable minimum sizes.
 *
 * These tests work headless by checking preferred sizes (computed by the layout manager without a
 * display) rather than actual rendered widths. Preferred size is what pack() uses to determine
 * dialog dimensions, so if preferred size is too small, the actual dialog will be too small.
 */
class UiLayoutTest {
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18n = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    @Test
    fun `sidebar nav buttons have sufficient preferred size`() {
        val (_, frame) = createWindow()
        try {
            runOnEdt {
                val messagesBtn = findByName<JButton>(frame, "navMessagesBtn")
                val contactsBtn = findByName<JButton>(frame, "navContactsBtn")
                val usersBtn = findByName<JButton>(frame, "navUsersBtn")

                assertTrue(
                    messagesBtn.preferredSize.width >= MIN_NAV_BUTTON_SIZE,
                    "Messages nav preferred width ${messagesBtn.preferredSize.width}px too small (min $MIN_NAV_BUTTON_SIZE)",
                )
                assertTrue(
                    messagesBtn.preferredSize.height >= MIN_NAV_BUTTON_SIZE,
                    "Messages nav preferred height ${messagesBtn.preferredSize.height}px too small",
                )
                assertTrue(
                    contactsBtn.preferredSize.width >= MIN_NAV_BUTTON_SIZE,
                    "Contacts nav preferred width ${contactsBtn.preferredSize.width}px too small",
                )
                assertTrue(
                    usersBtn.preferredSize.width >= MIN_NAV_BUTTON_SIZE,
                    "Users nav preferred width ${usersBtn.preferredSize.width}px too small",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `action buttons have sufficient preferred size`() {
        val (_, frame) = createWindow()
        try {
            runOnEdt {
                val syncButton = findByName<JButton>(frame, "syncButton")
                val createButton = findByName<JButton>(frame, "createButton")

                assertTrue(
                    syncButton.preferredSize.width >= MIN_BUTTON_WIDTH,
                    "Sync button preferred width ${syncButton.preferredSize.width}px too small (min $MIN_BUTTON_WIDTH)",
                )
                assertTrue(
                    createButton.preferredSize.width >= MIN_BUTTON_WIDTH,
                    "Create button preferred width ${createButton.preferredSize.width}px too small",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `contacts table has usable preferred width`() {
        val (_, frame) = createWindow()
        try {
            runOnEdt {
                val table = findByName<JTable>(frame, "contactsTable")
                assertTrue(
                    table.preferredSize.width >= MIN_TABLE_WIDTH,
                    "Contacts table preferred width ${table.preferredSize.width}px too narrow (min $MIN_TABLE_WIDTH)",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `users admin table has usable preferred width`() {
        val (_, frame) = createWindow()
        try {
            runOnEdt {
                val table = findByName<JTable>(frame, "usersTable")
                assertTrue(
                    table.preferredSize.width >= MIN_TABLE_WIDTH,
                    "Users table preferred width ${table.preferredSize.width}px too narrow (min $MIN_TABLE_WIDTH)",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `dialogs have enforced minimum size`() {
        val (sw, frame) = createWindow()
        try {
            runOnEdt {
                val dialog =
                    JDialog(frame, "Test", false).apply {
                        layout =
                            net.miginfocom.swing.MigLayout(
                                "fill, ins 24, gap 10",
                                "[][grow]",
                                "[][][]",
                            )
                        minimumSize = java.awt.Dimension(420, 250)
                        add(javax.swing.JLabel("Field:"))
                        add(JTextField(), "growx, wrap")
                        pack()
                    }

                assertTrue(
                    dialog.width >= 420,
                    "Dialog width ${dialog.width}px should be at least 420 (minimumSize)",
                )
                assertTrue(
                    dialog.height >= 250,
                    "Dialog height ${dialog.height}px should be at least 250 (minimumSize)",
                )
                dialog.dispose()
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `text fields have reasonable preferred width for data entry`() {
        runOnEdt {
            // A JTextField with 20 columns (Swing default) should be at least 150px wide
            val field = JTextField(20)
            assertTrue(
                field.preferredSize.width >= MIN_FIELD_WIDTH,
                "JTextField(20) preferred width ${field.preferredSize.width}px too narrow for data entry (min $MIN_FIELD_WIDTH)",
            )

            val passwordField = JPasswordField(20)
            assertTrue(
                passwordField.preferredSize.width >= MIN_FIELD_WIDTH,
                "JPasswordField(20) preferred width ${passwordField.preferredSize.width}px too narrow",
            )
        }
    }

    @Test
    fun `search and author fields exist and are text inputs`() {
        val (_, frame) = createWindow()
        try {
            runOnEdt {
                val searchField = findByName<JTextField>(frame, "searchField")
                val authorField = findByName<JTextField>(frame, "authorField")

                assertTrue(searchField.isEditable, "Search field should be editable")
                assertTrue(authorField.isEditable, "Author field should be editable")
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `main window has all expected named components`() {
        val (_, frame) = createWindow()
        try {
            runOnEdt {
                // Verify all key components exist
                findByName<JButton>(frame, "syncButton")
                findByName<JButton>(frame, "createButton")
                findByName<JButton>(frame, "navMessagesBtn")
                findByName<JButton>(frame, "navContactsBtn")
                findByName<JButton>(frame, "navUsersBtn")
                findByName<JButton>(frame, "navProfileBtn")
                findByName<JTextField>(frame, "searchField")
                findByName<JTextField>(frame, "authorField")
                findByName<JTable>(frame, "contactsTable")
                findByName<JTable>(frame, "usersTable")
                findByName<JComponent>(frame, "statusBarPanel")
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `profile nav button exists and is initially disabled`() {
        val (_, frame) = createWindow()
        try {
            runOnEdt {
                val btn = findByName<JButton>(frame, "navProfileBtn")
                assertTrue(btn.preferredSize.width >= MIN_NAV_BUTTON_SIZE, "Profile nav too narrow")
                assertTrue(btn.preferredSize.height >= MIN_NAV_BUTTON_SIZE, "Profile nav too short")
                // not logged in → should be disabled
                assertTrue(!btn.isEnabled, "Profile nav should be disabled before login")
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `profile panel has all expected input components`() {
        val (_, frame) = createWindow()
        try {
            runOnEdt {
                findByName<JTextField>(frame, "profileEmailField")
                findByName<JTextField>(frame, "profileUsernameField")
                findByName<JTextField>(frame, "profileAvatarUrlField")
                findByName<JButton>(frame, "saveProfileBtn")
                findByName<JCheckBox>(frame, "emailNotifCheckbox")
                findByName<JCheckBox>(frame, "pushNotifCheckbox")
                findByName<JButton>(frame, "saveNotifBtn")
                findByName<JButton>(frame, "deleteAccountBtn")
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    // ---- Helpers ----

    private fun createWindow(): Pair<SyncWindow, javax.swing.JFrame> {
        val vm = SyncViewModel(messageService, null, syncService, i18n)
        val sw = runOnEdtResult {
            val w = SyncWindow(vm, ThemeManager(), i18n)
            w.configureForTest()
            w
        }
        return sw to sw.frame
    }

    private inline fun <reified T> findByName(root: Container, name: String): T {
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current is JComponent && current.name == name && current is T) return current
            if (current is Container) current.components.forEach { queue.add(it) }
        }
        throw AssertionError("Component '$name' of type ${T::class.simpleName} not found")
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }

    private fun <T> runOnEdtResult(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: T? = null
        SwingUtilities.invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
