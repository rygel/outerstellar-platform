package dev.outerstellar.starter.swing

import dev.outerstellar.starter.model.ThemeCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import javax.swing.UIManager

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
        assertEquals(expectedComponentBackground.rgb, UIManager.getColor("TextField.background").rgb)
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
}
