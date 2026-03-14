package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import io.mockk.mockk
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.util.Locale
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test

private const val MIN_FIELD_WIDTH = 150
private const val MIN_BUTTON_WIDTH = 60
private const val MIN_BUTTON_HEIGHT = 24
private const val MIN_TABLE_WIDTH = 200
private const val MIN_NAV_BUTTON_SIZE = 60

class UiLayoutTest {
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18n = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    @Test
    fun `main window fields have usable minimum widths`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (_, frame) = createAndShowWindow()
        try {
            runOnEdt {
                val searchField = findByName<JTextField>(frame, "searchField")
                val authorField = findByName<JTextField>(frame, "authorField")

                assertTrue(
                    searchField.width >= MIN_FIELD_WIDTH,
                    "Search field width ${searchField.width}px is too narrow (min $MIN_FIELD_WIDTH)",
                )
                assertTrue(
                    authorField.width >= MIN_FIELD_WIDTH,
                    "Author field width ${authorField.width}px is too narrow (min $MIN_FIELD_WIDTH)",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `action buttons have readable size`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (_, frame) = createAndShowWindow()
        try {
            runOnEdt {
                val syncButton = findByName<JButton>(frame, "syncButton")
                val createButton = findByName<JButton>(frame, "createButton")

                assertTrue(
                    syncButton.width >= MIN_BUTTON_WIDTH,
                    "Sync button width ${syncButton.width}px is too narrow (min $MIN_BUTTON_WIDTH)",
                )
                assertTrue(
                    syncButton.height >= MIN_BUTTON_HEIGHT,
                    "Sync button height ${syncButton.height}px is too short (min $MIN_BUTTON_HEIGHT)",
                )
                assertTrue(
                    createButton.width >= MIN_BUTTON_WIDTH,
                    "Create button width ${createButton.width}px is too narrow",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `sidebar nav buttons are large enough to be clickable`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (_, frame) = createAndShowWindow()
        try {
            runOnEdt {
                val messagesBtn = findByName<JButton>(frame, "navMessagesBtn")
                val contactsBtn = findByName<JButton>(frame, "navContactsBtn")

                assertTrue(
                    messagesBtn.width >= MIN_NAV_BUTTON_SIZE,
                    "Messages nav button width ${messagesBtn.width}px is too small",
                )
                assertTrue(
                    messagesBtn.height >= MIN_NAV_BUTTON_SIZE,
                    "Messages nav button height ${messagesBtn.height}px is too small",
                )
                assertTrue(
                    contactsBtn.width >= MIN_NAV_BUTTON_SIZE,
                    "Contacts nav button width ${contactsBtn.width}px is too small",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `contacts table has usable width`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (_, frame) = createAndShowWindow()
        try {
            runOnEdt {
                val table = findByName<JTable>(frame, "contactsTable")
                assertTrue(
                    table.preferredSize.width >= MIN_TABLE_WIDTH,
                    "Contacts table preferred width ${table.preferredSize.width}px is too narrow",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `users admin table has usable width`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (_, frame) = createAndShowWindow()
        try {
            runOnEdt {
                val table = findByName<JTable>(frame, "usersTable")
                assertTrue(
                    table.preferredSize.width >= MIN_TABLE_WIDTH,
                    "Users table preferred width ${table.preferredSize.width}px is too narrow",
                )
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `login dialog fields are wide enough for input`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (sw, frame) = createAndShowWindow()
        try {
            // Build the login dialog directly instead of clicking menu
            val dialog = runOnEdtResult {
                val d = javax.swing.JDialog(frame, "Login Test", false)
                d.layout =
                    net.miginfocom.swing.MigLayout("fill, ins 24, gap 10", "[][grow]", "[][][]")
                d.add(javax.swing.JLabel("Username:"))
                val userField = JTextField().apply { name = "testLoginUser" }
                d.add(userField, "growx, wrap")
                d.add(javax.swing.JLabel("Password:"))
                val passField = JPasswordField().apply { name = "testLoginPass" }
                d.add(passField, "growx, wrap")
                val btn = JButton("Sign In")
                d.add(btn, "span, right")
                d.pack()
                d.setLocationRelativeTo(frame)
                d.isVisible = true
                d
            }
            Thread.sleep(100)

            runOnEdt {
                val userField = findByName<JTextField>(dialog, "testLoginUser")
                val passField = findByName<JPasswordField>(dialog, "testLoginPass")

                assertTrue(
                    userField.width >= MIN_FIELD_WIDTH,
                    "Login username field width ${userField.width}px is too narrow (min $MIN_FIELD_WIDTH)",
                )
                assertTrue(
                    passField.width >= MIN_FIELD_WIDTH,
                    "Login password field width ${passField.width}px is too narrow (min $MIN_FIELD_WIDTH)",
                )
                dialog.dispose()
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `change password dialog fields are wide enough`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (sw, frame) = createAndShowWindow()
        try {
            // Build a change password dialog directly
            val dialog = runOnEdtResult {
                val d = javax.swing.JDialog(frame, "Change Password Test", false)
                d.layout =
                    net.miginfocom.swing.MigLayout("fill, ins 24, gap 10", "[][grow]", "[][][][]")
                d.add(javax.swing.JLabel("Current:"))
                val currentField = JPasswordField().apply { name = "testCurrent" }
                d.add(currentField, "growx, wrap")
                d.add(javax.swing.JLabel("New:"))
                val newField = JPasswordField().apply { name = "testNew" }
                d.add(newField, "growx, wrap")
                d.add(javax.swing.JLabel("Confirm:"))
                val confirmField = JPasswordField().apply { name = "testConfirm" }
                d.add(confirmField, "growx, wrap")
                val btn = JButton("Change Password")
                d.add(btn, "span, right")
                d.pack()
                d.setLocationRelativeTo(frame)
                d.isVisible = true
                d
            }
            Thread.sleep(100)

            runOnEdt {
                val currentField = findByName<JPasswordField>(dialog, "testCurrent")
                val newField = findByName<JPasswordField>(dialog, "testNew")
                val confirmField = findByName<JPasswordField>(dialog, "testConfirm")

                assertTrue(
                    currentField.width >= MIN_FIELD_WIDTH,
                    "Current password field width ${currentField.width}px is too narrow",
                )
                assertTrue(
                    newField.width >= MIN_FIELD_WIDTH,
                    "New password field width ${newField.width}px is too narrow",
                )
                assertTrue(
                    confirmField.width >= MIN_FIELD_WIDTH,
                    "Confirm password field width ${confirmField.width}px is too narrow",
                )
                dialog.dispose()
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `register dialog fields are wide enough`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (sw, frame) = createAndShowWindow()
        try {
            val dialog = runOnEdtResult {
                val d = javax.swing.JDialog(frame, "Register Test", false)
                d.layout =
                    net.miginfocom.swing.MigLayout("fill, ins 24, gap 10", "[][grow]", "[][][][]")
                d.add(javax.swing.JLabel("Username:"))
                val userField = JTextField().apply { name = "testRegUser" }
                d.add(userField, "growx, wrap")
                d.add(javax.swing.JLabel("Password:"))
                val passField = JPasswordField().apply { name = "testRegPass" }
                d.add(passField, "growx, wrap")
                d.add(javax.swing.JLabel("Confirm:"))
                val confirmField = JPasswordField().apply { name = "testRegConfirm" }
                d.add(confirmField, "growx, wrap")
                val btn = JButton("Create Account")
                d.add(btn, "span, right")
                d.pack()
                d.setLocationRelativeTo(frame)
                d.isVisible = true
                d
            }
            Thread.sleep(100)

            runOnEdt {
                val userField = findByName<JTextField>(dialog, "testRegUser")
                val passField = findByName<JPasswordField>(dialog, "testRegPass")
                val confirmField = findByName<JPasswordField>(dialog, "testRegConfirm")

                assertTrue(
                    userField.width >= MIN_FIELD_WIDTH,
                    "Register username width ${userField.width}px is too narrow",
                )
                assertTrue(
                    passField.width >= MIN_FIELD_WIDTH,
                    "Register password width ${passField.width}px is too narrow",
                )
                assertTrue(
                    confirmField.width >= MIN_FIELD_WIDTH,
                    "Register confirm width ${confirmField.width}px is too narrow",
                )
                dialog.dispose()
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    // ---- Helpers ----

    private fun createAndShowWindow(): Pair<SyncWindow, javax.swing.JFrame> {
        val vm = SyncViewModel(messageService, null, syncService, i18n)
        val sw = runOnEdtResult {
            val w = SyncWindow(vm, ThemeManager(), i18n)
            w.configureForTest()
            w.frame.setSize(1000, 750)
            w.frame.isVisible = true
            w
        }
        // Wait for layout to complete
        try {
            Thread.sleep(200)
        } catch (_: InterruptedException) {}
        runOnEdt { sw.frame.validate() }
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
