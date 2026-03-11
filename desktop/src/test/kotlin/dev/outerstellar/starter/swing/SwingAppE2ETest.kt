package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.ThemeCatalog
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.web.AuthTokenResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.util.Locale
import java.util.function.BooleanSupplier
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.UIManager

class SwingAppE2ETest {

    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }
    private var window: FrameFixture? = null
    private var robot: Robot? = null

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpOnce() {
            if (!GraphicsEnvironment.isHeadless()) {
                FailOnThreadViolationRepaintManager.install()
            }
        }
    }

    @BeforeEach
    fun onSetUp() {
        if (GraphicsEnvironment.isHeadless()) return

        robot = BasicRobot.robotWithNewAwtHierarchy()
        val viewModel = SyncViewModel(messageService, null, syncService, i18nService)
        val syncWindow = GuiActionRunner.execute<SyncWindow> {
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
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI interaction test in headless mode")

        val w = window!!
        w.textBox("authorField").deleteText().enterText("AssertJ Author")
        w.textBox("contentArea").enterText("AssertJ Content")
        w.button("createButton").click()

        verify { messageService.createLocalMessage("AssertJ Author", "AssertJ Content") }
    }

    @Test
    fun `ui auth flow updates menu state on login and logout`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI interaction test in headless mode")

        every { syncService.login("alice", "secret") } returns AuthTokenResponse("tok", "alice", "USER")

        val w = window!!
        assertEquals("Login", w.menuItem("loginItem").target().text)
        w.menuItem("loginItem").click()

        w.dialog().textBox("username").enterText("alice")
        w.dialog().textBox("password").enterText("secret")
        w.dialog().button("loginBtn").click()

        waitUntil(2_000) { w.menuItem("logoutItem").target().isEnabled }

        w.menuItem("logoutItem").click()
        waitUntil(2_000) { !w.menuItem("logoutItem").target().isEnabled }
    }

    @Test
    fun `ui register flow updates menu state`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI interaction test in headless mode")

        every { syncService.register("newuser", "secret123") } returns AuthTokenResponse("tok2", "newuser", "USER")

        val w = window!!
        w.menuItem("registerItem").click()

        w.dialog().textBox("registerUsername").enterText("newuser")
        w.dialog().textBox("registerPassword").enterText("secret123")
        w.dialog().textBox("registerPasswordConfirm").enterText("secret123")
        w.dialog().button("registerBtn").click()

        waitUntil(2_000) { w.menuItem("logoutItem").target().isEnabled }
    }

    @Test
    fun `changing theme from settings updates key ui surfaces`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI interaction test in headless mode")

        val w = window!!
        val darkTheme = ThemeCatalog.allThemes().first { it.name == "Dark" }
        val defaultTheme = ThemeCatalog.allThemes().first { it.name == "Default" }

        w.menuItem("settingsItem").click()
        w.dialog().comboBox("themeCombo").selectItem("Dark")
        w.dialog().button("applyButton").click()

        waitUntil(2_000) {
            GuiActionRunner.execute<String?> { UIManager.get("current_theme_name") as? String } == "Dark"
        }

        assertThemeColors(w, darkTheme)
        assertThemeDiffersFrom(w, defaultTheme, darkTheme)

        w.menuItem("settingsItem").click()
        w.dialog().comboBox("themeCombo").selectItem("Default")
        w.dialog().button("applyButton").click()

        waitUntil(2_000) {
            GuiActionRunner.execute<String?> { UIManager.get("current_theme_name") as? String } == "Default"
        }

        assertThemeColors(w, defaultTheme)
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

    private fun assertThemeColors(w: FrameFixture, theme: dev.outerstellar.starter.model.ThemeDefinition) {
        val expectedWindowBg = Color.decode(theme.colors.getValue("background"))
        val expectedComponentBg = Color.decode(theme.colors.getValue("componentBackground"))

        val frameBg = GuiActionRunner.execute<Color> { requireNotNull((w.target() as JFrame).contentPane.background) }
        val menuBg = GuiActionRunner.execute<Color> { requireNotNull((w.target() as JFrame).jMenuBar.background) }
        val listBg = GuiActionRunner.execute<Color> { requireNotNull(w.list("messagesList").target().background) }
        val searchBg = GuiActionRunner.execute<Color> { requireNotNull(w.textBox("searchField").target().background) }
        val authorBg = GuiActionRunner.execute<Color> { requireNotNull(w.textBox("authorField").target().background) }
        val contentBg = GuiActionRunner.execute<Color> { requireNotNull(w.textBox("contentArea").target().background) }
        val statusBg = GuiActionRunner.execute<Color> {
            requireNotNull((findByName((w.target() as JFrame).contentPane, "statusBarPanel") as JComponent).background)
        }

        assertEquals(expectedWindowBg.rgb, rgbOf(frameBg))
        assertEquals(expectedWindowBg.rgb, rgbOf(menuBg))
        assertEquals(expectedComponentBg.rgb, rgbOf(listBg))
        assertEquals(expectedComponentBg.rgb, rgbOf(searchBg))
        assertEquals(expectedComponentBg.rgb, rgbOf(authorBg))
        assertEquals(expectedComponentBg.rgb, rgbOf(contentBg))
        assertEquals(expectedWindowBg.rgb, rgbOf(statusBg))
    }

    private fun assertThemeDiffersFrom(
        w: FrameFixture,
        previous: dev.outerstellar.starter.model.ThemeDefinition,
        current: dev.outerstellar.starter.model.ThemeDefinition
    ) {
        val prevWindowBg = Color.decode(previous.colors.getValue("background")).rgb
        val currentWindowBg = Color.decode(current.colors.getValue("background")).rgb
        val frameBg = GuiActionRunner.execute<Color> { requireNotNull((w.target() as JFrame).contentPane.background) }

        if (prevWindowBg != currentWindowBg) {
            assertEquals(currentWindowBg, rgbOf(frameBg))
        }
    }

    private fun rgbOf(color: Color?): Int = requireNotNull(color).rgb
}
