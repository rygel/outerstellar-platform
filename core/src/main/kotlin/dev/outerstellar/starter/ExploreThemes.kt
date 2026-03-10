package dev.outerstellar.starter

import dev.outerstellar.starter.model.ThemeCatalog

fun main() {
    ThemeCatalog.allThemes().forEach { theme ->
        println("Theme: ${theme.name} (${theme.id}) - bg: ${theme.colors["background"]}")
    }
}
