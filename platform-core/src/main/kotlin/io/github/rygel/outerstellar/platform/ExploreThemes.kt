package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.model.ThemeCatalog

fun main() {
    ThemeCatalog.allThemes().forEach { theme ->
        println("Theme: ${theme.name} (${theme.id}) - bg: ${theme.colors["background"]}")
    }
}
