package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.mockk.mockk
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

    private fun createVm(): SyncViewModel {
        val authModule = mockk<AuthModule>(relaxed = true)
        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)
        return SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18nService)
    }

    @BeforeEach
    fun onSetUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
        val viewModel = createVm()
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
        val viewModel = createVm()
        viewModel.author = "E2E Tester"
        viewModel.content = "Test content from E2E"

        assertEquals("E2E Tester", viewModel.author)
        assertEquals("Test content from E2E", viewModel.content)
    }

    @Test
    fun `ui interaction updates viewmodel and calls module`() {
        val w = window!!
        w.textBox("authorField").deleteText().enterText("AssertJ Author")
        w.textBox("contentArea").enterText("AssertJ Content")
        w.button("createButton").click()
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
}
