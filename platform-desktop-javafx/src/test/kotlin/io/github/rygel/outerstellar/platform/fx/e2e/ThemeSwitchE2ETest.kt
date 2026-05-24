package io.github.rygel.outerstellar.platform.fx.e2e

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.fx.FxAppContext
import io.github.rygel.outerstellar.platform.fx.app.FxAppConfig
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.mockk.mockk
import java.util.Locale
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.api.FxRobot
import org.testfx.framework.junit5.ApplicationExtension
import org.testfx.framework.junit5.Start

@ExtendWith(ApplicationExtension::class)
class ThemeSwitchE2ETest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUp() {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                java.awt.GraphicsEnvironment.isHeadless() && System.getProperty("testfx.monocle") != "true",
                "Test requires a display or Monocle",
            )

            val authModule = mockk<AuthModule>(relaxed = true)
            val syncDataModule = mockk<SyncDataModule>(relaxed = true)
            val profileModule = mockk<ProfileModule>(relaxed = true)
            val adminModule = mockk<AdminModule>(relaxed = true)
            val notificationModule = mockk<NotificationModule>(relaxed = true)
            val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

            FxAppContext.themeManager = FxThemeManager()
            FxAppContext.i18nService = i18nService
            FxAppContext.appConfig = FxAppConfig()
            FxAppContext.authModule = authModule
        }
    }

    private lateinit var themeManager: FxThemeManager

    @Start
    fun start(stage: Stage) {
        themeManager = FxAppContext.themeManager
        val root = VBox(10.0, Label("Theme Test"))
        val scene = Scene(root, 400.0, 300.0)
        stage.scene = scene
        stage.show()
    }

    @Test
    fun `applying DARK theme updates currentThemeName`(@Suppress("UNUSED_PARAMETER") robot: FxRobot) {
        themeManager.applyThemeByName("DARK")
        assertEquals("Dark", themeManager.currentThemeName())
    }

    @Test
    fun `applying LIGHT theme updates currentThemeName`(@Suppress("UNUSED_PARAMETER") robot: FxRobot) {
        themeManager.applyThemeByName("LIGHT")
        assertEquals("Light", themeManager.currentThemeName())
    }

    @Test
    fun `applying DARCULA theme updates currentThemeName`(@Suppress("UNUSED_PARAMETER") robot: FxRobot) {
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
