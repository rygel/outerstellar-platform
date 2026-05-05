package io.github.rygel.outerstellar.platform.model

import kotlinx.serialization.json.Json

object ThemeCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    private val themes: List<ThemeDefinition> by lazy {
        val resourceStream =
            requireNotNull(ThemeCatalog::class.java.classLoader.getResourceAsStream("themes.json")) {
                "Unable to load themes.json from the classpath."
            }

        resourceStream.bufferedReader().use { json.decodeFromString<List<ThemeDefinition>>(it.readText()) }
    }

    fun allThemes(): List<ThemeDefinition> = themes

    fun findTheme(themeId: String): ThemeDefinition =
        themes.firstOrNull { it.id == themeId } ?: themes.first { it.id == "dark" }
}
