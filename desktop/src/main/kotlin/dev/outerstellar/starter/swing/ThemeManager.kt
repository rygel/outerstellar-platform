package dev.outerstellar.starter.swing

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import dev.outerstellar.starter.model.ThemeCatalog
import dev.outerstellar.starter.model.ThemeDefinition
import org.slf4j.LoggerFactory
import java.awt.Color
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource

class ThemeManager {
    private val logger = LoggerFactory.getLogger(ThemeManager::class.java)

    fun availableThemes(): List<ThemeDefinition> = ThemeCatalog.allThemes()

    fun setLightTheme() {
        applyLookAndFeel(FlatLightLaf())
        UIManager.put("current_theme_name", "Light")
    }

    fun setDarkTheme() {
        applyLookAndFeel(FlatDarkLaf())
        UIManager.put("current_theme_name", "Dark")
    }

    fun applyTheme(theme: ThemeDefinition) {
        val isDark = theme.type == "dark"

        // Use FlatLaf setup which is more robust for runtime switching
        if (isDark) {
            FlatDarkLaf.setup()
        } else {
            FlatLightLaf.setup()
        }

        UIManager.put("current_theme_name", theme.name)
        val palette = theme.colors

        // Helper to put ColorUIResource which updateComponentTreeUI respects
        fun putColor(key: String, hex: String?) {
            decodeSafe(hex)?.let { color ->
                UIManager.put(key, ColorUIResource(color))
            }
        }

        // Standard background/foreground
        putColor("Panel.background", palette["background"])
        putColor("Window.background", palette["background"])
        putColor("MenuBar.background", palette["background"])
        putColor("Menu.background", palette["background"])
        putColor("MenuItem.background", palette["background"])
        putColor("CheckBoxMenuItem.background", palette["background"])
        putColor("RadioButtonMenuItem.background", palette["background"])
        putColor("ToolBar.background", palette["background"])
        putColor("ScrollBar.background", palette["background"])
        putColor("Label.background", palette["background"])
        putColor("OptionPane.background", palette["background"])

        decodeSafe(palette["foreground"])?.let { color ->
            val res = ColorUIResource(color)
            UIManager.put("Label.foreground", res)
            UIManager.put("MenuBar.foreground", res)
            UIManager.put("Menu.foreground", res)
            UIManager.put("MenuItem.foreground", res)
            UIManager.put("CheckBoxMenuItem.foreground", res)
            UIManager.put("RadioButtonMenuItem.foreground", res)
            UIManager.put("ToolBar.foreground", res)
            UIManager.put("CheckBox.foreground", res)
            UIManager.put("RadioButton.foreground", res)
            UIManager.put("TitledBorder.titleColor", res)
            UIManager.put("Button.foreground", res)
            UIManager.put("ToggleButton.foreground", res)
            UIManager.put("TextField.foreground", res)
            UIManager.put("TextArea.foreground", res)
            UIManager.put("TextPane.foreground", res)
            UIManager.put("EditorPane.foreground", res)
            UIManager.put("ComboBox.foreground", res)
            UIManager.put("List.foreground", res)
            UIManager.put("Table.foreground", res)
            UIManager.put("TableHeader.foreground", res)
            UIManager.put("Tree.foreground", res)
            UIManager.put("FormattedTextField.foreground", res)
            UIManager.put("PasswordField.foreground", res)
            UIManager.put("ToolTip.foreground", res)
        }

        // Component specific backgrounds
        putColor("List.background", palette["componentBackground"])
        putColor("TextArea.background", palette["componentBackground"])
        putColor("TextField.background", palette["componentBackground"])
        putColor("PasswordField.background", palette["componentBackground"])
        putColor("ScrollPane.background", palette["componentBackground"])
        putColor("Viewport.background", palette["componentBackground"])
        putColor("ComboBox.background", palette["componentBackground"])
        putColor("Table.background", palette["componentBackground"])
        putColor("TableHeader.background", palette["componentBackground"])
        putColor("Tree.background", palette["componentBackground"])
        putColor("TextPane.background", palette["componentBackground"])
        putColor("EditorPane.background", palette["componentBackground"])
        putColor("FormattedTextField.background", palette["componentBackground"])
        putColor("ToolTip.background", palette["componentBackground"])

        // Selection and Accents
        putColor("List.selectionBackground", palette["selectionBackground"])
        putColor("Menu.selectionBackground", palette["selectionBackground"])
        putColor("MenuItem.selectionBackground", palette["selectionBackground"])
        putColor("TextField.selectionBackground", palette["selectionBackground"])
        putColor("TextArea.selectionBackground", palette["selectionBackground"])
        putColor("TextPane.selectionBackground", palette["selectionBackground"])
        putColor("EditorPane.selectionBackground", palette["selectionBackground"])
        putColor("Table.selectionBackground", palette["selectionBackground"])
        putColor("Tree.selectionBackground", palette["selectionBackground"])
        putColor("FormattedTextField.selectionBackground", palette["selectionBackground"])
        putColor("ComboBox.selectionBackground", palette["selectionBackground"])

        // We use background for selection foreground as most selection backgrounds are dark/accented
        decodeSafe(palette["background"])?.let { color ->
            val res = ColorUIResource(color)
            UIManager.put("List.selectionForeground", res)
            UIManager.put("Table.selectionForeground", res)
            UIManager.put("TextField.selectionForeground", res)
            UIManager.put("TextArea.selectionForeground", res)
            UIManager.put("TextPane.selectionForeground", res)
            UIManager.put("EditorPane.selectionForeground", res)
            UIManager.put("ComboBox.selectionForeground", res)
            UIManager.put("Tree.selectionForeground", res)
            UIManager.put("FormattedTextField.selectionForeground", res)
        }

        decodeSafe(palette["accent"])?.let { color ->
            val res = ColorUIResource(color)
            UIManager.put("Component.focusColor", res)
            UIManager.put("Component.accentColor", res)
            UIManager.put("Button.focusedBorderColor", res)
            UIManager.put("Button.background", res)
            UIManager.put("Button.default.background", res)
            UIManager.put("ToggleButton.background", res)
        }

        decodeSafe(palette["borderColor"])?.let { color ->
            val res = ColorUIResource(color)
            UIManager.put("Component.borderColor", res)
            UIManager.put("Button.borderColor", res)
            UIManager.put("Separator.foreground", res)
        }

        // Custom Outerstellar keys for use in renderers/manual painting
        decodeSafe(palette["success"])?.let { UIManager.put("Theme.success", it) }
        decodeSafe(palette["danger"])?.let { UIManager.put("Theme.danger", it) }
        decodeSafe(palette["warning"])?.let { UIManager.put("Theme.warning", it) }
        decodeSafe(palette["accent"])?.let { UIManager.put("Theme.accent", it) }

        // FlatLaf way to update all windows
        FlatLaf.updateUI()
    }

    fun decodeColor(hex: String?): Color? = decodeSafe(hex)

    private fun decodeSafe(hex: String?): Color? {
        return try {
            if (hex != null && hex.startsWith("#")) Color.decode(hex) else null
        } catch (e: NumberFormatException) {
            logger.warn("Failed to decode hex color {}: {}", hex, e.message)
            null
        }
    }

    private fun applyLookAndFeel(lookAndFeel: javax.swing.LookAndFeel) {
        try {
            UIManager.setLookAndFeel(lookAndFeel)
            FlatLaf.updateUI()
        } catch (e: javax.swing.UnsupportedLookAndFeelException) {
            logger.error("Look and feel not supported: {}", e.message)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Unexpected error applying look and feel: {}", e.message)
        }
    }
}
