package io.github.rygel.outerstellar.platform.swing

import com.formdev.flatlaf.FlatDarculaLaf
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatIntelliJLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import java.awt.Color
import javax.swing.UIManager
import org.slf4j.LoggerFactory

enum class DesktopTheme(val label: String, val lafSetup: () -> Unit) {
    DARK("Dark", { FlatDarkLaf.setup() }),
    LIGHT("Light", { FlatLightLaf.setup() }),
    DARCULA("Darcula", { FlatDarculaLaf.setup() }),
    INTELLIJ("IntelliJ", { FlatIntelliJLaf.setup() }),
    MAC_DARK("macOS Dark", { FlatMacDarkLaf.setup() }),
    MAC_LIGHT("macOS Light", { FlatMacLightLaf.setup() }),
}

class ThemeManager {
    private val logger = LoggerFactory.getLogger(ThemeManager::class.java)

    fun availableThemes(): List<DesktopTheme> = DesktopTheme.entries

    fun applyTheme(theme: DesktopTheme) {
        theme.lafSetup()
        UIManager.put("current_theme_name", theme.label)
        FlatLaf.updateUI()
    }

    fun applyThemeByName(name: String) {
        val theme = DesktopTheme.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DesktopTheme.DARK
        applyTheme(theme)
    }

    fun decodeColor(hex: String?): Color? {
        return try {
            if (hex != null && hex.startsWith("#")) Color.decode(hex) else null
        } catch (e: NumberFormatException) {
            logger.warn("Failed to decode hex color {}: {}", hex, e.message)
            null
        }
    }
}
