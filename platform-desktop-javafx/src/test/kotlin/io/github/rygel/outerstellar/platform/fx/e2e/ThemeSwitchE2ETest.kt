package io.github.rygel.outerstellar.platform.fx.e2e

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.fx.app.FxAppConfig
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
class ThemeSwitchE2ETest {

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

    private lateinit var themeManager: FxThemeManager

    @Start
    fun start(stage: Stage) {
        themeManager = GlobalContext.get().get<FxThemeManager>()
        val root = VBox(10.0, Label("Theme Test"))
        val scene = Scene(root, 400.0, 300.0)
        stage.scene = scene
        stage.show()
    }

    @Test
    fun `applying DARK theme updates currentThemeName`(robot: FxRobot) {
        themeManager.applyThemeByName("DARK")
        assertEquals("Dark", themeManager.currentThemeName())
    }

    @Test
    fun `applying LIGHT theme updates currentThemeName`(robot: FxRobot) {
        themeManager.applyThemeByName("LIGHT")
        assertEquals("Light", themeManager.currentThemeName())
    }

    @Test
    fun `applying DARCULA theme updates currentThemeName`(robot: FxRobot) {
        themeManager.applyThemeByName("DARCULA")
        assertEquals("Darcula", themeManager.currentThemeName())
    }

    @Test
    fun `scene remains functional after theme manager operations`(robot: FxRobot) {
        val label = robot.lookup(".label").query<Label>()
        assertNotNull(label)
        assertEquals("Theme Test", label.text)
    }
}
