package io.github.rygel.outerstellar.platform.fx.e2e

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.fx.app.FxAppConfig
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.platform.sync.engine.SyncEngine
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.testfx.api.FxRobot
import org.testfx.framework.junit5.ApplicationExtension
import org.testfx.framework.junit5.Start

@ExtendWith(ApplicationExtension::class)
class FxAppE2ETest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpKoin() {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                java.awt.GraphicsEnvironment.isHeadless() && System.getProperty("testfx.monocle") != "true",
                "Test requires a display or Monocle",
            )

            if (GlobalContext.getOrNull() != null) {
                stopKoin()
            }

            val messageService = mockk<MessageService>(relaxed = true)
            every { messageService.listMessages(any(), any(), any(), any()) } returns
                PagedResult(emptyList(), PaginationMetadata(1, 100, 0))

            val syncService = mockk<SyncService>(relaxed = true)
            val syncEngine = mockk<SyncEngine>(relaxed = true)

            val testModule = module {
                single { FxAppConfig() }
                single {
                    io.github.rygel.outerstellar.platform.AppConfig(jdbcUrl = "", jdbcUser = "", jdbcPassword = "")
                }
                single { FxThemeManager() }
                single<I18nService> { I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) } }
                single { messageService }
                single { syncService }
                single<io.github.rygel.outerstellar.platform.persistence.MessageCache> {
                    io.github.rygel.outerstellar.platform.persistence.NoOpMessageCache
                }
                single<SyncEngine> { syncEngine }
                single { FxSyncViewModel(get()) }
            }

            GlobalContext.startKoin { modules(testModule) }
        }

        @JvmStatic
        @AfterAll
        fun tearDownKoin() {
            if (GlobalContext.getOrNull() != null) {
                stopKoin()
            }
        }
    }

    @Start
    fun start(stage: Stage) {
        val loader = FXMLLoader(javaClass.getResource("/fxml/MainWindow.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        val scene = Scene(root, 1200.0, 800.0)
        val themeManager = GlobalContext.get().get<FxThemeManager>()
        themeManager.setScene(scene)
        themeManager.applyThemeByName("DARK")
        stage.scene = scene
        stage.show()
    }

    @Test
    fun `main window has navigation buttons`(robot: FxRobot) {
        val messagesBtn = robot.lookup("#navMessagesBtn").query<Button>()
        assertNotNull(messagesBtn)
        assertTrue(messagesBtn.visibleProperty().get())

        val contactsBtn = robot.lookup("#navContactsBtn").query<Button>()
        assertNotNull(contactsBtn)
        assertTrue(contactsBtn.visibleProperty().get())

        val settingsBtn = robot.lookup("#navSettingsBtn").query<Button>()
        assertNotNull(settingsBtn)
        assertTrue(settingsBtn.visibleProperty().get())

        val loginBtn = robot.lookup("#navLoginBtn").query<Button>()
        assertNotNull(loginBtn)
        assertEquals("Login", loginBtn.text)
    }

    @Test
    fun `main window has sidebar`(robot: FxRobot) {
        val sidebar = robot.lookup("#sidebar").query<VBox>()
        assertNotNull(sidebar)
        assertTrue(sidebar.visibleProperty().get())
    }

    @Test
    fun `main window has status bar`(robot: FxRobot) {
        val statusLabel = robot.lookup("#statusLabel").query<Node>()
        assertNotNull(statusLabel)
    }
}
