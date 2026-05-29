package io.github.rygel.outerstellar.platform.model

data class ThemeOption(val id: String, val label: String)

object ThemeCatalog {
    private val themes: List<ThemeOption> =
        listOf(
            ThemeOption("dark", "Dark"),
            ThemeOption("light", "Light"),
            ThemeOption("cupcake", "Cupcake"),
            ThemeOption("bumblebee", "Bumblebee"),
            ThemeOption("emerald", "Emerald"),
            ThemeOption("corporate", "Corporate"),
            ThemeOption("synthwave", "Synthwave"),
            ThemeOption("retro", "Retro"),
            ThemeOption("cyberpunk", "Cyberpunk"),
            ThemeOption("valentine", "Valentine"),
            ThemeOption("halloween", "Halloween"),
            ThemeOption("garden", "Garden"),
            ThemeOption("forest", "Forest"),
            ThemeOption("aqua", "Aqua"),
            ThemeOption("lofi", "Lo-Fi"),
            ThemeOption("pastel", "Pastel"),
            ThemeOption("fantasy", "Fantasy"),
            ThemeOption("wireframe", "Wireframe"),
            ThemeOption("black", "Black"),
            ThemeOption("luxury", "Luxury"),
            ThemeOption("dracula", "Dracula"),
            ThemeOption("cmyk", "CMYK"),
            ThemeOption("autumn", "Autumn"),
            ThemeOption("business", "Business"),
            ThemeOption("acid", "Acid"),
            ThemeOption("lemonade", "Lemonade"),
            ThemeOption("night", "Night"),
            ThemeOption("coffee", "Coffee"),
            ThemeOption("winter", "Winter"),
            ThemeOption("dim", "Dim"),
            ThemeOption("nord", "Nord"),
            ThemeOption("sunset", "Sunset"),
        )

    private val themesById: Map<String, ThemeOption> = themes.associateBy { it.id }

    fun allThemes(): List<ThemeOption> = themes

    fun allThemeIds(): List<String> = themes.map { it.id }

    fun isValidTheme(themeId: String): Boolean = themeId in themesById

    fun findTheme(themeId: String): ThemeOption = themesById[themeId] ?: themesById.getValue("dark")
}
