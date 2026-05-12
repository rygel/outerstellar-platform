package io.github.rygel.outerstellar.platform.web

class SettingsPageFactory {

    fun buildSettingsPage(ctx: WebContext, activeTab: String = "profile"): Page<SettingsPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.settings.title"), "/settings")
        val validTabs = listOf("profile", "password", "api-keys", "notifications", "appearance")
        val normalizedTab = if (activeTab in validTabs) activeTab else "profile"
        val tabs =
            listOf(
                SettingsTab(
                    "profile",
                    i18n.translate("web.settings.tab.profile"),
                    ctx.url("/settings?tab=profile"),
                    normalizedTab == "profile",
                ),
                SettingsTab(
                    "password",
                    i18n.translate("web.settings.tab.password"),
                    ctx.url("/settings?tab=password"),
                    normalizedTab == "password",
                ),
                SettingsTab(
                    "api-keys",
                    i18n.translate("web.settings.tab.api-keys"),
                    ctx.url("/settings?tab=api-keys"),
                    normalizedTab == "api-keys",
                ),
                SettingsTab(
                    "notifications",
                    i18n.translate("web.settings.tab.notifications"),
                    ctx.url("/settings?tab=notifications"),
                    normalizedTab == "notifications",
                ),
                SettingsTab(
                    "appearance",
                    i18n.translate("web.settings.tab.appearance"),
                    ctx.url("/settings?tab=appearance"),
                    normalizedTab == "appearance",
                ),
            )
        return Page(
            shell = shell,
            data =
                SettingsPage(
                    title = i18n.translate("web.settings.title"),
                    tabs = tabs,
                    activeTab = normalizedTab,
                    profileDescription = i18n.translate("web.settings.profile.description"),
                    passwordDescription = i18n.translate("web.settings.password.description"),
                    apiKeysDescription = i18n.translate("web.settings.apikeys.description"),
                    notificationsDescription = i18n.translate("web.settings.notifications.description"),
                    appearanceDescription = i18n.translate("web.settings.appearance.description"),
                ),
        )
    }
}
