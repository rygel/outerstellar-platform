package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.web.AuthTokenResponse
import io.mockk.every
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
private const val MIN_DIALOG_WIDTH = 300
private const val MIN_DIALOG_HEIGHT = 200

class UiLayoutTest {
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18n = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    // ---- Main window layout ----

    @Test
    fun `main window fields have usable minimum widths`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (window, frame) = createWindow()
        try {
            runOnEdt {
                frame.setSize(1000, 750)
                frame.doLayout()
                frame.validate()
            }

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

        val (window, frame) = createWindow()
        try {
            runOnEdt {
                frame.setSize(1000, 750)
                frame.doLayout()
                frame.validate()
            }

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

        val (window, frame) = createWindow()
        try {
            runOnEdt {
                frame.setSize(1000, 750)
                frame.doLayout()
                frame.validate()
            }

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

        val (window, frame) = createWindow()
        try {
            runOnEdt {
                frame.setSize(1000, 750)
                frame.doLayout()
                frame.validate()
            }

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

        val (window, frame) = createWindow()
        try {
            runOnEdt {
                frame.setSize(1000, 750)
                frame.doLayout()
                frame.validate()
            }

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

    // ---- Dialog layout tests ----

    @Test
    fun `login dialog fields are wide enough for input`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (window, frame) = createWindow()
        try {
            runOnEdt {
                frame.setSize(1000, 750)
                frame.validate()
            }

            // Trigger login dialog
            runOnEdt { frame.jMenuBar.getMenu(0).getItem(5).doClick() }
            Thread.sleep(200)

            runOnEdt {
                val dialogs = java.awt.Window.getWindows().filterIsInstance<javax.swing.JDialog>()
                val loginDialog = dialogs.lastOrNull { it.isVisible }
                assertTrue(loginDialog != null, "Login dialog should be visible")

                val usernameField = findByName<JTextField>(loginDialog!!, "username")
                val passwordField = findByName<JPasswordField>(loginDialog, "password")
                val loginBtn = findByName<JButton>(loginDialog, "loginBtn")

                loginDialog.doLayout()
                loginDialog.validate()

                assertTrue(
                    loginDialog.width >= MIN_DIALOG_WIDTH,
                    "Login dialog width ${loginDialog.width}px is too narrow (min $MIN_DIALOG_WIDTH)",
                )
                assertTrue(
                    usernameField.preferredSize.width >= MIN_FIELD_WIDTH,
                    "Username field preferred width ${usernameField.preferredSize.width}px is too narrow",
                )
                assertTrue(
                    passwordField.preferredSize.width >= MIN_FIELD_WIDTH,
                    "Password field preferred width ${passwordField.preferredSize.width}px is too narrow",
                )
                assertTrue(
                    loginBtn.width >= MIN_BUTTON_WIDTH,
                    "Login button width ${loginBtn.width}px is too narrow",
                )

                loginDialog.dispose()
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `change password dialog fields are wide enough`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        every { syncService.login("alice", "secret") } returns
            AuthTokenResponse("t", "alice", "USER")

        val (window, frame) = createWindow()
        try {
            runOnEdt {
                frame.setSize(1000, 750)
                frame.validate()
            }

            // Login first
            val vm = window.first
            val latch = java.util.concurrent.CountDownLatch(1)
            vm.login("alice", "secret") { _, _ -> latch.countDown() }
            assertTrue(latch.await(3, java.util.concurrent.TimeUnit.SECONDS))

            // Open change password dialog via menu
            runOnEdt { frame.jMenuBar.getMenu(0).getItem(8).doClick() }
            Thread.sleep(200)

            runOnEdt {
                val dialogs = java.awt.Window.getWindows().filterIsInstance<javax.swing.JDialog>()
                val pwdDialog = dialogs.lastOrNull { it.isVisible }
                assertTrue(pwdDialog != null, "Change password dialog should be visible")

                val currentField = findByName<JPasswordField>(pwdDialog!!, "currentPassword")
                val newField = findByName<JPasswordField>(pwdDialog, "newPassword")
                val confirmField = findByName<JPasswordField>(pwdDialog, "confirmPassword")
                val changeBtn = findByName<JButton>(pwdDialog, "changePasswordBtn")

                pwdDialog.doLayout()
                pwdDialog.validate()

                assertTrue(
                    pwdDialog.width >= MIN_DIALOG_WIDTH,
                    "Change password dialog width ${pwdDialog.width}px is too narrow",
                )
                assertTrue(
                    pwdDialog.height >= MIN_DIALOG_HEIGHT,
                    "Change password dialog height ${pwdDialog.height}px is too short",
                )
                assertTrue(
                    currentField.preferredSize.width >= MIN_FIELD_WIDTH,
                    "Current password field preferred width too narrow: ${currentField.preferredSize.width}px",
                )
                assertTrue(
                    newField.preferredSize.width >= MIN_FIELD_WIDTH,
                    "New password field preferred width too narrow: ${newField.preferredSize.width}px",
                )
                assertTrue(
                    confirmField.preferredSize.width >= MIN_FIELD_WIDTH,
                    "Confirm password field preferred width too narrow: ${confirmField.preferredSize.width}px",
                )
                assertTrue(
                    changeBtn.width >= MIN_BUTTON_WIDTH,
                    "Change password button too narrow: ${changeBtn.width}px",
                )

                pwdDialog.dispose()
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    @Test
    fun `register dialog fields are wide enough`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping layout test in headless mode")

        val (window, frame) = createWindow()
        try {
            runOnEdt {
                frame.setSize(1000, 750)
                frame.validate()
            }

            // Open register dialog
            runOnEdt { frame.jMenuBar.getMenu(0).getItem(7).doClick() }
            Thread.sleep(200)

            runOnEdt {
                val dialogs = java.awt.Window.getWindows().filterIsInstance<javax.swing.JDialog>()
                val regDialog = dialogs.lastOrNull { it.isVisible }
                assertTrue(regDialog != null, "Register dialog should be visible")

                val userField = findByName<JTextField>(regDialog!!, "registerUsername")
                val passField = findByName<JPasswordField>(regDialog, "registerPassword")
                val confirmField = findByName<JPasswordField>(regDialog, "registerPasswordConfirm")
                val registerBtn = findByName<JButton>(regDialog, "registerBtn")

                regDialog.doLayout()
                regDialog.validate()

                assertTrue(
                    regDialog.width >= MIN_DIALOG_WIDTH,
                    "Register dialog too narrow: ${regDialog.width}px",
                )
                assertTrue(
                    userField.preferredSize.width >= MIN_FIELD_WIDTH,
                    "Register username field too narrow: ${userField.preferredSize.width}px",
                )
                assertTrue(
                    passField.preferredSize.width >= MIN_FIELD_WIDTH,
                    "Register password field too narrow: ${passField.preferredSize.width}px",
                )
                assertTrue(
                    confirmField.preferredSize.width >= MIN_FIELD_WIDTH,
                    "Register confirm field too narrow: ${confirmField.preferredSize.width}px",
                )
                assertTrue(
                    registerBtn.width >= MIN_BUTTON_WIDTH,
                    "Register button too narrow: ${registerBtn.width}px",
                )

                regDialog.dispose()
            }
        } finally {
            runOnEdt { frame.dispose() }
        }
    }

    // ---- Helpers ----

    private fun createWindow(): Pair<Pair<SyncViewModel, SyncWindow>, javax.swing.JFrame> {
        val vm = SyncViewModel(messageService, null, syncService, i18n)
        val sw = runOnEdtResult {
            val w = SyncWindow(vm, ThemeManager(), i18n)
            w.configureForTest()
            w
        }
        return (vm to sw) to sw.frame
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
