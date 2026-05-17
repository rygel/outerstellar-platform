package io.github.rygel.outerstellar.platform.web

data class DaisyTheme(val id: String, val label: String)

object ThemeCatalog {
    val allThemes: List<DaisyTheme> =
        listOf(
            DaisyTheme("dark", "Dark"),
            DaisyTheme("light", "Light"),
            DaisyTheme("cupcake", "Cupcake"),
            DaisyTheme("bumblebee", "Bumblebee"),
            DaisyTheme("emerald", "Emerald"),
            DaisyTheme("corporate", "Corporate"),
            DaisyTheme("synthwave", "Synthwave"),
            DaisyTheme("retro", "Retro"),
            DaisyTheme("cyberpunk", "Cyberpunk"),
            DaisyTheme("valentine", "Valentine"),
            DaisyTheme("halloween", "Halloween"),
            DaisyTheme("garden", "Garden"),
            DaisyTheme("forest", "Forest"),
            DaisyTheme("aqua", "Aqua"),
            DaisyTheme("lofi", "Lo-Fi"),
            DaisyTheme("pastel", "Pastel"),
            DaisyTheme("fantasy", "Fantasy"),
            DaisyTheme("wireframe", "Wireframe"),
            DaisyTheme("black", "Black"),
            DaisyTheme("luxury", "Luxury"),
            DaisyTheme("dracula", "Dracula"),
            DaisyTheme("cmyk", "CMYK"),
            DaisyTheme("autumn", "Autumn"),
            DaisyTheme("business", "Business"),
            DaisyTheme("acid", "Acid"),
            DaisyTheme("lemonade", "Lemonade"),
            DaisyTheme("night", "Night"),
            DaisyTheme("coffee", "Coffee"),
            DaisyTheme("winter", "Winter"),
            DaisyTheme("dim", "Dim"),
            DaisyTheme("nord", "Nord"),
            DaisyTheme("sunset", "Sunset"),
        )

    val validThemeIds: Set<String> = allThemes.map { it.id }.toSet()

    fun isValidTheme(themeId: String): Boolean = themeId in validThemeIds

    fun findTheme(themeId: String): DaisyTheme =
        allThemes.firstOrNull { it.id == themeId } ?: allThemes.first { it.id == "dark" }
}
