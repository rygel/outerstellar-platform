package dev.outerstellar.starter.swing

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import com.outerstellar.theme.ThemeService
import java.awt.Window
import javax.swing.SwingUtilities
import javax.swing.UIManager

class ThemeManager {
  private val themeService = ThemeService.create()
  private val outerstellarPalette =
    themeService
      .loadFromClasspath("themes/dark.json")
      .getHexMapWithComputed(
        mapOf(
          "background" to "#0f172a",
          "foreground" to "#e2e8f0",
          "componentBackground" to "#111827",
          "selectionBackground" to "#2563eb",
          "borderColor" to "#334155",
          "accent" to "#22c55e",
        )
      )

  fun setLightTheme() {
    applyLookAndFeel(FlatLightLaf())
  }

  fun setDarkTheme() {
    applyLookAndFeel(FlatDarkLaf())
  }

  fun setOuterstellarTheme() {
    applyLookAndFeel(FlatDarkLaf())
    UIManager.put("Panel.background", java.awt.Color.decode(outerstellarPalette["background"]))
    UIManager.put("Label.foreground", java.awt.Color.decode(outerstellarPalette["foreground"]))
    UIManager.put(
      "List.background",
      java.awt.Color.decode(outerstellarPalette["componentBackground"]),
    )
    UIManager.put(
      "List.selectionBackground",
      java.awt.Color.decode(outerstellarPalette["selectionBackground"]),
    )
    UIManager.put(
      "TextArea.background",
      java.awt.Color.decode(outerstellarPalette["componentBackground"]),
    )
    UIManager.put(
      "TextField.background",
      java.awt.Color.decode(outerstellarPalette["componentBackground"]),
    )
    UIManager.put("Button.background", java.awt.Color.decode(outerstellarPalette["accent"]))
    UIManager.put("Button.foreground", java.awt.Color.decode(outerstellarPalette["foreground"]))
    UIManager.put(
      "Component.borderColor",
      java.awt.Color.decode(outerstellarPalette["borderColor"]),
    )
    Window.getWindows().forEach { SwingUtilities.updateComponentTreeUI(it) }
  }

  private fun applyLookAndFeel(lookAndFeel: javax.swing.LookAndFeel) {
    UIManager.setLookAndFeel(lookAndFeel)
    Window.getWindows().forEach { SwingUtilities.updateComponentTreeUI(it) }
  }
}
