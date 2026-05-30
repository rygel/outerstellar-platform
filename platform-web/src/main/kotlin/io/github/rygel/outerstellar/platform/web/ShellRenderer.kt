package io.github.rygel.outerstellar.platform.web

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.I18nTextResolver
import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.http4k.lens.RequestKey
import org.slf4j.LoggerFactory

class ShellRenderer(
    private val ctx: RequestContext,
    private val devDashboardEnabled: Boolean = false,
    private val appVersion: String = "dev",
    private val shellConfig: ShellConfig = ShellConfig(),
) {
    private val pluginOptions
        get() = shellConfig.pluginOptions

    private val appBaseUrl
        get() = shellConfig.appBaseUrl

    private val sidebarFactory
        get() = shellConfig.sidebarFactory

    private val bannerProviders
        get() = shellConfig.bannerProviders

    private val mode
        get() = shellConfig.mode

    private val includedPlatformPages
        get() = shellConfig.includedPlatformPages

    private val logger = LoggerFactory.getLogger(ShellRenderer::class.java)

    companion object {
        val KEY = RequestKey.required<ShellRenderer>("shell.renderer")

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

        private val i18nCache = ConcurrentHashMap<String, I18nService>()

        private val navLinkCache: Cache<String, List<ShellLink>> =
            Caffeine.newBuilder().maximumSize(500).expireAfterWrite(5, TimeUnit.MINUTES).build()

        fun cachedI18n(lang: String): I18nService =
            i18nCache.computeIfAbsent(lang) { I18nService.create("messages").also { it.setLocale(Locale.of(lang)) } }
    }

    val i18n: I18nService by lazy { cachedI18n(ctx.lang) }

    val textResolver: TextResolver by lazy { pluginOptions.textResolver ?: I18nTextResolver(i18n) }

    private val isPluginHostedApp: Boolean
        get() = mode == PlatformMode.PluginHostedApp

    fun url(path: String): String = path

    fun componentUrl(path: String, pagePath: String): String =
        "${url(path)}?pagePath=${if (pagePath.isBlank()) "/" else pagePath}"

    private fun buildNavLinks(activeSection: String): List<ShellLink> {
        val role = ctx.user?.role?.name ?: "ANONYMOUS"
        val navKey = if (pluginOptions.navItems.isNotEmpty()) pluginOptions.navItems.hashCode() else 0
        val adminNavKey = if (pluginOptions.adminNavItems.isNotEmpty()) pluginOptions.adminNavItems.hashCode() else 0
        val includedKey = includedPlatformPages.map { it.name }.sorted().joinToString(",")
        val cacheKey = "${ctx.lang}:$role:$activeSection:$devDashboardEnabled:$mode:$navKey:$adminNavKey:$includedKey"
        return navLinkCache.get(cacheKey) { buildNavLinksUncached(activeSection) }
    }

    @Suppress("LongMethod")
    private fun buildNavLinksUncached(activeSection: String): List<ShellLink> {
        val links: MutableList<ShellLink> =
            if (pluginOptions.navItems.isNotEmpty()) {
                pluginOptions.navItems
                    .map { item ->
                        ShellLink(item.label, url(item.url), item.icon, activeSection == item.activeSection)
                    }
                    .toMutableList()
            } else {
                defaultPlatformNavLinks(activeSection)
            }

        appendAdminLinks(links, activeSection)
        return links
    }

    private fun defaultPlatformNavLinks(activeSection: String): MutableList<ShellLink> =
        if (isPluginHostedApp) {
            buildPluginHostedNavLinks(activeSection)
        } else {
            buildFullPlatformNavLinks(activeSection)
        }

    private fun buildFullPlatformNavLinks(activeSection: String): MutableList<ShellLink> =
        mutableListOf(
                ShellLink(i18n.translate("web.nav.home"), url("/"), "ri-home-5-line", activeSection == "/"),
                ShellLink(
                    i18n.translate("web.nav.search"),
                    url("/search"),
                    "ri-search-line",
                    activeSection == "/search",
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
            )
            .also { navLinks ->
                if (ctx.user != null) {
                    navLinks.add(
                        ShellLink(
                            i18n.translate("web.nav.settings"),
                            url("/settings"),
                            "ri-settings-3-line",
                            activeSection == "/settings",
                        )
                    )
                }
            }

    private fun buildPluginHostedNavLinks(activeSection: String): MutableList<ShellLink> {
        val links = mutableListOf<ShellLink>()
        if (includes(PlatformPageSets.HOME)) {
            links.add(ShellLink(i18n.translate("web.nav.home"), url("/"), "ri-home-5-line", activeSection == "/"))
            links.add(
                ShellLink(
                    i18n.translate("web.nav.trash"),
                    url("/messages/trash"),
                    "ri-delete-bin-7-line",
                    activeSection == "/messages/trash",
                )
            )
        }
        if (includes(PlatformPageSets.SEARCH)) {
            links.add(
                ShellLink(
                    i18n.translate("web.nav.search"),
                    url("/search"),
                    "ri-search-line",
                    activeSection == "/search",
                )
            )
        }
        if (includes(PlatformPageSets.CONTACTS)) {
            links.add(
                ShellLink(
                    i18n.translate("web.nav.contacts"),
                    url("/contacts"),
                    "ri-user-3-line",
                    activeSection == "/contacts",
                )
            )
        }
        if (ctx.user != null && includes(PlatformPageSets.SETTINGS)) {
            links.add(
                ShellLink(
                    i18n.translate("web.nav.settings"),
                    url("/settings"),
                    "ri-settings-3-line",
                    activeSection == "/settings",
                )
            )
        }
        return links
    }

    private fun appendAdminLinks(links: MutableList<ShellLink>, activeSection: String) {
        if (ctx.user?.role != UserRole.ADMIN) return
        if (!isPluginHostedApp || includes(PlatformPageSets.ADMIN)) {
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
        }
        if (devDashboardEnabled && (!isPluginHostedApp || includes(PlatformPageSets.DEV_DASHBOARD))) {
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

    private fun includes(pageSet: PlatformPageSets): Boolean = pageSet in includedPlatformPages

    private fun appTitle(): String =
        if (isPluginHostedApp) {
            shellConfig.appLabel.ifBlank { i18n.translate("web.app.title") }
        } else {
            i18n.translate("web.app.title")
        }

    private fun footerVersion(): String =
        if (isPluginHostedApp) "v$appVersion" else i18n.translate("web.footer.version", appVersion)

    private fun searchUrl(): String? =
        if (!isPluginHostedApp || includes(PlatformPageSets.SEARCH)) url("/search") else null

    @Suppress("LongMethod")
    fun shell(pageTitle: String, activeSection: String): ShellView {
        val request = ctx.request
        val currentPath = if (request.uri.path.isBlank()) "/" else request.uri.path
        val layoutClass = if (ctx.layout == "nice") "" else "layout-${ctx.layout}"
        val navLinks = buildNavLinks(activeSection)
        val user = ctx.user
        val banners =
            if (user != null && bannerProviders.isNotEmpty()) {
                bannerProviders.flatMap { it.getBanners(user.id, user.role.name) }.sortedBy { it.severity.ordinal }
            } else {
                emptyList()
            }

        return ShellView(
            pageTitle = pageTitle,
            appTitle = appTitle(),
            appTagline = if (isPluginHostedApp) appTitle() else i18n.translate("web.app.tagline"),
            currentPath = currentPath,
            localeTag = ctx.lang,
            themeName = ctx.theme,
            layoutClass = layoutClass,
            layoutStyle = ctx.shellStyle,
            navLinks = navLinks,
            themeSelector = sidebarFactory.buildThemeSelector(ctx, this),
            languageSelector = sidebarFactory.buildLanguageSelector(ctx, this),
            layoutSelector = sidebarFactory.buildLayoutSelector(ctx, this),
            footerCopy = if (isPluginHostedApp) appTitle() else i18n.translate("web.footer.copy"),
            footerVersion = footerVersion(),
            footerStatusUrl = url("/components/footer-status"),
            version = appVersion,
            username = user?.username,
            isLoggedIn = user != null,
            logoutUrl = url("/logout"),
            changePasswordUrl = if (user != null && !isPluginHostedApp) url("/auth/change-password") else null,
            profileUrl =
                if (user != null && (!isPluginHostedApp || includes(PlatformPageSets.PROFILE))) url("/auth/profile")
                else null,
            toastErrorLabel = i18n.translate("web.layout.toast.error"),
            toastSuccessLabel = i18n.translate("web.layout.toast.success"),
            changePasswordLabel = i18n.translate("web.layout.change.password"),
            signOutLabel = i18n.translate("web.layout.sign.out"),
            csrfToken = ctx.csrfToken,
            searchUrl = searchUrl(),
            searchPlaceholder = i18n.translate("web.search.placeholder"),
            searchLabel = i18n.translate("web.search.label"),
            notificationsUrl =
                if (user != null && (!isPluginHostedApp || includes(PlatformPageSets.NOTIFICATIONS))) {
                    url("/notifications")
                } else {
                    null
                },
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
            appHomeUrl = if (isPluginHostedApp) shellConfig.appHomeUrl else url("/"),
            banners = banners,
            pluginLayoutRenderer = pluginOptions.layoutRenderer,
            pluginStylesheets = pluginOptions.assets.stylesheets,
            pluginScripts = pluginOptions.assets.scripts,
        )
    }
}
