package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import java.util.Locale
import org.http4k.core.Request

class WebContext(val request: Request) {
    val lang: String by lazy {
        val language = request.query("lang")?.lowercase() ?: Locale.getDefault().language.lowercase()
        if (language == "fr") "fr" else "en"
    }

    val theme: String by lazy {
        val theme = request.query("theme")?.lowercase() ?: "dark"
        if (ThemeCatalog.allThemes().any { it.id == theme }) theme else "dark"
    }

    val layout: String by lazy {
        val layout = request.query("layout")?.lowercase() ?: "nice"
        if (listOf("nice", "cozy", "compact").any { it == layout }) layout else "nice"
    }

    val i18n: I18nService by lazy {
        I18nService.create("web-messages").also {
            it.setLocale(Locale.forLanguageTag(lang))
        }
    }

    fun url(path: String): String = "$path?lang=$lang&theme=$theme&layout=$layout"

    fun componentUrl(path: String, pagePath: String): String =
        "${url(path)}&pagePath=${if (pagePath.isBlank()) "/" else pagePath}"

    fun shell(pageTitle: String, activeSection: String): ShellView {
        val currentPath = if (request.uri.path.isBlank()) "/" else request.uri.path
        val themeCss = ThemeCatalog.toCssVariables(theme)
        val layoutClass = if (layout == "nice") "" else "layout-$layout"

        return ShellView(
            pageTitle = pageTitle,
            appTitle = i18n.translate("web.app.title"),
            appTagline = i18n.translate("web.app.tagline"),
            currentPath = currentPath,
            localeTag = lang,
            themeId = theme,
            themeCss = themeCss,
            layoutClass = layoutClass,
            navLinks = listOf(
                ShellLink(i18n.translate("web.nav.home"), url("/"), "ri-home-5-line", activeSection == "/"),
                ShellLink(i18n.translate("web.nav.auth"), url("/auth"), "ri-shield-keyhole-line", activeSection == "/auth"),
                ShellLink(i18n.translate("web.nav.errors"), url("/errors/not-found"), "ri-error-warning-line", activeSection == "/errors")
            ),
            themeSelectorUrl = componentUrl("/components/sidebar/theme-selector", currentPath),
            languageSelectorUrl = componentUrl("/components/sidebar/language-selector", currentPath),
            layoutSelectorUrl = componentUrl("/components/sidebar/layout-selector", currentPath),
            footerCopy = i18n.translate("web.footer.copy"),
            footerStatusUrl = url("/components/footer-status")
        )
    }
}
