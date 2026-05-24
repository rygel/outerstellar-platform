package io.github.rygel.outerstellar.platform.web

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

class SidebarFactory {
    private val selectorCache: Cache<String, SidebarSelector> =
        Caffeine.newBuilder().maximumSize(500).expireAfterWrite(5, TimeUnit.MINUTES).build()

    private fun cacheKey(type: String, ctx: RequestContext): String {
        val pagePath = ctx.request.query("pagePath").orEmpty().ifBlank { ctx.request.uri.path }
        return "$type:${ctx.lang}:${ctx.theme}:${ctx.layout}:$pagePath"
    }

    fun buildThemeSelector(ctx: RequestContext, renderer: ShellRenderer): SidebarSelector =
        selectorCache.get(cacheKey("theme", ctx)) { buildThemeSelectorInner(ctx, renderer) }

    fun buildLanguageSelector(ctx: RequestContext, renderer: ShellRenderer): SidebarSelector =
        selectorCache.get(cacheKey("lang", ctx)) { buildLanguageSelectorInner(ctx, renderer) }

    fun buildLayoutSelector(ctx: RequestContext, renderer: ShellRenderer): SidebarSelector =
        selectorCache.get(cacheKey("layout", ctx)) { buildLayoutSelectorInner(ctx, renderer) }

    private fun resolvePagePath(ctx: RequestContext): String =
        ctx.request.query("pagePath").orEmpty().ifBlank { ctx.request.uri.path }

    private fun buildThemeSelectorInner(ctx: RequestContext, renderer: ShellRenderer): SidebarSelector {
        val i18n = renderer.i18n
        val pagePath = resolvePagePath(ctx)
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

    private fun buildLanguageSelectorInner(ctx: RequestContext, renderer: ShellRenderer): SidebarSelector {
        val i18n = renderer.i18n
        val pagePath = resolvePagePath(ctx)
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

    private fun buildLayoutSelectorInner(ctx: RequestContext, renderer: ShellRenderer): SidebarSelector {
        val i18n = renderer.i18n
        val pagePath = resolvePagePath(ctx)
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
