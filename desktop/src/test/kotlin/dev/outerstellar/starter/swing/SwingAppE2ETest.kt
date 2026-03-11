package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
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
}
