package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.banner.Banner
import org.http4k.template.ViewModel

data class ShellLink(val label: String, val url: String, val icon: String, val active: Boolean)

data class ShellOption(val id: String, val label: String, val url: String, val active: Boolean)

data class HiddenField(val name: String, val value: String)

data class ShellView(
    val pageTitle: String,
    val appTitle: String,
    val appTagline: String,
    val currentPath: String,
    val localeTag: String,
    val themeName: String,
    val layoutClass: String,
    val navLinks: List<ShellLink>,
    val themeSelector: SidebarSelector,
    val languageSelector: SidebarSelector,
    val layoutSelector: SidebarSelector,
    val layoutStyle: String = "sidebar",
    val footerCopy: String,
    val footerVersion: String,
    val footerStatusUrl: String,
    val version: String,
    val username: String? = null,
    val isLoggedIn: Boolean = false,
    val logoutUrl: String? = null,
    val changePasswordUrl: String? = null,
    val profileUrl: String? = null,
    val toastErrorLabel: String = "Error",
    val toastSuccessLabel: String = "Success",
    val changePasswordLabel: String = "Change password",
    val signOutLabel: String = "Sign out",
    val csrfToken: String = "",
    val notificationsUrl: String? = null,
    val unreadNotificationCount: Int = 0,
    val textResolver: TextResolver? = null,
    val toggleMenuLabel: String = "Toggle menu",
    val profileLabel: String = "Profile",
    val notificationBellTitle: String = "Notifications",
    val searchPlaceholder: String = "Search...",
    val searchLabel: String = "Search",
    val pageDescription: String = "",
    val canonicalUrl: String = "",
    val noIndex: Boolean = false,
    val supportedLocales: List<String> = listOf("en"),
    val appBaseUrl: String = "",
    val ogImage: String = "",
    val banners: List<Banner> = emptyList(),
    val pluginLayoutRenderer: PluginLayoutRenderer? = null,
    val pluginStylesheets: List<String> = emptyList(),
    val pluginScripts: List<String> = emptyList(),
) {
    fun text(key: String, vararg args: Any?): String = textResolver?.resolve(key, *args) ?: key
}

data class SidebarSelector(
    val heading: String,
    val label: String,
    val selectId: String,
    val selectName: String,
    val options: List<ShellOption>,
    val hiddenFields: List<HiddenField>,
    val refreshUrl: String,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/components/SidebarSelector"
}
