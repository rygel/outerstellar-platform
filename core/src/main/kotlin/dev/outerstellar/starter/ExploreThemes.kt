package dev.outerstellar.starter

import com.outerstellar.starter.model.ThemeCatalog

fun main() {
    ThemeCatalog.allThemes().forEach { theme ->
        println("Theme: ${theme.name} (${theme.id}) - bg: ${theme.palette.bgPrimary}")
    }
}
