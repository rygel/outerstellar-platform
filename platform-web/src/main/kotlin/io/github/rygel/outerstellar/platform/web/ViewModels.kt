package io.github.rygel.outerstellar.platform.web

import org.http4k.template.ViewModel

data class HomeFeature(val label: String, val value: String)

data class Page<T : ViewModel>(val shell: ShellView, val data: T) : ViewModel {
    override fun template(): String = data.template()
}

data class HomePage(
    val eyebrow: String,
    val intro: String,
    val features: List<HomeFeature>,
    val composerTitle: String,
    val composerIntro: String,
    val authorPlaceholder: String,
    val contentPlaceholder: String,
    val submitLabel: String,
    val submitUrl: String,
    val messageList: MessageListViewModel,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/HomePage"
}

data class ContactTrashItemViewModel(
    val syncId: String,
    val name: String,
    val emails: List<String>,
    val phones: List<String>,
    val company: String,
    val department: String,
    val restoreUrl: String,
)

data class ContactTrashListViewModel(
    val contacts: List<ContactTrashItemViewModel>,
    val emptyMessage: String,
    val refreshUrl: String,
    val title: String = "Deleted Contacts",
    val restoreTitle: String = "Restore contact",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/components/ContactTrashList"
}

data class TrashPage(
    val title: String,
    val description: String,
    val messageList: MessageListViewModel,
    val contactList: ContactTrashListViewModel? = null,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/TrashPage"
}

data class PaginationViewModel(
    val currentPage: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val previousUrl: String?,
    val nextUrl: String?,
    val pages: List<PageNumberViewModel>,
    val pageLabel: String = "Page",
)

data class PageNumberViewModel(val number: Int, val url: String, val isActive: Boolean)

data class AuthModeTab(val key: String, val label: String, val url: String)

data class AuthViewModel(
    val heading: String,
    val intro: String,
    val helperText: String,
    val tabs: List<AuthModeTab>,
    val defaultFormUrl: String,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/AuthPage"
}

data class AuthFormFragment(
    val mode: String,
    val title: String,
    val description: String,
    val submitUrl: String,
    val submitLabel: String,
    val language: String,
    val theme: String,
    val layout: String,
    val nameLabel: String,
    val emailLabel: String,
    val passwordLabel: String,
    val confirmPasswordLabel: String,
    val rememberLabel: String,
    val emailPlaceholder: String,
    val passwordPlaceholder: String,
    val confirmPasswordPlaceholder: String,
    val namePlaceholder: String,
    val includeNameField: Boolean,
    val includeConfirmPasswordField: Boolean,
    val includeRememberField: Boolean,
    val oauthSeparator: String = "or continue with",
    val signInWithApple: String = "Sign in with Apple",
    val showAppleOAuth: Boolean = false,
) : ViewModel

data class AuthResultFragment(val title: String, val message: String, val toneClass: String) : ViewModel

data class TotpChallengeForm(
    val partialToken: String,
    val title: String = "Two-Factor Authentication",
    val description: String = "Enter the 6-digit code from your authenticator app.",
    val codeLabel: String = "Authentication Code",
    val verifyLabel: String = "Verify",
    val backLinkLabel: String = "Back to login",
    val error: String? = null,
) : ViewModel

data class TotpSetupFragment(
    val totpEnabled: Boolean = false,
    val totpQrDataUri: String? = null,
    val totpSecret: String? = null,
    val totpBackupCodes: List<String>? = null,
    val totpRemainingBackupCodes: Int = 0,
    val enabledLabel: String = "Two-factor authentication is enabled.",
    val passwordLabel: String = "Enter your password to disable",
    val disableLabel: String = "Disable Two-Factor Auth",
    val backupCodesLabel: String = "Backup Codes",
    val backupCodesHint: String = "Save these codes in a safe place. Each code can be used once.",
    val backupCodesRemainingLabel: String = "%s backup codes remaining",
    val copyLabel: String = "Copy Codes",
    val downloadLabel: String = "Download Codes",
    val disabledLabel: String = "Scan the QR code below with your authenticator app, then enter the 6-digit code.",
    val manualKeyLabel: String = "Or enter this key manually:",
    val codeLabel: String = "Authentication Code",
    val setupLabel: String = "Enable Two-Factor Auth",
    val verifyLabel: String = "Verify and Enable",
) : ViewModel

data class ErrorPage(
    val statusCode: Int,
    val heading: String,
    val message: String,
    val primaryActionLabel: String,
    val primaryActionUrl: String,
    val secondaryActionLabel: String,
    val secondaryActionUrl: String,
    val helpButtonLabel: String,
    val helpUrl: String,
    val errorLabel: String = "Error",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/ErrorPage"
}

