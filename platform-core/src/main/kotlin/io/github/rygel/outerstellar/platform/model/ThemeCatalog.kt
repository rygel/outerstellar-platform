package io.github.rygel.outerstellar.platform.model

object ThemeCatalog {
    private val themeIds: List<String> =
        listOf(
            "dark",
            "light",
            "cupcake",
            "bumblebee",
            "emerald",
            "corporate",
            "synthwave",
            "retro",
            "cyberpunk",
            "valentine",
            "halloween",
            "garden",
            "forest",
            "aqua",
            "lofi",
            "pastel",
            "fantasy",
            "wireframe",
            "black",
            "luxury",
            "dracula",
            "cmyk",
            "autumn",
            "business",
            "acid",
            "lemonade",
            "night",
            "coffee",
            "winter",
            "dim",
            "nord",
            "sunset",
        )

    private val themeIdSet: Set<String> = themeIds.toSet()

    fun allThemeIds(): List<String> = themeIds

    fun isValidTheme(themeId: String): Boolean = themeId in themeIdSet
}
