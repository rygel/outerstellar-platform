package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import java.util.Locale
import org.http4k.core.Request
import org.http4k.core.RequestContexts
import org.http4k.core.cookie.cookie
import org.http4k.lens.RequestContextKey

class WebContext(val request: Request, private val devDashboardEnabled: Boolean = false) {
    companion object {
        val contexts = RequestContexts()
        val KEY = RequestContextKey.required<WebContext>(contexts)
        
        const val LANG_COOKIE = "app_lang"
        const val THEME_COOKIE = "app_theme"
        const val LAYOUT_COOKIE = "app_layout"
    }
    
    val lang: String by lazy {
        request.query("lang") 
            ?: request.cookie(LANG_COOKIE)?.value 
            ?: Locale.getDefault().language.lowercase().let { if (it == "fr") "fr" else "en" }
    }

    val theme: String by lazy {
        val value = request.query("theme") ?: request.cookie(THEME_COOKIE)?.value ?: "dark"
        if (ThemeCatalog.allThemes().any { it.id == value }) value else "dark"
    }

    val layout: String by lazy {
        val value = request.query("layout") ?: request.cookie(LAYOUT_COOKIE)?.value ?: "nice"
        if (listOf("nice", "cozy", "compact").any { it == value }) value else "nice"
    }

    val i18n: I18nService by lazy {
        I18nService.create("messages").also {
            it.setLocale(Locale.forLanguageTag(lang))
        }
    }

    /**
     * Build a clean URL. Since settings are in cookies, we no longer need query params!
     */
    fun url(path: String): String = path

    fun componentUrl(path: String, pagePath: String): String =
        "$path?pagePath=${if (pagePath.isBlank()) "/" else pagePath}"

    fun shell(pageTitle: String, activeSection: String): ShellView {
        val currentPath = if (request.uri.path.isBlank()) "/" else request.uri.path
        val themeCss = ThemeCatalog.toCssVariables(theme)
        val layoutClass = if (layout == "nice") "" else "layout-$layout"

        val navLinks = mutableListOf(
            ShellLink(i18n.translate("web.nav.home"), url("/"), "ri-home-5-line", activeSection == "/"),
            ShellLink(i18n.translate("web.nav.auth"), url("/auth"), "ri-shield-keyhole-line", activeSection == "/auth"),
            ShellLink(i18n.translate("web.nav.errors"), url("/errors/not-found"), "ri-error-warning-line", activeSection == "/errors")
        )

        if (devDashboardEnabled) {
            navLinks.add(ShellLink(i18n.translate("web.nav.dev"), url("/admin/dev"), "ri-dashboard-line", activeSection == "/admin/dev"))
        }

        return ShellView(
            pageTitle = pageTitle,
            appTitle = i18n.translate("web.app.title"),
            appTagline = i18n.translate("web.app.tagline"),
            currentPath = currentPath,
            localeTag = lang,
            themeId = theme,
            themeCss = themeCss,
            layoutClass = layoutClass,
            navLinks = navLinks,
            themeSelectorUrl = componentUrl("/components/sidebar/theme-selector", currentPath),
            languageSelectorUrl = componentUrl("/components/sidebar/language-selector", currentPath),
            layoutSelectorUrl = componentUrl("/components/sidebar/layout-selector", currentPath),
            footerCopy = i18n.translate("web.footer.copy"),
            footerStatusUrl = url("/components/footer-status")
        )
    }
}
