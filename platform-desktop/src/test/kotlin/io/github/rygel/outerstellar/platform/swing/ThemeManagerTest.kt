package io.github.rygel.outerstellar.platform.swing

import java.awt.Color
import javax.swing.SwingUtilities
import javax.swing.UIManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ThemeManagerTest {

    private val themeManager = ThemeManager()

    private fun onEdt(block: () -> Unit) {
        SwingUtilities.invokeAndWait(block)
    }

    @Test
    fun `applyTheme updates current theme name to Dark`() {
        onEdt { themeManager.applyTheme(DesktopTheme.DARK) }
        onEdt {
            assertEquals("Dark", UIManager.get("current_theme_name"))
            assertNotNull(UIManager.getColor("Panel.background"))
            assertNotNull(UIManager.getColor("ToolBar.background"))
        }
    }

    @Test
    fun `applyTheme switches ui defaults when changing themes twice`() {
        onEdt { themeManager.applyTheme(DesktopTheme.DARK) }
        onEdt {
            assertEquals("Dark", UIManager.get("current_theme_name"))
            assertNotNull(UIManager.getColor("Panel.background"))
        }

        onEdt { themeManager.applyTheme(DesktopTheme.LIGHT) }
        onEdt {
            assertEquals("Light", UIManager.get("current_theme_name"))
            assertNotNull(UIManager.getColor("Panel.background"))
        }
    }

    @Test
    fun `all desktop themes can be applied and update critical ui keys`() {
        DesktopTheme.entries.forEach { theme ->
            onEdt { themeManager.applyTheme(theme) }

            onEdt {
                assertEquals(theme.label, UIManager.get("current_theme_name"))
                assertNotNull(UIManager.getColor("Panel.background"))
                assertNotNull(UIManager.getColor("ToolBar.background"))
                assertNotNull(UIManager.getColor("TextField.background"))
            }
        }
    }

    @Test
    fun `decodeColor returns Color for valid hex`() {
        val color = themeManager.decodeColor("#ff0000")
        assertNotNull(color)
        assertEquals(Color.RED.rgb, color!!.rgb)
    }

    @Test
    fun `decodeColor returns null for null input`() {
        assertEquals(null, themeManager.decodeColor(null))
    }

    @Test
    fun `decodeColor returns null for non hex input`() {
        assertEquals(null, themeManager.decodeColor("red"))
    }
}
