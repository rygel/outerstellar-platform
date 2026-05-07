package io.github.rygel.outerstellar.platform.swing

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import io.github.rygel.outerstellar.platform.model.ThemeCatalog
import io.github.rygel.outerstellar.platform.model.ThemeDefinition
import java.awt.Color
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import org.slf4j.LoggerFactory

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

        if (isDark) {
            FlatDarkLaf.setup()
        } else {
            FlatLightLaf.setup()
        }

        UIManager.put("current_theme_name", theme.name)
        val palette = theme.colors

        fun putColor(key: String, hex: String?) {
            decodeSafe(hex)?.let { color -> UIManager.put(key, ColorUIResource(color)) }
        }

        fun putColorToKeys(hex: String?, keys: List<String>) {
            decodeSafe(hex)?.let { color ->
                val res = ColorUIResource(color)
                keys.forEach { UIManager.put(it, res) }
            }
        }

        putColorToKeys(palette["background"], backgroundKeys)
        putColorToKeys(palette["foreground"], foregroundKeys)
        putColorToKeys(palette["componentBackground"], componentBackgroundKeys)
        putColorToKeys(palette["selectionBackground"], selectionBackgroundKeys)
        putColorToKeys(palette["background"], selectionForegroundKeys)
        putColorToKeys(palette["accent"], accentKeys)
        putColorToKeys(palette["borderColor"], borderKeys)

        decodeSafe(palette["success"])?.let { UIManager.put("Theme.success", it) }
        decodeSafe(palette["danger"])?.let { UIManager.put("Theme.danger", it) }
        decodeSafe(palette["warning"])?.let { UIManager.put("Theme.warning", it) }
        decodeSafe(palette["accent"])?.let { UIManager.put("Theme.accent", it) }

        FlatLaf.updateUI()
    }

    private val backgroundKeys =
        listOf(
            "Panel.background",
            "Window.background",
            "MenuBar.background",
            "Menu.background",
            "MenuItem.background",
            "CheckBoxMenuItem.background",
            "RadioButtonMenuItem.background",
            "ToolBar.background",
            "ScrollBar.background",
            "Label.background",
            "OptionPane.background",
        )

    private val foregroundKeys =
        listOf(
            "Label.foreground",
            "MenuBar.foreground",
            "Menu.foreground",
            "MenuItem.foreground",
            "CheckBoxMenuItem.foreground",
            "RadioButtonMenuItem.foreground",
            "ToolBar.foreground",
            "CheckBox.foreground",
            "RadioButton.foreground",
            "TitledBorder.titleColor",
            "Button.foreground",
            "ToggleButton.foreground",
            "TextField.foreground",
            "TextArea.foreground",
            "TextPane.foreground",
            "EditorPane.foreground",
            "ComboBox.foreground",
            "List.foreground",
            "Table.foreground",
            "TableHeader.foreground",
            "Tree.foreground",
            "FormattedTextField.foreground",
            "PasswordField.foreground",
            "ToolTip.foreground",
        )

    private val componentBackgroundKeys =
        listOf(
            "List.background",
            "TextArea.background",
            "TextField.background",
            "PasswordField.background",
            "ScrollPane.background",
            "Viewport.background",
            "ComboBox.background",
            "Table.background",
            "TableHeader.background",
            "Tree.background",
            "TextPane.background",
            "EditorPane.background",
            "FormattedTextField.background",
            "ToolTip.background",
        )

    private val selectionBackgroundKeys =
        listOf(
            "List.selectionBackground",
            "Menu.selectionBackground",
            "MenuItem.selectionBackground",
            "TextField.selectionBackground",
            "TextArea.selectionBackground",
            "TextPane.selectionBackground",
            "EditorPane.selectionBackground",
            "Table.selectionBackground",
            "Tree.selectionBackground",
            "FormattedTextField.selectionBackground",
            "ComboBox.selectionBackground",
        )

    private val selectionForegroundKeys =
        listOf(
            "List.selectionForeground",
            "Table.selectionForeground",
            "TextField.selectionForeground",
            "TextArea.selectionForeground",
            "TextPane.selectionForeground",
            "EditorPane.selectionForeground",
            "ComboBox.selectionForeground",
            "Tree.selectionForeground",
            "FormattedTextField.selectionForeground",
        )

    private val accentKeys =
        listOf(
            "Component.focusColor",
            "Component.accentColor",
            "Button.focusedBorderColor",
            "Button.background",
            "Button.default.background",
            "ToggleButton.background",
        )

    private val borderKeys = listOf("Component.borderColor", "Button.borderColor", "Separator.foreground")

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
