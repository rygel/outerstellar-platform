package dev.outerstellar.platform

import dev.outerstellar.platform.model.ThemeCatalog

fun main() {
    ThemeCatalog.allThemes().forEach { theme ->
        println("Theme: ${theme.name} (${theme.id}) - bg: ${theme.colors["background"]}")
    }
}
