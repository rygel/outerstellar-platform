package io.github.rygel.outerstellar.platform.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object ThemeCatalog {
    private val objectMapper = jacksonObjectMapper()

    private val themes: List<ThemeDefinition> by lazy {
        val resourceStream =
            requireNotNull(
                ThemeCatalog::class.java.classLoader.getResourceAsStream("themes.json")
            ) {
                "Unable to load themes.json from the classpath."
            }

        resourceStream.use { objectMapper.readValue<List<ThemeDefinition>>(it) }
    }

    fun allThemes(): List<ThemeDefinition> = themes

    fun findTheme(themeId: String): ThemeDefinition =
        themes.firstOrNull { it.id == themeId } ?: themes.first { it.id == "dark" }
}
