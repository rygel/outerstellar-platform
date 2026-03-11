package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
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
import java.awt.GraphicsEnvironment
import java.util.Locale
import java.util.function.BooleanSupplier

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
        val viewModel = SyncViewModel(messageService, syncService, i18nService)
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
        val viewModel = SyncViewModel(messageService, syncService, i18nService)
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

    private fun waitUntil(timeoutMs: Long, condition: BooleanSupplier) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return
            Thread.sleep(25)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }
}
