package dev.outerstellar.starter.swing

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import dev.outerstellar.starter.model.ThemeDefinition
import dev.outerstellar.starter.model.ThemeCatalog
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Window
import javax.swing.SwingUtilities
import javax.swing.UIManager

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
    if (theme.type == "light") {
        applyLookAndFeel(FlatLightLaf())
    } else {
        applyLookAndFeel(FlatDarkLaf())
    }

    UIManager.put("current_theme_name", theme.name)

    val palette: Map<String, String> = theme.colors

    decodeSafe(palette["background"])?.let { color: Color ->
        UIManager.put("Panel.background", color)
        UIManager.put("Window.background", color)
        UIManager.put("MenuBar.background", color)
        UIManager.put("Menu.background", color)
        UIManager.put("MenuItem.background", color)
        UIManager.put("CheckBoxMenuItem.background", color)
        UIManager.put("RadioButtonMenuItem.background", color)
    }
    
    decodeSafe(palette["foreground"])?.let { color: Color -> 
        UIManager.put("Label.foreground", color)
        UIManager.put("MenuBar.foreground", color)
        UIManager.put("Menu.foreground", color)
        UIManager.put("MenuItem.foreground", color)
        UIManager.put("CheckBoxMenuItem.foreground", color)
        UIManager.put("RadioButtonMenuItem.foreground", color)
    }
    
    decodeSafe(palette["componentBackground"])?.let { color: Color ->
        UIManager.put("List.background", color)
        UIManager.put("TextArea.background", color)
        UIManager.put("TextField.background", color)
        UIManager.put("ScrollPane.background", color)
    }
    
    decodeSafe(palette["selectionBackground"])?.let { color: Color -> 
        UIManager.put("List.selectionBackground", color)
        UIManager.put("Menu.selectionBackground", color)
        UIManager.put("MenuItem.selectionBackground", color)
    }
    
    decodeSafe(palette["accent"])?.let { color: Color -> UIManager.put("Button.background", color) }
    decodeSafe(palette["foreground"])?.let { color: Color -> UIManager.put("Button.foreground", color) }
    decodeSafe(palette["borderColor"])?.let { color: Color -> UIManager.put("Component.borderColor", color) }

    Window.getWindows().forEach { SwingUtilities.updateComponentTreeUI(it) }
  }

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
        Window.getWindows().forEach { SwingUtilities.updateComponentTreeUI(it) }
    } catch (e: javax.swing.UnsupportedLookAndFeelException) {
        logger.error("Look and feel not supported: {}", e.message)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.error("Unexpected error applying look and feel: {}", e.message)
    }
  }
}