data class ErrorHelpFragment(val title: String, val items: List<String>) : ViewModel

data class FooterStatusFragment(val text: String) : ViewModel

data class VoteFragmentViewModel(
    val score: io.github.rygel.outerstellar.platform.model.VoteScore,
    val messageSyncId: String,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/components/VoteFragment"
}

data class PollFragmentViewModel(
    val results: io.github.rygel.outerstellar.platform.model.PollWithResults,
    val syncId: String,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/components/PollCard"
}

data class DevDashboardPage(
    val metrics: String,
    val cacheStats: Map<String, Any>,
    val outboxStats: OutboxStatsViewModel,
    val telemetryStatus: String,
    val badge: String = "System Diagnostics",
    val heading: String = "Developer Dashboard",
    val description: String = "Real-time metrics and system state for local development.",
    val outboxLabel: String = "Transactional Outbox",
    val pendingLabel: String = "Pending",
    val processedLabel: String = "Processed",
    val failedLabel: String = "Failed",
    val cacheLabel: String = "Cache Statistics",
    val protocolLabel: String = "Custom Protocol Tester",
    val telemetryLabel: String = "OpenTelemetry",
    val metricsLabel: String = "Application Metrics",
    val triggerSyncLabel: String = "Trigger Sync",
    val protocolDescription: String = "",
    val searchHtmxLabel: String = "Search \"HTMX\"",
    val searchDraculaLabel: String = "Search \"Dracula\"",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/DevDashboard"
}

data class PluginAdminDashboardPage(val cards: List<AdminSummaryCard>, val diagnostics: HostedAppDiagnostics? = null) :
    ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/PluginAdminDashboard"
}

data class OutboxStatsViewModel(val pending: Int, val processed: Int, val failed: Int)

data class ModalViewModel(
    val id: String,
    val title: String,
    val message: String,
    val confirmLabel: String,
    val cancelLabel: String,
    val actionUrl: String,
    val targetId: String,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/components/Modal"
}

data class MessageEditFormFragment(
    val syncId: String,
    val author: String,
    val content: String,
    val submitUrl: String,
    val titleLabel: String = "Edit message",
    val authorLabel: String = "Author",
    val contentLabel: String = "Content",
    val saveLabel: String = "Save",
    val cancelLabel: String = "Cancel",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/components/MessageEdit"
}

data class ConflictResolveViewModel(
    val syncId: String,
    val myAuthor: String,
    val myContent: String,
    val serverAuthor: String,
    val serverContent: String,
    val resolveUrl: String,
    val modalTitle: String = "Sync Conflict Detected",
    val myVersionLabel: String = "My Version (Local)",
    val serverVersionLabel: String = "Server Version",
    val keepMineLabel: String = "Keep My Version",
    val acceptServerLabel: String = "Accept Server Version",
    val decideLaterLabel: String = "Decide Later",
    val description: String =
        "The server has a newer or different version of this message. Please choose which version to keep.",
    val authorLabel: String = "Author",
) : ViewModel

data class ChangePasswordPage(val form: ChangePasswordForm) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/ChangePasswordPage"
}

data class ChangePasswordForm(
    val title: String,
    val currentPasswordLabel: String,
    val newPasswordLabel: String,
    val confirmPasswordLabel: String,
    val submitLabel: String,
    val submitUrl: String,
    val currentPasswordPlaceholder: String,
    val newPasswordPlaceholder: String,
    val confirmPasswordPlaceholder: String,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/ChangePasswordForm"
}

data class ResetPasswordPage(
    val token: String,
    val newPasswordLabel: String,
    val confirmPasswordLabel: String,
    val submitLabel: String,
    val submitUrl: String,
    val newPasswordPlaceholder: String = "At least 8 characters",
    val confirmPasswordPlaceholder: String = "Repeat your new password",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/ResetPasswordPage"
}

data class UserAdminRow(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val enabled: Boolean,
    val toggleEnabledUrl: String,
    val toggleRoleUrl: String,
    val isSelf: Boolean,
    val isLocked: Boolean = false,
    val unlockUrl: String = "",
    val failedLoginAttempts: Int = 0,
)

data class UserAdminPage(
    val title: String,
    val description: String,
    val users: List<UserAdminRow>,
    val currentPage: Int = 1,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val previousUrl: String = "",
    val nextUrl: String = "",
    val headerUsername: String = "Username",
    val headerEmail: String = "Email",
    val headerRole: String = "Role",
    val headerEnabled: String = "Enabled",
    val headerActions: String = "Actions",
    val actionDisable: String = "Disable",
    val actionEnable: String = "Enable",
    val actionDemote: String = "Demote",
    val actionPromote: String = "Promote",
    val selfLabel: String = "you",
    val actionUnlock: String = "Unlock",
    val previousLabel: String = "Previous",
    val nextLabel: String = "Next",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/UserAdminPage"
}

