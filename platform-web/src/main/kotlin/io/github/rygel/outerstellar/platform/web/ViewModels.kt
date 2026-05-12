package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.TextResolver
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
    val pageDescription: String = "",
    val canonicalUrl: String = "",
    val noIndex: Boolean = false,
    val supportedLocales: List<String> = listOf("en"),
    val appBaseUrl: String = "",
    val ogImage: String = "",
) {
    fun text(key: String, vararg args: Any?): String = textResolver?.resolve(key, *args) ?: key
}

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

data class TrashPage(val title: String, val description: String, val messageList: MessageListViewModel) : ViewModel {
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
) : ViewModel

data class AuthResultFragment(val title: String, val message: String, val toneClass: String) : ViewModel

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

data class PluginAdminDashboardPage(val cards: List<AdminSummaryCard>) : ViewModel {
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

data class SearchPage(
    val title: String,
    val query: String,
    val results: List<SearchResultViewModel>,
    val emptyLabel: String,
    val searchPlaceholder: String,
    val searchLabel: String,
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/SearchPage"
}

data class SettingsTab(val key: String, val label: String, val url: String, val active: Boolean)

data class SettingsPage(
    val title: String,
    val tabs: List<SettingsTab>,
    val activeTab: String,
    val profileDescription: String = "",
    val passwordDescription: String = "",
    val apiKeysDescription: String = "",
    val notificationsDescription: String = "",
    val appearanceDescription: String = "",
) : ViewModel {
    override fun template(): String = "io/github/rygel/outerstellar/platform/web/SettingsPage"
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
