package io.github.rygel.outerstellar.platform.web

class SettingsPageFactory(
    private val adminPageFactory: AdminPageFactory? = null,
    private val authPageFactory: AuthPageFactory? = null,
    private val sidebarFactory: SidebarFactory? = null,
) {
    fun buildSettingsPage(shellRenderer: ShellRenderer, activeTab: String = "profile"): Page<SettingsPage> {
        val i18n = shellRenderer.i18n
        val shell = shellRenderer.shell(i18n.translate("web.settings.title"), "/settings")
        val validTabs = listOf("profile", "password", "security", "api-keys", "notifications", "appearance")
        val normalizedTab = if (activeTab in validTabs) activeTab else "profile"
        val tabs =
            listOf(
                SettingsTab(
                    "profile",
                    i18n.translate("web.settings.tab.profile"),
                    shellRenderer.url("/settings?tab=profile"),
                    normalizedTab == "profile",
                ),
                SettingsTab(
                    "password",
                    i18n.translate("web.settings.tab.password"),
                    shellRenderer.url("/settings?tab=password"),
                    normalizedTab == "password",
                ),
                SettingsTab(
                    "security",
                    i18n.translate("web.settings.tab.security"),
                    shellRenderer.url("/settings?tab=security"),
                    normalizedTab == "security",
                ),
                SettingsTab(
                    "api-keys",
                    i18n.translate("web.settings.tab.api-keys"),
                    shellRenderer.url("/settings?tab=api-keys"),
                    normalizedTab == "api-keys",
                ),
                SettingsTab(
                    "notifications",
                    i18n.translate("web.settings.tab.notifications"),
                    shellRenderer.url("/settings?tab=notifications"),
                    normalizedTab == "notifications",
                ),
                SettingsTab(
                    "appearance",
                    i18n.translate("web.settings.tab.appearance"),
                    shellRenderer.url("/settings?tab=appearance"),
                    normalizedTab == "appearance",
                ),
            )
        return Page(
            shell = shell,
            data = SettingsPage(title = i18n.translate("web.settings.title"), tabs = tabs, activeTab = normalizedTab),
        )
    }

    fun buildSettingsFragment(ctx: RequestContext, shellRenderer: ShellRenderer, tab: String): SettingsTabContent {
        val normalizedTab = normalizeTab(tab)
        val csrfToken = ctx.csrfToken.orEmpty()
        return when (normalizedTab) {
            "password" -> buildPasswordTab(shellRenderer, csrfToken)
            "api-keys" -> buildApiKeysTab(ctx, shellRenderer, csrfToken)
            "notifications" -> buildNotificationsTab(ctx, shellRenderer, csrfToken)
            "appearance" -> buildAppearanceTab(ctx, shellRenderer, csrfToken)
            else -> buildProfileTab(ctx, shellRenderer, csrfToken)
        }
    }

    private fun buildProfileTab(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        csrfToken: String,
    ): SettingsTabContent {
        val i18n = shellRenderer.i18n
        val data = ctx.user?.let { adminPageFactory?.buildProfilePage(ctx, shellRenderer)?.data }
        return profileForm(csrfToken, data, i18n, ctx, shellRenderer)
            .let { profileLabels(it, data, i18n) }
            .let { profileNotifications(it, data, i18n) }
            .let { profileDangerZone(it, data, i18n) }
    }

    private fun profileForm(
        csrfToken: String,
        data: ProfilePage?,
        i18n: io.github.rygel.outerstellar.i18n.I18nService,
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
    ): SettingsTabContent =
        SettingsTabContent(
            activeTab = "profile",
            csrfToken = csrfToken,
            profileUsername = data?.username ?: ctx.user?.username ?: "",
            profileEmail = data?.email ?: ctx.user?.email ?: "",
            profileAvatarUrl = data?.avatarUrl ?: "",
            profileAvatarAlt = data?.avatarAlt ?: "Avatar",
            profileSubmitUrl = data?.submitUrl ?: shellRenderer.url("/auth/components/profile-update"),
        )

    private fun profileLabels(
        base: SettingsTabContent,
        data: ProfilePage?,
        i18n: io.github.rygel.outerstellar.i18n.I18nService,
    ): SettingsTabContent =
        base.copy(
            profileUsernameLabel = data?.usernameLabel ?: i18n.translate("web.profile.username"),
            profileUsernamePlaceholder =
                data?.usernamePlaceholder ?: i18n.translate("web.profile.username.placeholder"),
            profileEmailLabel = data?.emailLabel ?: i18n.translate("web.profile.email"),
            profileEmailPlaceholder = data?.emailPlaceholder ?: i18n.translate("web.profile.email.placeholder"),
            profileAvatarLabel = data?.avatarLabel ?: i18n.translate("web.profile.avatar"),
            profileAvatarPlaceholder = data?.avatarPlaceholder ?: i18n.translate("web.profile.avatar.placeholder"),
            profileSubmitLabel = data?.submitLabel ?: i18n.translate("web.profile.save"),
            profileGravatarHint = data?.gravatarHint ?: i18n.translate("web.profile.gravatar.hint"),
        )

    private fun profileNotifications(
        base: SettingsTabContent,
        data: ProfilePage?,
        i18n: io.github.rygel.outerstellar.i18n.I18nService,
    ): SettingsTabContent =
        base.copy(
            profileNotifPrefsUrl = data?.notificationPrefsUrl ?: "",
            profileNotifPrefsLabel = data?.notificationPrefsLabel ?: i18n.translate("web.profile.notifications"),
            profileEmailNotifLabel = data?.emailNotifLabel ?: i18n.translate("web.profile.emailNotifications"),
            profilePushNotifLabel = data?.pushNotifLabel ?: i18n.translate("web.profile.pushNotifications"),
            profileSavePrefsLabel = data?.savePrefsLabel ?: i18n.translate("web.profile.savePreferences"),
            profileEmailNotificationsEnabled = data?.emailNotificationsEnabled ?: false,
            profilePushNotificationsEnabled = data?.pushNotificationsEnabled ?: false,
        )

    private fun profileDangerZone(
        base: SettingsTabContent,
        data: ProfilePage?,
        i18n: io.github.rygel.outerstellar.i18n.I18nService,
    ): SettingsTabContent =
        base.copy(
            profileDangerZoneLabel = data?.dangerZoneLabel ?: i18n.translate("web.profile.dangerZone"),
            profileDeleteAccountUrl = data?.deleteAccountUrl ?: "",
            profileDeleteAccountLabel = data?.deleteAccountLabel ?: i18n.translate("web.profile.deleteAccount"),
            profileDeleteAccountDescription =
                data?.deleteAccountDescription ?: i18n.translate("web.profile.deleteAccount.description"),
            profileDeleteAccountConfirmLabel =
                data?.deleteAccountConfirmLabel ?: i18n.translate("web.profile.deleteAccount.confirm"),
            profileDeleteAccountCancelLabel =
                data?.deleteAccountCancelLabel ?: i18n.translate("web.profile.deleteAccount.cancel"),
        )

    private fun buildPasswordTab(shellRenderer: ShellRenderer, csrfToken: String): SettingsTabContent {
        val form = authPageFactory?.buildChangePasswordForm(shellRenderer)
        return SettingsTabContent(
            activeTab = "password",
            csrfToken = csrfToken,
            passwordTitle = form?.title ?: shellRenderer.i18n.translate("web.password.title"),
            passwordCurrentPasswordLabel =
                form?.currentPasswordLabel ?: shellRenderer.i18n.translate("web.password.current"),
            passwordNewPasswordLabel = form?.newPasswordLabel ?: shellRenderer.i18n.translate("web.password.new"),
            passwordConfirmPasswordLabel =
                form?.confirmPasswordLabel ?: shellRenderer.i18n.translate("web.password.confirm"),
            passwordSubmitLabel = form?.submitLabel ?: shellRenderer.i18n.translate("web.password.submit"),
            passwordSubmitUrl = form?.submitUrl ?: shellRenderer.url("/auth/components/change-password"),
            passwordCurrentPasswordPlaceholder = form?.currentPasswordPlaceholder ?: "",
            passwordNewPasswordPlaceholder = form?.newPasswordPlaceholder ?: "",
            passwordConfirmPasswordPlaceholder = form?.confirmPasswordPlaceholder ?: "",
        )
    }

    private fun buildApiKeysTab(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        csrfToken: String,
    ): SettingsTabContent {
        val data = ctx.user?.let { adminPageFactory?.buildApiKeysPage(ctx, shellRenderer)?.data }
        return SettingsTabContent(
            activeTab = "api-keys",
            csrfToken = csrfToken,
            apiKeysCreateLabel = data?.createLabel ?: shellRenderer.i18n.translate("web.apiKeys.create"),
            apiKeysKeyNameLabel = data?.keyNameLabel ?: shellRenderer.i18n.translate("web.apiKeys.keyName"),
            apiKeysKeyNamePlaceholder =
                data?.keyNamePlaceholder ?: shellRenderer.i18n.translate("web.apiKeys.keyName.placeholder"),
            apiKeysYourKeysHeading = data?.yourKeysHeading ?: shellRenderer.i18n.translate("web.apiKeys.yourKeys"),
            apiKeysEmptyLabel = data?.emptyLabel ?: shellRenderer.i18n.translate("web.apiKeys.empty"),
            apiKeysHeaderPrefix = data?.headerPrefix ?: shellRenderer.i18n.translate("web.apiKeys.header.prefix"),
            apiKeysHeaderName = data?.headerName ?: shellRenderer.i18n.translate("web.apiKeys.header.name"),
            apiKeysHeaderCreated = data?.headerCreated ?: shellRenderer.i18n.translate("web.apiKeys.header.created"),
            apiKeysHeaderLastUsed = data?.headerLastUsed ?: shellRenderer.i18n.translate("web.apiKeys.header.lastUsed"),
            apiKeysHeaderActions = data?.headerActions ?: shellRenderer.i18n.translate("web.apiKeys.header.actions"),
            apiKeysNeverLabel = data?.neverLabel ?: shellRenderer.i18n.translate("web.apiKeys.never"),
            apiKeysDeleteConfirm = data?.deleteConfirm ?: shellRenderer.i18n.translate("web.apiKeys.delete.confirm"),
            apiKeysDeleteLabel = data?.deleteLabel ?: shellRenderer.i18n.translate("web.apiKeys.delete"),
            apiKeysCreateUrl = data?.createUrl ?: shellRenderer.url("/auth/api-keys/create"),
            apiKeys = data?.keys ?: emptyList(),
            apiKeysNewKey = data?.newKey,
            apiKeysNewKeyName = data?.newKeyName,
        )
    }

    private fun buildNotificationsTab(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        csrfToken: String,
    ): SettingsTabContent {
        val data = ctx.user?.let { adminPageFactory?.buildNotificationsPage(ctx, shellRenderer)?.data }
        return SettingsTabContent(
            activeTab = "notifications",
            csrfToken = csrfToken,
            notifications = data?.notifications ?: emptyList(),
            notifUnreadCount = data?.unreadCount ?: 0,
            notifMarkAllReadUrl = data?.markAllReadUrl ?: shellRenderer.url("/notifications/read-all"),
            notifMarkAllReadLabel =
                data?.markAllReadLabel ?: shellRenderer.i18n.translate("web.notifications.markAllRead"),
            notifMarkReadLabel = data?.markReadLabel ?: shellRenderer.i18n.translate("web.notifications.markRead"),
            notifReadLabel = data?.readLabel ?: shellRenderer.i18n.translate("web.notifications.read"),
            notifNewLabel = data?.newLabel ?: shellRenderer.i18n.translate("web.notifications.new"),
            notifEmptyLabel = data?.emptyLabel ?: shellRenderer.i18n.translate("web.notifications.empty"),
        )
    }

    private fun buildAppearanceTab(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        csrfToken: String,
    ): SettingsTabContent =
        SettingsTabContent(
            activeTab = "appearance",
            csrfToken = csrfToken,
            themeSelector = sidebarFactory?.buildThemeSelector(ctx, shellRenderer),
            languageSelector = sidebarFactory?.buildLanguageSelector(ctx, shellRenderer),
            layoutSelector = sidebarFactory?.buildLayoutSelector(ctx, shellRenderer),
        )

    private fun normalizeTab(tab: String): String =
        if (tab in listOf("profile", "password", "security", "api-keys", "notifications", "appearance")) tab
        else "profile"
}
