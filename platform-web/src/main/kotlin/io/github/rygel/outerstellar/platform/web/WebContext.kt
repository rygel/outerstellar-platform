package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.I18nTextResolver
import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.SessionLookup
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRepository
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.http4k.core.Request
import org.http4k.core.cookie.cookie
import org.http4k.lens.RequestKey
import org.slf4j.LoggerFactory

class WebContext(
    val request: Request,
    private val devDashboardEnabled: Boolean = false,
    private val userRepository: UserRepository? = null,
    private val appVersion: String = "dev",
    private val jwtService: JwtService? = null,
    private val securityService: SecurityService? = null,
    private val pluginOptions: PluginOptions = PluginOptions(),
    private val appBaseUrl: String = "",
) {
    private val logger = LoggerFactory.getLogger(WebContext::class.java)

    companion object {
        val KEY = RequestKey.required<WebContext>("web.context")

        const val LANG_COOKIE = "app_lang"
        const val THEME_COOKIE = "app_theme"
        const val LAYOUT_COOKIE = "app_layout"
        const val SHELL_COOKIE = "app_shell"
        const val SESSION_COOKIE = "app_session"
        const val JWT_COOKIE = "app_jwt"
        const val CSRF_COOKIE = "_csrf"

        val SUPPORTED_LANGUAGES = setOf("en", "fr")
        val SUPPORTED_LAYOUTS = listOf("nice", "cozy", "compact")
        val SUPPORTED_SHELLS = listOf("sidebar", "topbar")

        private val NO_INDEX_SECTIONS =
            setOf(
                "/auth",
                "/auth/profile",
                "/auth/api-keys",
                "/errors",
                "/admin/users",
                "/admin/audit",
                "/admin/dev",
                "/admin/plugins",
                "/settings",
                "/notifications",
            )

        /** Stable cache-buster computed once at class load — survives the JVM lifetime. */
        val assetVersion: String = System.currentTimeMillis().toString()

        /** Cached I18nService per locale to avoid re-parsing .properties on every request. */
        private val i18nCache = ConcurrentHashMap<String, I18nService>()

        fun cachedI18n(lang: String): I18nService =
            i18nCache.computeIfAbsent(lang) { I18nService.create("messages").also { it.setLocale(Locale.of(lang)) } }
    }

    val lang: String by lazy {
        request.query("lang")
            ?: request.cookie(LANG_COOKIE)?.value
            ?: user?.language
            ?: Locale.getDefault().language.lowercase().let { if (it == "fr") "fr" else "en" }
    }

    val theme: String by lazy {
        val value = request.query("theme") ?: request.cookie(THEME_COOKIE)?.value ?: user?.theme ?: "dark"
        if (ThemeCatalog.isValidTheme(value)) value else "dark"
    }

    val layout: String by lazy {
        val value = request.query("layout") ?: request.cookie(LAYOUT_COOKIE)?.value ?: user?.layout ?: "nice"
        if (value in SUPPORTED_LAYOUTS) value else "nice"
    }

    val shellStyle: String by lazy {
        val value = request.query("shell") ?: request.cookie(SHELL_COOKIE)?.value ?: "sidebar"
        if (value in SUPPORTED_SHELLS) value else "sidebar"
    }

    val user: User? by lazy {
        request.cookie(SESSION_COOKIE)?.value?.let { rawToken ->
            when (val lookup = securityService?.lookupSession(rawToken)) {
                is SessionLookup.Active -> lookup.user
                SessionLookup.Expired -> null
                SessionLookup.NotFound -> null
                null -> null
            }
        }
            ?: request.cookie(JWT_COOKIE)?.value?.let { token ->
                jwtService?.extractClaims(token)?.let { (userId, _) ->
                    userRepository?.findById(userId)?.takeIf { it.enabled }
                }
            }
    }

    val sessionExpired: Boolean by lazy {
        request.cookie(SESSION_COOKIE)?.value?.let { rawToken ->
            securityService?.lookupSession(rawToken) is SessionLookup.Expired
        } ?: false
    }

    val i18n: I18nService by lazy { cachedI18n(lang) }

    val textResolver: TextResolver by lazy { pluginOptions.textResolver ?: I18nTextResolver(i18n) }

    val csrfToken: String by lazy { request.cookie(CSRF_COOKIE)?.value ?: java.util.UUID.randomUUID().toString() }

    fun url(path: String): String = path

    fun componentUrl(path: String, pagePath: String): String =
        "${url(path)}?pagePath=${if (pagePath.isBlank()) "/" else pagePath}"

    private fun buildNavLinks(activeSection: String): List<ShellLink> {
        val links: MutableList<ShellLink> =
            if (pluginOptions.navItems.isNotEmpty()) {
                // Plugin replaces the default nav; admin links are still appended below.
                pluginOptions.navItems
                    .map { item ->
                        ShellLink(item.label, url(item.url), item.icon, activeSection == item.activeSection)
                    }
                    .toMutableList()
            } else {
                mutableListOf(
                    ShellLink(i18n.translate("web.nav.home"), url("/"), "ri-home-5-line", activeSection == "/"),
                    ShellLink(
                        i18n.translate("web.nav.contacts"),
                        url("/contacts"),
                        "ri-user-3-line",
                        activeSection == "/contacts",
                    ),
                    ShellLink(
                        i18n.translate("web.nav.trash"),
                        url("/messages/trash"),
                        "ri-delete-bin-7-line",
                        activeSection == "/messages/trash",
                    ),
                    ShellLink(
                        i18n.translate("web.nav.auth"),
                        url("/auth"),
                        "ri-shield-keyhole-line",
                        activeSection == "/auth",
                    ),
                    ShellLink(
                        i18n.translate("web.nav.errors"),
                        url("/errors/not-found"),
                        "ri-error-warning-line",
                        activeSection == "/errors",
                    ),
                )
            }

        appendAdminLinks(links, activeSection)
        return links
    }

    private fun appendAdminLinks(links: MutableList<ShellLink>, activeSection: String) {
        if (user?.role != UserRole.ADMIN) return
        links.add(
            ShellLink(
                i18n.translate("web.nav.users"),
                url("/admin/users"),
                "ri-group-line",
                activeSection == "/admin/users",
            )
        )
        links.add(
            ShellLink(
                i18n.translate("web.nav.audit"),
                url("/admin/audit"),
                "ri-file-list-3-line",
                activeSection == "/admin/audit",
            )
        )
        if (devDashboardEnabled) {
            links.add(
                ShellLink(
                    i18n.translate("web.nav.dev"),
                    url("/admin/dev"),
                    "ri-dashboard-line",
                    activeSection == "/admin/dev",
                )
            )
        }
        pluginOptions.adminNavItems.forEach { item ->
            links.add(ShellLink(item.label, url(item.url), item.icon, activeSection == item.url))
        }
    }

    @Suppress("LongMethod")
    fun shell(pageTitle: String, activeSection: String): ShellView {
        val currentPath = if (request.uri.path.isBlank()) "/" else request.uri.path
        val layoutClass = if (layout == "nice") "" else "layout-$layout"
        val navLinks = buildNavLinks(activeSection)

        return ShellView(
            pageTitle = pageTitle,
            appTitle = i18n.translate("web.app.title"),
            appTagline = i18n.translate("web.app.tagline"),
            currentPath = currentPath,
            localeTag = lang,
            themeName = theme,
            layoutClass = layoutClass,
            layoutStyle = shellStyle,
            navLinks = navLinks,
            themeSelector =
                SidebarSelector(
                    heading = i18n.translate("web.sidebar.themes"),
                    label = i18n.translate("web.sidebar.theme.label"),
                    selectId = "theme-selector",
                    selectName = "theme",
                    options =
                        ThemeCatalog.allThemes.map { t ->
                            ShellOption(id = t.id, label = t.label, url = t.id, active = t.id == theme)
                        },
                    hiddenFields =
                        listOf(
                            HiddenField("pagePath", currentPath),
                            HiddenField("lang", lang),
                            HiddenField("layout", layout),
                        ),
                    refreshUrl = "/components/navigation/page",
                ),
            languageSelector =
                SidebarSelector(
                    heading = i18n.translate("web.sidebar.language"),
                    label = i18n.translate("web.sidebar.language.label"),
                    selectId = "language-selector",
                    selectName = "lang",
                    options =
                        listOf("en" to "web.language.english", "fr" to "web.language.french").map { (id, key) ->
                            ShellOption(id, i18n.translate(key), id, id == lang)
                        },
                    hiddenFields =
                        listOf(
                            HiddenField("pagePath", currentPath),
                            HiddenField("theme", theme),
                            HiddenField("layout", layout),
                        ),
                    refreshUrl = "/components/navigation/page",
                ),
            layoutSelector =
                SidebarSelector(
                    heading = i18n.translate("web.sidebar.layout"),
                    label = i18n.translate("web.sidebar.layout.label"),
                    selectId = "layout-selector",
                    selectName = "layout",
                    options =
                        listOf(
                                "nice" to "web.layout.nice",
                                "cozy" to "web.layout.cozy",
                                "compact" to "web.layout.compact",
                            )
                            .map { (id, key) -> ShellOption(id, i18n.translate(key), id, id == layout) },
                    hiddenFields =
                        listOf(
                            HiddenField("pagePath", currentPath),
                            HiddenField("theme", theme),
                            HiddenField("lang", lang),
                        ),
                    refreshUrl = "/components/navigation/page",
                ),
            footerCopy = i18n.translate("web.footer.copy"),
            footerVersion = i18n.translate("web.footer.version", appVersion),
            footerStatusUrl = url("/components/footer-status"),
            version = assetVersion,
            username = user?.username,
            isLoggedIn = user != null,
            logoutUrl = url("/logout"),
            changePasswordUrl = if (user != null) url("/auth/change-password") else null,
            profileUrl = if (user != null) url("/auth/profile") else null,
            toastErrorLabel = i18n.translate("web.layout.toast.error"),
            toastSuccessLabel = i18n.translate("web.layout.toast.success"),
            changePasswordLabel = i18n.translate("web.layout.change.password"),
            signOutLabel = i18n.translate("web.layout.sign.out"),
            csrfToken = csrfToken,
            notificationsUrl = if (user != null) url("/notifications") else null,
            textResolver = textResolver,
            pageDescription =
                i18n
                    .translate("web.page.description.$activeSection")
                    .takeIf { !it.startsWith("web.page.description.") }
                    .orEmpty(),
            canonicalUrl = if (appBaseUrl.isNotBlank()) "$appBaseUrl$currentPath" else "",
            noIndex = activeSection in NO_INDEX_SECTIONS,
            supportedLocales = listOf("en", "fr"),
            appBaseUrl = appBaseUrl,
        )
    }
}
