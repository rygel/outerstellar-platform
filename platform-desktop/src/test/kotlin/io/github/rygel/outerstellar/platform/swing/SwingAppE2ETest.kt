package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.util.Locale
import java.util.function.BooleanSupplier
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.UIManager
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SwingAppE2ETest {

    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }
    private var window: FrameFixture? = null
    private var robot: Robot? = null

    companion object {
        @JvmStatic
        @BeforeAll
        fun assumeNotHeadless() {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                GraphicsEnvironment.isHeadless(),
                "Test requires a display (AssertJ Swing BasicRobot)",
            )
        }

        @JvmStatic
        @BeforeAll
        fun setUpOnce() {
            FailOnThreadViolationRepaintManager.install()
        }

        fun isMenuPopupSupported(): Boolean {
            return System.getenv("CI") != "true"
        }
    }

    @BeforeEach
    fun onSetUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
        val viewModel = SyncViewModel(messageService, null, syncService, i18nService)
        val syncWindow =
            GuiActionRunner.execute<SyncWindow> {
                val sw = SyncWindow(viewModel, ThemeManager(), i18nService)
                sw.configureForTest()
                sw
            }!!
        window = FrameFixture(robot!!, syncWindow.frame)
        window?.show()
    }

    @AfterEach
    fun tearDown() {
        window?.cleanUp()
        robot?.cleanUp()
    }

    @Test
    fun `viewmodel correctly holds author and content`() {
        val viewModel = SyncViewModel(messageService, null, syncService, i18nService)
        viewModel.author = "E2E Tester"
        viewModel.content = "Test content from E2E"

        assertEquals("E2E Tester", viewModel.author)
        assertEquals("Test content from E2E", viewModel.content)
    }

    @Test
    fun `ui interaction updates viewmodel and calls service`() {
        val w = window!!
        w.textBox("authorField").deleteText().enterText("AssertJ Author")
        w.textBox("contentArea").enterText("AssertJ Content")
        w.button("createButton").click()

        verify { messageService.createLocalMessage("AssertJ Author", "AssertJ Content") }
    }

    @Test
    fun `ui auth flow updates menu state on login and logout`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isMenuPopupSupported(), "JMenu popups not supported in Xvfb/CI")
        every { syncService.login("alice", "secret") } returns AuthTokenResponse("tok", "alice", "USER")

        val w = window!!
        assertEquals("Login", w.menuItem("loginItem").target().text)
        w.menuItem("loginItem").click()

        w.dialog().textBox("username").enterText("alice")
        w.dialog().textBox("password").enterText("secret")
        w.dialog().button("loginBtn").click()

        waitUntil(5_000) { w.menuItem("logoutItem").target().isEnabled }

        w.menuItem("logoutItem").click()
        waitUntil(5_000) { !w.menuItem("logoutItem").target().isEnabled }
    }

    @Test
    fun `ui register flow updates menu state`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isMenuPopupSupported(), "JMenu popups not supported in Xvfb/CI")
        every { syncService.register("newuser", "secret123") } returns AuthTokenResponse("tok2", "newuser", "USER")

        val w = window!!
        w.menuItem("registerItem").click()

        w.dialog().textBox("registerUsername").enterText("newuser")
        w.dialog().textBox("registerPassword").enterText("secret123")
        w.dialog().textBox("registerPasswordConfirm").enterText("secret123")
        w.dialog().button("registerBtn").click()

        waitUntil(5_000) { w.menuItem("logoutItem").target().isEnabled }
    }

    @Test
    fun `changing theme from settings updates key ui surfaces`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isMenuPopupSupported(), "JMenu popups not supported in Xvfb/CI")
        val w = window!!

        clickMenuItemThroughMenu(w, "settingsItem")
        w.dialog().comboBox("themeCombo").selectItem("Dark")
        w.dialog().button("applyButton").click()

        waitUntil(5_000) {
            GuiActionRunner.execute<String?> { UIManager.get("current_theme_name") as? String } == "Dark"
        }

        assertThemeApplied(w, "Dark")

        clickMenuItemThroughMenu(w, "settingsItem")
        w.dialog().comboBox("themeCombo").selectItem("Light")
        w.dialog().button("applyButton").click()

        waitUntil(5_000) {
            GuiActionRunner.execute<String?> { UIManager.get("current_theme_name") as? String } == "Light"
        }

        assertThemeApplied(w, "Light")
    }

    private fun clickMenuItemThroughMenu(w: FrameFixture, menuItemName: String) {
        GuiActionRunner.execute {
            val rootMenu = (w.target() as JFrame).jMenuBar.getMenu(0)
            rootMenu.isSelected = true
            val item =
                rootMenu.popupMenu.components.filterIsInstance<javax.swing.JMenuItem>().firstOrNull {
                    it.name == menuItemName
                }
            item?.doClick()
            null
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: BooleanSupplier) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return
            Thread.sleep(25)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    private fun findByName(root: Container, name: String): Component {
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current is JComponent && current.name == name) return current
            if (current is Container) {
                current.components.forEach(queue::add)
            }
        }
        throw AssertionError("Component not found: $name")
    }

    private fun assertThemeApplied(w: FrameFixture, expectedName: String) {
        val frameBg = GuiActionRunner.execute<Color> { requireNotNull((w.target() as JFrame).contentPane.background) }
        val menuBg = GuiActionRunner.execute<Color> { requireNotNull((w.target() as JFrame).jMenuBar.background) }
        val listBg = GuiActionRunner.execute<Color> { requireNotNull(w.list("messagesList").target().background) }
        val searchBg = GuiActionRunner.execute<Color> { requireNotNull(w.textBox("searchField").target().background) }
        val authorBg = GuiActionRunner.execute<Color> { requireNotNull(w.textBox("authorField").target().background) }
        val contentBg = GuiActionRunner.execute<Color> { requireNotNull(w.textBox("contentArea").target().background) }
        val statusBg =
            GuiActionRunner.execute<Color> {
                requireNotNull(
                    (findByName((w.target() as JFrame).contentPane, "statusBarPanel") as JComponent).background
                )
            }

        assertNotNull(frameBg)
        assertNotNull(menuBg)
        assertNotNull(listBg)
        assertNotNull(searchBg)
        assertNotNull(authorBg)
        assertNotNull(contentBg)
        assertNotNull(statusBg)
        assertEquals(expectedName, UIManager.get("current_theme_name"))
    }

    @Test
    fun `change password menu item is enabled after login`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isMenuPopupSupported(), "JMenu popups not supported in Xvfb/CI")
        every { syncService.login("alice", "secret") } returns AuthTokenResponse("tok", "alice", "USER")

        val w = window!!

        // Before login, change password should be disabled
        val changePasswordBefore =
            GuiActionRunner.execute<Boolean> { w.menuItem("changePasswordItem").target().isEnabled }
        assertEquals(false, changePasswordBefore)

        // Login
        w.menuItem("loginItem").click()
        w.dialog().textBox("username").enterText("alice")
        w.dialog().textBox("password").enterText("secret")
        w.dialog().button("loginBtn").click()

        waitUntil(5_000) { w.menuItem("changePasswordItem").target().isEnabled }

        // After login, change password should be enabled
        val changePasswordAfter =
            GuiActionRunner.execute<Boolean> { w.menuItem("changePasswordItem").target().isEnabled }
        assertEquals(true, changePasswordAfter)
    }

    @Test
    fun `change password dialog has correct fields`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isMenuPopupSupported(), "JMenu popups not supported in Xvfb/CI")
        every { syncService.login("alice", "secret") } returns AuthTokenResponse("tok", "alice", "USER")

        val w = window!!
        w.menuItem("loginItem").click()
        w.dialog().textBox("username").enterText("alice")
        w.dialog().textBox("password").enterText("secret")
        w.dialog().button("loginBtn").click()

        waitUntil(5_000) { w.menuItem("changePasswordItem").target().isEnabled }

        w.menuItem("changePasswordItem").click()

        // Verify the change password dialog has the expected fields
        val dialog = w.dialog()
        dialog.textBox("currentPassword").requireVisible()
        dialog.textBox("newPassword").requireVisible()
        dialog.textBox("confirmPassword").requireVisible()
        dialog.button("changePasswordBtn").requireVisible()

        dialog.close()
    }

    @Test
    fun `users nav button is enabled for admin role`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isMenuPopupSupported(), "JMenu popups not supported in Xvfb/CI")
        every { syncService.login("admin", "secret") } returns AuthTokenResponse("tok", "admin", "ADMIN")

        val w = window!!

        // Before login, users button should be hidden
        val usersVisibleBefore =
            GuiActionRunner.execute<Boolean> { findByName((w.target() as JFrame).contentPane, "navUsersBtn").isEnabled }
        assertEquals(false, usersVisibleBefore)

        // Login as admin
        w.menuItem("loginItem").click()
        w.dialog().textBox("username").enterText("admin")
        w.dialog().textBox("password").enterText("secret")
        w.dialog().button("loginBtn").click()

        waitUntil(5_000) {
            GuiActionRunner.execute<Boolean> {
                findByName((w.target() as JFrame).contentPane, "navUsersBtn").isEnabled
            } == true
        }

        val usersVisibleAfter =
            GuiActionRunner.execute<Boolean> { findByName((w.target() as JFrame).contentPane, "navUsersBtn").isEnabled }
        assertEquals(true, usersVisibleAfter)
    }

    @Test
    fun `users nav button stays disabled for regular user`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isMenuPopupSupported(), "JMenu popups not supported in Xvfb/CI")
        every { syncService.login("alice", "secret") } returns AuthTokenResponse("tok", "alice", "USER")

        val w = window!!
        w.menuItem("loginItem").click()
        w.dialog().textBox("username").enterText("alice")
        w.dialog().textBox("password").enterText("secret")
        w.dialog().button("loginBtn").click()

        waitUntil(5_000) { w.menuItem("logoutItem").target().isEnabled }

        val usersVisible =
            GuiActionRunner.execute<Boolean> { findByName((w.target() as JFrame).contentPane, "navUsersBtn").isEnabled }
        assertEquals(false, usersVisible)
    }
}
