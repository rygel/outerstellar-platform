package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.platform.model.ThemeCatalog
import java.awt.Color
import javax.swing.UIManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ThemeManagerTest {

    private val themeManager = ThemeManager()

    @Test
    fun `applyTheme updates toolbar and core ui color defaults`() {
        val dark = ThemeCatalog.allThemes().first { it.name == "Dark" }
        val expectedBackground = Color.decode(dark.colors.getValue("background"))
        val expectedForeground = Color.decode(dark.colors.getValue("foreground"))
        val expectedComponentBackground = Color.decode(dark.colors.getValue("componentBackground"))

        themeManager.applyTheme(dark)

        assertEquals("Dark", UIManager.get("current_theme_name"))
        assertEquals(expectedBackground.rgb, UIManager.getColor("Panel.background").rgb)
        assertEquals(expectedBackground.rgb, UIManager.getColor("MenuBar.background").rgb)
        assertEquals(expectedBackground.rgb, UIManager.getColor("ToolBar.background").rgb)
        assertEquals(expectedForeground.rgb, UIManager.getColor("ToolBar.foreground").rgb)
        assertEquals(expectedComponentBackground.rgb, UIManager.getColor("List.background").rgb)
        assertEquals(
            expectedComponentBackground.rgb,
            UIManager.getColor("TextField.background").rgb,
        )
        assertEquals(expectedComponentBackground.rgb, UIManager.getColor("TextArea.background").rgb)
    }

    @Test
    fun `applyTheme switches ui defaults when changing themes twice`() {
        val dark = ThemeCatalog.allThemes().first { it.name == "Dark" }
        val light = ThemeCatalog.allThemes().first { it.name == "Default" }
        val expectedDarkBg = Color.decode(dark.colors.getValue("background"))
        val expectedLightBg = Color.decode(light.colors.getValue("background"))

        themeManager.applyTheme(dark)
        assertEquals(expectedDarkBg.rgb, UIManager.getColor("Panel.background").rgb)
        assertEquals(expectedDarkBg.rgb, UIManager.getColor("ToolBar.background").rgb)

        themeManager.applyTheme(light)
        assertEquals("Default", UIManager.get("current_theme_name"))
        assertEquals(expectedLightBg.rgb, UIManager.getColor("Panel.background").rgb)
        assertEquals(expectedLightBg.rgb, UIManager.getColor("ToolBar.background").rgb)
    }

    @Test
    fun `all catalog themes can be applied and update critical ui keys`() {
        ThemeCatalog.allThemes().forEach { theme ->
            val expectedBackground = Color.decode(theme.colors.getValue("background"))
            val expectedComponentBackground =
                Color.decode(theme.colors.getValue("componentBackground"))

            themeManager.applyTheme(theme)

            assertEquals(theme.name, UIManager.get("current_theme_name"))
            assertNotNull(UIManager.getColor("Panel.background"))
            assertNotNull(UIManager.getColor("ToolBar.background"))
            assertNotNull(UIManager.getColor("TextField.background"))
            assertEquals(expectedBackground.rgb, UIManager.getColor("Panel.background").rgb)
            assertEquals(expectedBackground.rgb, UIManager.getColor("ToolBar.background").rgb)
            assertEquals(
                expectedComponentBackground.rgb,
                UIManager.getColor("TextField.background").rgb,
            )
        }
    }
}
