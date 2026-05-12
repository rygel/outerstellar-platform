package io.github.rygel.outerstellar.platform.web

class SidebarFactory {

    fun buildThemeSelector(ctx: WebContext): SidebarSelector {
        val i18n = ctx.i18n
        val pagePath = ctx.request.query("pagePath").orEmpty()

        return SidebarSelector(
            heading = i18n.translate("web.sidebar.themes"),
            label = i18n.translate("web.sidebar.theme.label"),
            selectId = "theme-selector",
            selectName = "theme",
            options =
                ThemeCatalog.allThemes.map { theme ->
                    ShellOption(id = theme.id, label = theme.label, url = theme.id, active = theme.id == ctx.theme)
                },
            hiddenFields =
                listOf(
                    HiddenField("pagePath", pagePath),
                    HiddenField("lang", ctx.lang),
                    HiddenField("layout", ctx.layout),
                ),
            refreshUrl = "/components/navigation/page",
        )
    }

    fun buildLanguageSelector(ctx: WebContext): SidebarSelector {
        val i18n = ctx.i18n
        val pagePath = ctx.request.query("pagePath").orEmpty()

        return SidebarSelector(
            heading = i18n.translate("web.sidebar.language"),
            label = i18n.translate("web.sidebar.language.label"),
            selectId = "language-selector",
            selectName = "lang",
            options =
                listOf("en" to "web.language.english", "fr" to "web.language.french").map { (id, key) ->
                    ShellOption(id, i18n.translate(key), id, id == ctx.lang)
                },
            hiddenFields =
                listOf(
                    HiddenField("pagePath", pagePath),
                    HiddenField("theme", ctx.theme),
                    HiddenField("layout", ctx.layout),
                ),
            refreshUrl = "/components/navigation/page",
        )
    }

    fun buildLayoutSelector(ctx: WebContext): SidebarSelector {
        val i18n = ctx.i18n
        val pagePath = ctx.request.query("pagePath").orEmpty()

        return SidebarSelector(
            heading = i18n.translate("web.sidebar.layout"),
            label = i18n.translate("web.sidebar.layout.label"),
            selectId = "layout-selector",
            selectName = "layout",
            options =
                listOf("nice" to "web.layout.nice", "cozy" to "web.layout.cozy", "compact" to "web.layout.compact")
                    .map { (id, key) -> ShellOption(id, i18n.translate(key), id, id == ctx.layout) },
            hiddenFields =
                listOf(
                    HiddenField("pagePath", pagePath),
                    HiddenField("theme", ctx.theme),
                    HiddenField("lang", ctx.lang),
                ),
            refreshUrl = "/components/navigation/page",
        )
    }
}
