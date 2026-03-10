package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import io.mockk.mockk
import io.mockk.verify
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale
import javax.swing.JFrame

class SwingAppE2ETest {

    private lateinit var window: FrameFixture
    private lateinit var robot: Robot
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("swing-messages").also { it.setLocale(Locale.ENGLISH) }

    @BeforeEach
    fun onSetUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
        val viewModel = SyncViewModel(messageService, syncService, i18nService)
        val themeManager = ThemeManager()
        
        val frame = GuiActionRunner.execute<JFrame> {
            val syncWindow = SyncWindow(viewModel, themeManager, i18nService)
            syncWindow.show()
            syncWindow.frame
        }
        window = FrameFixture(robot, frame)
        window.show()
    }

    @AfterEach
    fun tearDown() {
        window.cleanUp()
    }

    @Test
    fun `application window renders with correct title and components`() {
        window.requireTitle("Outerstellar Swing Sync Demo")
        window.textBox("searchField").requireEnabled()
        window.button("syncButton").requireEnabled()
        window.button("createButton").requireEnabled()
    }

    @Test
    fun `settings dialog can change language to French`() {
        // 1. Open settings dialog
        window.menuItem("appMenu").click()
        window.menuItem("settingsItem").click()
        
        val dialog = window.dialog("settingsDialog")
        dialog.requireVisible()
        
        // 2. Change language to French
        dialog.comboBox("langCombo").selectItem("French")
        dialog.button("applyButton").click()
        
        // 3. Verify labels updated to French
        window.requireTitle("Demo de synchronisation Swing Outerstellar")
        window.button("syncButton").requireText("Synchroniser")
        window.button("createButton").requireText("Créer un message local")
    }

    @Test
    fun `creating a message calls the message service`() {
        window.textBox("authorField").deleteText().enterText("E2E Tester")
        window.textBox("contentArea").enterText("Test content from E2E")
        window.button("createButton").click()
        
        verify { messageService.createLocalMessage("E2E Tester", "Test content from E2E") }
    }
}
