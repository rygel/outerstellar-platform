package io.github.rygel.outerstellar.platform.web

class SettingsPageFactory(
    private val adminPageFactory: AdminPageFactory? = null,
    private val authPageFactory: AuthPageFactory? = null,
    private val sidebarFactory: SidebarFactory? = null,
) {
    fun buildSettingsPage(ctx: WebContext, activeTab: String = "profile"): Page<SettingsPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.settings.title"), "/settings")
        val validTabs = listOf("profile", "password", "security", "api-keys", "notifications", "appearance")
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
                    "security",
                    i18n.translate("web.settings.tab.security"),
                    ctx.url("/settings?tab=security"),
                    normalizedTab == "security",
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
            data = SettingsPage(title = i18n.translate("web.settings.title"), tabs = tabs, activeTab = normalizedTab),
        )
    }

    fun buildSettingsFragment(ctx: WebContext, tab: String): SettingsTabContent {
        val i18n = ctx.i18n
        val validTabs = listOf("profile", "password", "security", "api-keys", "notifications", "appearance")
        val normalizedTab = if (tab in validTabs) tab else "profile"
        val user = ctx.user
        val csrfToken = ctx.csrfToken

        val profileData =
            if (normalizedTab == "profile" && user != null) {
                val profilePage = adminPageFactory?.buildProfilePage(ctx)
                profilePage?.data
            } else null

        val passwordForm =
            if (normalizedTab == "password") {
                authPageFactory?.buildChangePasswordForm(ctx)
            } else null

        val apiKeysData =
            if (normalizedTab == "api-keys" && user != null) {
                val apiKeysPage = adminPageFactory?.buildApiKeysPage(ctx)
                apiKeysPage?.data
            } else null

        val notifData =
            if (normalizedTab == "notifications" && user != null) {
                val notifPage = adminPageFactory?.buildNotificationsPage(ctx)
                notifPage?.data
            } else null

        val profileEmailNotificationsEnabled = profileData?.emailNotificationsEnabled ?: false
        val profilePushNotificationsEnabled = profileData?.pushNotificationsEnabled ?: false

        return SettingsTabContent(
            activeTab = normalizedTab,
            csrfToken = csrfToken,
            // Profile
            profileUsername = profileData?.username ?: "",
            profileEmail = profileData?.email ?: "",
            profileAvatarUrl = profileData?.avatarUrl ?: "",
            profileAvatarAlt = profileData?.avatarAlt ?: "Avatar",
            profileSubmitUrl = profileData?.submitUrl ?: "",
            profileUsernameLabel = profileData?.usernameLabel ?: "",
            profileUsernamePlaceholder = profileData?.usernamePlaceholder ?: "",
            profileEmailLabel = profileData?.emailLabel ?: "",
            profileEmailPlaceholder = profileData?.emailPlaceholder ?: "",
            profileAvatarLabel = profileData?.avatarLabel ?: "",
            profileAvatarPlaceholder = profileData?.avatarPlaceholder ?: "",
            profileSubmitLabel = profileData?.submitLabel ?: "",
            profileGravatarHint = profileData?.gravatarHint ?: "",
            profileNotifPrefsUrl = profileData?.notificationPrefsUrl ?: "",
            profileNotifPrefsLabel = profileData?.notificationPrefsLabel ?: "",
            profileEmailNotifLabel = profileData?.emailNotifLabel ?: "",
            profilePushNotifLabel = profileData?.pushNotifLabel ?: "",
            profileSavePrefsLabel = profileData?.savePrefsLabel ?: "",
            profileEmailNotificationsEnabled = profileEmailNotificationsEnabled,
            profilePushNotificationsEnabled = profilePushNotificationsEnabled,
            profileDangerZoneLabel = profileData?.dangerZoneLabel ?: "",
            profileDeleteAccountUrl = profileData?.deleteAccountUrl ?: "",
            profileDeleteAccountLabel = profileData?.deleteAccountLabel ?: "",
            profileDeleteAccountDescription = profileData?.deleteAccountDescription ?: "",
            profileDeleteAccountConfirmLabel = profileData?.deleteAccountConfirmLabel ?: "",
            profileDeleteAccountCancelLabel = profileData?.deleteAccountCancelLabel ?: "",
            // Password
            passwordTitle = passwordForm?.title ?: "",
            passwordCurrentPasswordLabel = passwordForm?.currentPasswordLabel ?: "",
            passwordNewPasswordLabel = passwordForm?.newPasswordLabel ?: "",
            passwordConfirmPasswordLabel = passwordForm?.confirmPasswordLabel ?: "",
            passwordSubmitLabel = passwordForm?.submitLabel ?: "",
            passwordSubmitUrl = passwordForm?.submitUrl ?: "",
            passwordCurrentPasswordPlaceholder = passwordForm?.currentPasswordPlaceholder ?: "",
            passwordNewPasswordPlaceholder = passwordForm?.newPasswordPlaceholder ?: "",
            passwordConfirmPasswordPlaceholder = passwordForm?.confirmPasswordPlaceholder ?: "",
            // API Keys
            apiKeys = apiKeysData?.keys ?: emptyList(),
            apiKeysCreateUrl = apiKeysData?.createUrl ?: "",
            apiKeysNewKey = apiKeysData?.newKey,
            apiKeysNewKeyName = apiKeysData?.newKeyName,
            apiKeysCreateLabel = apiKeysData?.createLabel ?: "",
            apiKeysKeyNameLabel = apiKeysData?.keyNameLabel ?: "",
            apiKeysKeyNamePlaceholder = apiKeysData?.keyNamePlaceholder ?: "",
            apiKeysYourKeysHeading = apiKeysData?.yourKeysHeading ?: "",
            apiKeysEmptyLabel = apiKeysData?.emptyLabel ?: "",
            apiKeysHeaderPrefix = apiKeysData?.headerPrefix ?: "",
            apiKeysHeaderName = apiKeysData?.headerName ?: "",
            apiKeysHeaderCreated = apiKeysData?.headerCreated ?: "",
            apiKeysHeaderLastUsed = apiKeysData?.headerLastUsed ?: "",
            apiKeysHeaderActions = apiKeysData?.headerActions ?: "",
            apiKeysNeverLabel = apiKeysData?.neverLabel ?: "",
            apiKeysDeleteConfirm = apiKeysData?.deleteConfirm ?: "",
            apiKeysDeleteLabel = apiKeysData?.deleteLabel ?: "",
            // Notifications
            notifications = notifData?.notifications ?: emptyList(),
            notifUnreadCount = notifData?.unreadCount ?: 0,
            notifMarkAllReadUrl = notifData?.markAllReadUrl ?: "",
            notifMarkAllReadLabel = notifData?.markAllReadLabel ?: "",
            notifMarkReadLabel = notifData?.markReadLabel ?: "",
            notifReadLabel = notifData?.readLabel ?: "",
            notifNewLabel = notifData?.newLabel ?: "",
            notifEmptyLabel = notifData?.emptyLabel ?: "",
            // Appearance
            themeSelector = if (normalizedTab == "appearance") sidebarFactory?.buildThemeSelector(ctx) else null,
            languageSelector = if (normalizedTab == "appearance") sidebarFactory?.buildLanguageSelector(ctx) else null,
            layoutSelector = if (normalizedTab == "appearance") sidebarFactory?.buildLayoutSelector(ctx) else null,
        )
    }
}
