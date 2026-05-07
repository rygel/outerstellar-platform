package io.github.rygel.outerstellar.platform.web

data class DaisyTheme(val id: String, val label: String, val accentHex: String)

object ThemeCatalog {
    val allThemes: List<DaisyTheme> = listOf(
        DaisyTheme("dark", "Dark", "#00d68f"),
        DaisyTheme("light", "Light", "#570df8"),
        DaisyTheme("cupcake", "Cupcake", "#65c3c8"),
        DaisyTheme("bumblebee", "Bumblebee", "#f59e0b"),
        DaisyTheme("emerald", "Emerald", "#66cc8a"),
        DaisyTheme("corporate", "Corporate", "#4b6bfb"),
        DaisyTheme("synthwave", "Synthwave", "#e779e5"),
        DaisyTheme("retro", "Retro", "#ef4444"),
        DaisyTheme("cyberpunk", "Cyberpunk", "#ff00ff"),
        DaisyTheme("valentine", "Valentine", "#e06c9f"),
        DaisyTheme("halloween", "Halloween", "#f59e0b"),
        DaisyTheme("garden", "Garden", "#6ab879"),
        DaisyTheme("forest", "Forest", "#66cc8a"),
        DaisyTheme("aqua", "Aqua", "#094f5c"),
        DaisyTheme("lofi", "Lo-Fi", "#7e7e7e"),
        DaisyTheme("pastel", "Pastel", "#a680f1"),
        DaisyTheme("fantasy", "Fantasy", "#df00ff"),
        DaisyTheme("wireframe", "Wireframe", "#b8b8b8"),
        DaisyTheme("black", "Black", "#3b82f6"),
        DaisyTheme("luxury", "Luxury", "#d4af37"),
        DaisyTheme("dracula", "Dracula", "#ff79c6"),
        DaisyTheme("cmyk", "CMYK", "#4892ff"),
        DaisyTheme("autumn", "Autumn", "#e06c75"),
        DaisyTheme("business", "Business", "#1e40af"),
        DaisyTheme("acid", "Acid", "#aaff00"),
        DaisyTheme("lemonade", "Lemonade", "#eab308"),
        DaisyTheme("night", "Night", "#818cf8"),
        DaisyTheme("coffee", "Coffee", "#c4a882"),
        DaisyTheme("winter", "Winter", "#38bdf8"),
        DaisyTheme("dim", "Dim", "#6366f1"),
        DaisyTheme("nord", "Nord", "#5e81ac"),
        DaisyTheme("sunset", "Sunset", "#f472b6"),
    )

    fun isValidTheme(themeId: String): Boolean = allThemes.any { it.id == themeId }

    fun findTheme(themeId: String): DaisyTheme =
        allThemes.firstOrNull { it.id == themeId } ?: allThemes.first { it.id == "dark" }
}