data class AuditLogPage(
    val title: String,
    val entries: List<AuditEntryViewModel>,
    val currentPage: Int = 1,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val previousUrl: String = "",
    val nextUrl: String = "",
    val headerWhen: String = "When",
    val headerActor: String = "Actor",
    val headerAction: String = "Action",
    val headerTarget: String = "Target",
    val headerDetail: String = "Detail",
    val previousLabel: String = "Previous",
    val nextLabel: String = "Next",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/AuditLogPage"
}

data class AuditEntryViewModel(
    val actorUsername: String,
    val targetUsername: String,
    val action: String,
    val detail: String,
    val timestamp: String,
)

data class ProfilePage(
    val title: String,
    val username: String,
    val email: String,
    val role: String,
    val avatarUrl: String,
    val submitUrl: String,
    val usernameLabel: String,
    val usernamePlaceholder: String,
    val emailLabel: String,
    val emailPlaceholder: String,
    val avatarLabel: String,
    val avatarPlaceholder: String,
    val submitLabel: String,
    // Notification preferences
    val emailNotificationsEnabled: Boolean,
    val pushNotificationsEnabled: Boolean,
    val notificationPrefsUrl: String,
    val notificationPrefsLabel: String,
    val emailNotifLabel: String,
    val pushNotifLabel: String,
    val savePrefsLabel: String,
    // Danger zone
    val deleteAccountUrl: String,
    val dangerZoneLabel: String,
    val deleteAccountLabel: String,
    val deleteAccountDescription: String,
    val deleteAccountConfirmLabel: String,
    val deleteAccountCancelLabel: String,
    val avatarAlt: String = "Avatar",
    val gravatarHint: String = "Leave blank to use Gravatar.",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/ProfilePage"
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

data class ApiKeysPage(
    val title: String,
    val keys: List<io.github.rygel.outerstellar.platform.model.ApiKeySummary>,
    val createUrl: String,
    val newKey: String? = null,
    val newKeyName: String? = null,
    val description: String = "Manage your API keys for programmatic access.",
    val newKeyBanner: String = "Key created. Copy it now - it won't be shown again:",
    val createLabel: String = "Create API Key",
    val keyNameLabel: String = "Key Name",
    val keyNamePlaceholder: String = "My integration",
    val yourKeysHeading: String = "Your API Keys",
    val emptyLabel: String = "No API keys yet.",
    val headerPrefix: String = "Prefix",
    val headerName: String = "Name",
    val headerCreated: String = "Created",
    val headerLastUsed: String = "Last Used",
    val headerActions: String = "Actions",
    val neverLabel: String = "Never",
    val deleteConfirm: String = "Delete this key?",
    val deleteLabel: String = "Delete",
    val nameLabel: String = "Name:",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/ApiKeysPage"
}

data class NotificationViewModel(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val read: Boolean,
    val timeAgo: String,
    val markReadUrl: String,
)

data class NotificationsPage(
    val title: String,
    val description: String,
    val notifications: List<NotificationViewModel>,
    val unreadCount: Int,
    val markAllReadUrl: String,
    val emptyLabel: String = "No notifications yet.",
    val markAllReadLabel: String = "Mark all as read",
    val markReadLabel: String = "Mark as read",
    val readLabel: String = "Read",
    val newLabel: String = "New",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/NotificationsPage"
}

data class NotificationBellFragment(
    val unreadCount: Int,
    val notificationsUrl: String,
    val title: String = "Notifications",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/components/NotificationBell"
}

data class ContactFormFragment(
    val syncId: String = "",
    val name: String = "",
    val emails: String = "",
    val phones: String = "",
    val socialMedia: String = "",
    val company: String = "",
    val companyAddress: String = "",
    val department: String = "",
    val submitUrl: String = "/contacts",
    val isEdit: Boolean = false,
    val titleLabel: String = "Create Contact",
    val nameLabel: String = "Name",
    val emailsLabel: String = "Emails (comma-separated)",
    val phonesLabel: String = "Phones (comma-separated)",
    val socialLabel: String = "Social media (comma-separated)",
    val companyLabel: String = "Company",
    val addressLabel: String = "Address",
    val departmentLabel: String = "Department",
    val saveLabel: String = "Save",
    val cancelLabel: String = "Cancel",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/components/ContactForm"
}

data class ContactViewModel(
    val syncId: String,
    val name: String,
    val emails: List<String>,
    val phones: List<String>,
    val socialMedia: List<String>,
    val company: String,
    val companyAddress: String,
    val department: String,
    val deleteUrl: String = "",
    val editUrl: String = "",
)

data class SearchResultViewModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val type: String,
)

