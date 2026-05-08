package io.github.rygel.outerstellar.platform.fx.service

import atlantafx.base.theme.CupertinoDark
import atlantafx.base.theme.CupertinoLight
import atlantafx.base.theme.Dracula
import atlantafx.base.theme.NordLight
import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import atlantafx.base.theme.Theme
import javafx.application.Application
import javafx.scene.Scene

enum class FxTheme(val label: String, val atlantafx: Theme, val cssFile: String) {
    DARK("Dark", PrimerDark(), "theme-dark.css"),
    LIGHT("Light", PrimerLight(), "theme-light.css"),
    DARCULA("Darcula", Dracula(), "theme-darcula.css"),
    INTELLIJ("IntelliJ", NordLight(), "theme-intellij.css"),
    MAC_DARK("macOS Dark", CupertinoDark(), "theme-cyprus-dark.css"),
    MAC_LIGHT("macOS Light", CupertinoLight(), "theme-cyprus.css"),
}

class FxThemeManager {
    private var scene: Scene? = null
    private var current: FxTheme? = null

    fun setScene(scene: Scene) {
        this.scene = scene
        current?.let { applyTheme(it) }
    }

    fun applyTheme(theme: FxTheme) {
        current = theme
        Application.setUserAgentStylesheet(theme.atlantafx.userAgentStylesheet)
        val s = scene ?: return
        s.stylesheets.clear()
        val themeCss = javaClass.getResource("/css/${theme.cssFile}")?.toExternalForm() ?: return
        val appCss = javaClass.getResource("/css/app.css")?.toExternalForm() ?: return
        s.stylesheets.addAll(themeCss, appCss)
    }

    fun applyThemeByName(name: String) {
        val theme = FxTheme.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FxTheme.DARK
        applyTheme(theme)
    }

    fun currentThemeName(): String? = current?.label
}
