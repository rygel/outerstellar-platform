package io.github.rygel.outerstellar.platform.model

object ThemeCatalog {
    fun allThemeIds(): List<String> = listOf(
        "dark", "light", "cupcake", "bumblebee", "emerald", "corporate",
        "synthwave", "retro", "cyberpunk", "valentine", "halloween", "garden",
        "forest", "aqua", "lofi", "pastel", "fantasy", "wireframe", "black",
        "luxury", "dracula", "cmyk", "autumn", "business", "acid", "lemonade",
        "night", "coffee", "winter", "dim", "nord", "sunset",
    )

    fun isValidTheme(themeId: String): Boolean = allThemeIds().contains(themeId)
}