data class SearchTypeFilter(val key: String, val label: String, val url: String, val active: Boolean)

data class SearchPage(
    val title: String,
    val query: String,
    val results: List<SearchResultViewModel>,
    val emptyLabel: String,
    val searchPlaceholder: String,
    val searchLabel: String,
    val typeFilter: String = "",
    val typeFilters: List<SearchTypeFilter> = emptyList(),
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/SearchPage"
}

data class SettingsTab(val key: String, val label: String, val url: String, val active: Boolean)

data class SettingsPage(val title: String, val tabs: List<SettingsTab>, val activeTab: String) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/SettingsPage"
}

data class SettingsTabContent(
    val activeTab: String,
    val csrfToken: String = "",
    // Profile tab
    val profileUsername: String = "",
    val profileEmail: String = "",
    val profileAvatarUrl: String = "",
    val profileAvatarAlt: String = "",
    val profileSubmitUrl: String = "",
    val profileUsernameLabel: String = "",
    val profileUsernamePlaceholder: String = "",
    val profileEmailLabel: String = "",
    val profileEmailPlaceholder: String = "",
    val profileAvatarLabel: String = "",
    val profileAvatarPlaceholder: String = "",
    val profileSubmitLabel: String = "",
    val profileGravatarHint: String = "",
    val profileNotifPrefsUrl: String = "",
    val profileNotifPrefsLabel: String = "",
    val profileEmailNotifLabel: String = "",
    val profilePushNotifLabel: String = "",
    val profileSavePrefsLabel: String = "",
    val profileEmailNotificationsEnabled: Boolean = false,
    val profilePushNotificationsEnabled: Boolean = false,
    val profileDangerZoneLabel: String = "",
    val profileDeleteAccountUrl: String = "",
    val profileDeleteAccountLabel: String = "",
    val profileDeleteAccountDescription: String = "",
    val profileDeleteAccountConfirmLabel: String = "",
    val profileDeleteAccountCancelLabel: String = "",
    // Password tab
    val passwordTitle: String = "",
    val passwordCurrentPasswordLabel: String = "",
    val passwordNewPasswordLabel: String = "",
    val passwordConfirmPasswordLabel: String = "",
    val passwordSubmitLabel: String = "",
    val passwordSubmitUrl: String = "",
    val passwordCurrentPasswordPlaceholder: String = "",
    val passwordNewPasswordPlaceholder: String = "",
    val passwordConfirmPasswordPlaceholder: String = "",
    // API Keys tab
    val apiKeys: List<io.github.rygel.outerstellar.platform.model.ApiKeySummary> = emptyList(),
    val apiKeysCreateUrl: String = "",
    val apiKeysNewKey: String? = null,
    val apiKeysNewKeyName: String? = null,
    val apiKeysCreateLabel: String = "",
    val apiKeysKeyNameLabel: String = "",
    val apiKeysKeyNamePlaceholder: String = "",
    val apiKeysYourKeysHeading: String = "",
    val apiKeysEmptyLabel: String = "",
    val apiKeysHeaderPrefix: String = "",
    val apiKeysHeaderName: String = "",
    val apiKeysHeaderCreated: String = "",
    val apiKeysHeaderLastUsed: String = "",
    val apiKeysHeaderActions: String = "",
    val apiKeysNeverLabel: String = "",
    val apiKeysDeleteConfirm: String = "",
    val apiKeysDeleteLabel: String = "",
    // Notifications tab
    val notifications: List<NotificationViewModel> = emptyList(),
    val notifUnreadCount: Int = 0,
    val notifMarkAllReadUrl: String = "",
    val notifMarkAllReadLabel: String = "",
    val notifMarkReadLabel: String = "",
    val notifReadLabel: String = "",
    val notifNewLabel: String = "",
    val notifEmptyLabel: String = "",
    // Appearance tab
    val themeSelector: SidebarSelector? = null,
    val languageSelector: SidebarSelector? = null,
    val layoutSelector: SidebarSelector? = null,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/SettingsTabContent"
}

data class ContactsPage(
    val title: String,
    val description: String,
    val contacts: List<ContactViewModel>,
    val currentPage: Int = 1,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val previousUrl: String = "",
    val nextUrl: String = "",
    val totalCount: Long = 0,
    val currentQuery: String = "",
    val currentOffset: Int = 0,
    val currentLimit: Int = 12,
    val createLabel: String = "Create Contact",
    val editTitle: String = "Edit contact",
    val deleteTitle: String = "Delete contact",
    val deleteConfirmLabel: String = "Are you sure you want to delete this contact?",
    val contactsTotalLabel: String = "contacts total",
    val previousPageTitle: String = "Previous page",
    val nextPageTitle: String = "Next page",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/ContactsPage"
}
