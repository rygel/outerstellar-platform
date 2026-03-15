package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.UserRole
import java.util.Locale
import java.util.UUID
import org.http4k.core.Request
import org.http4k.core.cookie.cookie
import org.http4k.lens.RequestKey
import org.slf4j.LoggerFactory

class WebContext(
    val request: Request,
    private val devDashboardEnabled: Boolean = false,
    private val userRepository: UserRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(WebContext::class.java)

    companion object {
        val KEY = RequestKey.required<WebContext>("web.context")

        const val LANG_COOKIE = "app_lang"
        const val THEME_COOKIE = "app_theme"
        const val LAYOUT_COOKIE = "app_layout"
        const val SESSION_COOKIE = "app_session"
        const val CSRF_COOKIE = "_csrf"
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

    val user: User? by lazy {
        request.cookie(SESSION_COOKIE)?.value?.let { sessionUserId ->
            try {
                val uid = UUID.fromString(sessionUserId)
                userRepository?.findById(uid)
            } catch (e: IllegalArgumentException) {
                logger.debug("Invalid session cookie format: {}", e.message)
                null
            }
        }
    }

    val i18n: I18nService by lazy {
        I18nService.create("messages").also { it.setLocale(Locale.of(lang)) }
    }

    val csrfToken: String by lazy {
        request.cookie(CSRF_COOKIE)?.value ?: java.util.UUID.randomUUID().toString()
    }

    fun url(path: String): String = path

    fun componentUrl(path: String, pagePath: String): String =
        "${url(path)}?pagePath=${if (pagePath.isBlank()) "/" else pagePath}"

    @Suppress("LongMethod")
    fun shell(pageTitle: String, activeSection: String): ShellView {
        val currentPath = if (request.uri.path.isBlank()) "/" else request.uri.path
        val themeCss = ThemeCatalog.toCssVariables(theme)
        val layoutClass = if (layout == "nice") "" else "layout-$layout"

        val navLinks =
            mutableListOf(
                ShellLink(
                    i18n.translate("web.nav.home"),
                    url("/"),
                    "ri-home-5-line",
                    activeSection == "/",
                ),
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

        if (user?.role == UserRole.ADMIN) {
            navLinks.add(
                ShellLink(
                    i18n.translate("web.nav.users"),
                    url("/admin/users"),
                    "ri-group-line",
                    activeSection == "/admin/users",
                )
            )
            navLinks.add(
                ShellLink(
                    i18n.translate("web.nav.audit"),
                    url("/admin/audit"),
                    "ri-file-list-3-line",
                    activeSection == "/admin/audit",
                )
            )
        }

        if (devDashboardEnabled && user?.role == UserRole.ADMIN) {
            navLinks.add(
                ShellLink(
                    i18n.translate("web.nav.dev"),
                    url("/admin/dev"),
                    "ri-dashboard-line",
                    activeSection == "/admin/dev",
                )
            )
        }

        val isDark = theme == "dark"
        val toggleTheme = if (isDark) "default" else "dark"
        val darkModeToggleUrl = "$currentPath?theme=$toggleTheme"

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
            languageSelectorUrl =
                componentUrl("/components/sidebar/language-selector", currentPath),
            layoutSelectorUrl = componentUrl("/components/sidebar/layout-selector", currentPath),
            footerCopy = i18n.translate("web.footer.copy"),
            footerStatusUrl = url("/components/footer-status"),
            version = System.currentTimeMillis().toString(),
            userName = user?.username,
            isLoggedIn = user != null,
            logoutUrl = url("/logout"),
            changePasswordUrl = if (user != null) url("/auth/change-password") else null,
            profileUrl = if (user != null) url("/auth/profile") else null,
            isDarkMode = isDark,
            darkModeToggleUrl = darkModeToggleUrl,
            toastErrorLabel = i18n.translate("web.layout.toast.error"),
            toastSuccessLabel = i18n.translate("web.layout.toast.success"),
            changePasswordLabel = i18n.translate("web.layout.change.password"),
            signOutLabel = i18n.translate("web.layout.sign.out"),
            csrfToken = csrfToken,
            notificationsUrl = if (user != null) url("/notifications") else null,
        )
    }
}
