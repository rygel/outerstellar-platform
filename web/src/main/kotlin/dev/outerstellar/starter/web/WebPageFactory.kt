package dev.outerstellar.starter.web

import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncMessage
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
    val themeId: String,
    val themeCss: String,
    val layoutClass: String,
    val navLinks: List<ShellLink>,
    val themeSelectorUrl: String,
    val languageSelectorUrl: String,
    val layoutSelectorUrl: String,
    val footerCopy: String,
    val footerStatusUrl: String,
    val version: String,
    val userName: String? = null,
    val isLoggedIn: Boolean = false,
    val logoutUrl: String? = null,
    val changePasswordUrl: String? = null,
    val profileUrl: String? = null,
    val isDarkMode: Boolean = true,
    val darkModeToggleUrl: String = "?theme=default",
    val toastErrorLabel: String = "Error",
    val toastSuccessLabel: String = "Success",
    val changePasswordLabel: String = "Change password",
    val signOutLabel: String = "Sign out",
    val csrfToken: String = "",
    val notificationsUrl: String? = null,
    val unreadNotificationCount: Int = 0,
)

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
    override fun template(): String = "dev/outerstellar/starter/web/HomePage"
}

data class TrashPage(
    val title: String,
    val description: String,
    val messageList: MessageListViewModel,
) : ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/TrashPage"
}

data class PaginationViewModel(
    val currentPage: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val previousUrl: String?,
    val nextUrl: String?,
    val pages: List<PageNumberViewModel>,
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
    override fun template(): String = "dev/outerstellar/starter/web/AuthPage"
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

data class AuthResultFragment(val title: String, val message: String, val toneClass: String) :
    ViewModel

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
) : ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/ErrorPage"
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
) : ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/DevDashboard"
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
    override fun template(): String = "dev/outerstellar/starter/web/components/Modal"
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
    override fun template(): String = "dev/outerstellar/starter/web/ChangePasswordPage"
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
    override fun template(): String = "dev/outerstellar/starter/web/ChangePasswordForm"
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
    override fun template(): String = "dev/outerstellar/starter/web/ResetPasswordPage"
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
    val previousLabel: String = "Previous",
    val nextLabel: String = "Next",
) : ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/UserAdminPage"
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
    override fun template(): String = "dev/outerstellar/starter/web/AuditLogPage"
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
) : ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/ProfilePage"
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
    override fun template(): String = "dev/outerstellar/starter/web/components/SidebarSelector"
}

data class ApiKeysPage(
    val title: String,
    val keys: List<dev.outerstellar.starter.model.ApiKeySummary>,
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
) : ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/ApiKeysPage"
}

private const val MIN_PASSWORD_LENGTH = 8

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
) : ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/NotificationsPage"
}

data class NotificationBellFragment(val unreadCount: Int, val notificationsUrl: String) :
    ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/components/NotificationBell"
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
    override fun template(): String = "dev/outerstellar/starter/web/components/ContactForm"
}

private const val DEFAULT_LIMIT = 10
private const val HTTP_STATUS_NOT_FOUND = 404
private const val HTTP_STATUS_SERVER_ERROR = 500

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
    override fun template(): String = "dev/outerstellar/starter/web/ContactsPage"
}

@Suppress("TooManyFunctions")
class WebPageFactory(
    private val repository: MessageRepository,
    private val messageService: MessageService,
    private val contactService: dev.outerstellar.starter.service.ContactService? = null,
    private val securityService: dev.outerstellar.starter.security.SecurityService? = null,
    private val auditRepository: dev.outerstellar.starter.security.AuditRepository? = null,
    private val notificationService: dev.outerstellar.starter.service.NotificationService? = null,
) {
    private val messageListComponent = MessageListComponent(messageService)

    fun buildContactsPage(
        ctx: WebContext,
        query: String? = null,
        limit: Int = 12,
        offset: Int = 0,
    ): Page<ContactsPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.contacts"), "/contacts")

        val dbContacts = contactService?.listContacts(query, limit, offset) ?: emptyList()
        val totalCount = contactService?.countContacts(query) ?: 0L
        val currentPage = (offset / limit) + 1
        val hasPrevious = offset > 0
        val hasNext = offset + limit < totalCount
        val previousUrl = ctx.url("/contacts?limit=$limit&offset=${maxOf(0, offset - limit)}")
        val nextUrl = ctx.url("/contacts?limit=$limit&offset=${offset + limit}")

        return Page(
            shell = shell,
            data =
                ContactsPage(
                    title = "Contacts Directory",
                    description = "A list of all your contacts.",
                    contacts =
                        dbContacts.map {
                            ContactViewModel(
                                syncId = it.syncId,
                                name = it.name,
                                emails = it.emails,
                                phones = it.phones,
                                socialMedia = it.socialMedia,
                                company = it.company,
                                companyAddress = it.companyAddress,
                                department = it.department,
                                deleteUrl = ctx.url("/contacts/${it.syncId}/delete"),
                                editUrl = ctx.url("/contacts/${it.syncId}/edit"),
                            )
                        },
                    currentPage = currentPage,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    previousUrl = previousUrl,
                    nextUrl = nextUrl,
                    totalCount = totalCount,
                    createLabel = i18n.translate("web.contacts.create"),
                    editTitle = i18n.translate("web.contacts.edit"),
                    deleteTitle = i18n.translate("web.contacts.delete"),
                    deleteConfirmLabel = i18n.translate("web.contacts.delete.confirm"),
                    contactsTotalLabel = i18n.translate("web.contacts.total"),
                    previousPageTitle = i18n.translate("web.contacts.previous.page"),
                    nextPageTitle = i18n.translate("web.contacts.next.page"),
                ),
        )
    }

    fun buildHomePage(
        ctx: WebContext,
        query: String? = null,
        limit: Int = DEFAULT_LIMIT,
        offset: Int = 0,
        year: Int? = null,
    ): Page<HomePage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.home"), "/")
        val messageList = messageListComponent.build(ctx, query, limit, offset, year)

        return Page(
            shell = shell,
            data =
                HomePage(
                    eyebrow = i18n.translate("web.home.eyebrow"),
                    intro = i18n.translate("web.home.intro"),
                    features =
                        listOf(
                            HomeFeature(
                                i18n.translate("web.feature.http.label"),
                                i18n.translate("web.feature.http.value"),
                            ),
                            HomeFeature(
                                i18n.translate("web.feature.db.label"),
                                i18n.translate("web.feature.db.value"),
                            ),
                            HomeFeature(
                                i18n.translate("web.feature.sync.label"),
                                i18n.translate("web.feature.sync.value"),
                            ),
                            HomeFeature(
                                i18n.translate("web.feature.desktop.label"),
                                i18n.translate("web.feature.desktop.value"),
                            ),
                        ),
                    composerTitle = i18n.translate("web.home.composer.title"),
                    composerIntro = i18n.translate("web.home.composer.intro"),
                    authorPlaceholder = i18n.translate("web.home.composer.author"),
                    contentPlaceholder = i18n.translate("web.home.composer.content"),
                    submitLabel = i18n.translate("web.home.composer.submit"),
                    submitUrl = ctx.url("/messages"),
                    messageList = messageList,
                ),
        )
    }

    fun buildAuthPage(ctx: WebContext): Page<AuthViewModel> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.auth"), "/auth")
        val returnTo = ctx.request.query("returnTo") ?: "/"

        val formsUrl = "/auth/components/forms"
        return Page(
            shell = shell,
            data =
                AuthViewModel(
                    heading = i18n.translate("web.auth.heading"),
                    intro = i18n.translate("web.auth.intro"),
                    helperText = i18n.translate("web.auth.helper"),
                    tabs =
                        listOf(
                            AuthModeTab(
                                "sign-in",
                                i18n.translate("web.auth.signin"),
                                ctx.url("$formsUrl/sign-in?returnTo=$returnTo"),
                            ),
                            AuthModeTab(
                                "register",
                                i18n.translate("web.auth.register"),
                                ctx.url("$formsUrl/register?returnTo=$returnTo"),
                            ),
                            AuthModeTab(
                                "recover",
                                i18n.translate("web.auth.recover"),
                                ctx.url("$formsUrl/recover?returnTo=$returnTo"),
                            ),
                        ),
                    defaultFormUrl = ctx.url("$formsUrl/sign-in?returnTo=$returnTo"),
                ),
        )
    }

    fun buildAuthForm(ctx: WebContext, mode: String): AuthFormFragment {
        val i18n = ctx.i18n
        val normalizedMode = if (mode == "register" || mode == "recover") mode else "sign-in"
        val returnTo = ctx.request.query("returnTo") ?: "/"

        return AuthFormFragment(
            mode = normalizedMode,
            title = i18n.translate("web.auth.$normalizedMode.title"),
            description = i18n.translate("web.auth.$normalizedMode.description"),
            submitUrl = ctx.url("/auth/components/result?returnTo=$returnTo"),
            submitLabel = i18n.translate("web.auth.$normalizedMode.submit"),
            language = ctx.lang,
            theme = ctx.theme,
            layout = ctx.layout,
            nameLabel = i18n.translate("web.auth.field.name"),
            emailLabel = i18n.translate("web.auth.field.email"),
            passwordLabel = i18n.translate("web.auth.field.password"),
            confirmPasswordLabel = i18n.translate("web.auth.field.confirm"),
            rememberLabel = i18n.translate("web.auth.field.remember"),
            emailPlaceholder = i18n.translate("web.auth.placeholder.email"),
            passwordPlaceholder = i18n.translate("web.auth.placeholder.password"),
            confirmPasswordPlaceholder = i18n.translate("web.auth.placeholder.confirm"),
            namePlaceholder = i18n.translate("web.auth.placeholder.name"),
            includeNameField = normalizedMode == "register",
            includeConfirmPasswordField = normalizedMode == "register",
            includeRememberField = normalizedMode == "sign-in",
            oauthSeparator = i18n.translate("web.auth.oauth.separator"),
            signInWithApple = i18n.translate("web.auth.signin.apple"),
        )
    }

    fun buildAuthResult(ctx: WebContext, formValues: Map<String, String?>): AuthResultFragment {
        val i18n = ctx.i18n
        val mode = formValues["mode"] ?: "sign-in"
        val email = formValues["email"].orEmpty()
        val password = formValues["password"].orEmpty()
        val confirmPassword = formValues["confirmPassword"].orEmpty()
        val errors = mutableListOf<String>()

        if (email.isBlank()) errors += i18n.translate("web.auth.error.email")
        if (mode != "recover" && password.length < MIN_PASSWORD_LENGTH) {
            errors += i18n.translate("web.auth.error.password")
        }
        if (mode == "register" && confirmPassword != password) {
            errors += i18n.translate("web.auth.error.confirm")
        }

        return if (errors.isEmpty()) {
            AuthResultFragment(
                title = i18n.translate("web.auth.result.success.title"),
                message = i18n.translate("web.auth.result.success.body", email),
                toneClass = "panel-success",
            )
        } else {
            AuthResultFragment(
                title = i18n.translate("web.auth.result.error.title"),
                message = errors.joinToString(" "),
                toneClass = "panel-danger",
            )
        }
    }

    fun buildErrorPage(ctx: WebContext, kind: String): Page<ErrorPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.errors"), "/errors")
        val normalizedKind = if (kind == "server-error") "server-error" else "not-found"
        val statusCode =
            if (normalizedKind == "server-error") {
                HTTP_STATUS_SERVER_ERROR
            } else {
                HTTP_STATUS_NOT_FOUND
            }

        return Page(
            shell = shell,
            data =
                ErrorPage(
                    statusCode = statusCode,
                    heading = i18n.translate("web.error.$normalizedKind.title"),
                    message = i18n.translate("web.error.$normalizedKind.message"),
                    primaryActionLabel = i18n.translate("web.error.primary"),
                    primaryActionUrl = ctx.url("/"),
                    secondaryActionLabel = i18n.translate("web.error.secondary"),
                    secondaryActionUrl = ctx.url("/auth"),
                    helpButtonLabel = i18n.translate("web.error.help"),
                    helpUrl = ctx.url("/errors/components/help/$normalizedKind"),
                ),
        )
    }

    fun buildErrorHelp(ctx: WebContext, kind: String): ErrorHelpFragment {
        val i18n = ctx.i18n
        val normalizedKind = if (kind == "server-error") "server-error" else "not-found"

        return ErrorHelpFragment(
            title = i18n.translate("web.error.$normalizedKind.help.title"),
            items =
                listOf(
                    i18n.translate("web.error.$normalizedKind.help.item1"),
                    i18n.translate("web.error.$normalizedKind.help.item2"),
                    i18n.translate("web.error.$normalizedKind.help.item3"),
                ),
        )
    }

    @Suppress("LongParameterList")
    fun buildMessageList(
        ctx: WebContext,
        query: String? = null,
        limit: Int = DEFAULT_LIMIT,
        offset: Int = 0,
        year: Int? = null,
        isTrash: Boolean = false,
    ): MessageListViewModel {
        return messageListComponent.build(ctx, query, limit, offset, year, isTrash)
    }

    fun buildTrashPage(ctx: WebContext): Page<TrashPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.trash.title"), "/messages/trash")
        val messageList = buildMessageList(ctx, isTrash = true)

        return Page(
            shell = shell,
            data =
                TrashPage(
                    title = i18n.translate("web.trash.title"),
                    description = i18n.translate("web.trash.description"),
                    messageList = messageList,
                ),
        )
    }

    fun buildFooterStatus(ctx: WebContext): FooterStatusFragment {
        val i18n = ctx.i18n
        val msgCount = repository.listMessages().size
        val dirtyCount = repository.listDirtyMessages().size
        return FooterStatusFragment(
            text = i18n.translate("web.footer.status", msgCount, dirtyCount)
        )
    }

    fun buildDevDashboardPage(
        ctx: WebContext,
        metrics: String,
        cacheStats: Map<String, Any>,
        outboxStats: OutboxStatsViewModel,
        telemetryStatus: String,
    ): Page<DevDashboardPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.dev"), "/admin/dev")

        return Page(
            shell = shell,
            data =
                DevDashboardPage(
                    metrics = metrics,
                    cacheStats = cacheStats,
                    outboxStats = outboxStats,
                    telemetryStatus = telemetryStatus,
                    badge = i18n.translate("web.dev.badge"),
                    heading = i18n.translate("web.dev.heading"),
                    description = i18n.translate("web.dev.description"),
                    outboxLabel = i18n.translate("web.dev.outbox"),
                    pendingLabel = i18n.translate("web.dev.outbox.pending"),
                    processedLabel = i18n.translate("web.dev.outbox.processed"),
                    failedLabel = i18n.translate("web.dev.outbox.failed"),
                    cacheLabel = i18n.translate("web.dev.cache"),
                    protocolLabel = i18n.translate("web.dev.protocol"),
                    telemetryLabel = i18n.translate("web.dev.telemetry"),
                    metricsLabel = i18n.translate("web.dev.metrics"),
                    triggerSyncLabel = i18n.translate("web.dev.trigger.sync"),
                ),
        )
    }

    fun buildConflictResolveModal(ctx: WebContext, syncId: String): ConflictResolveViewModel {
        val message =
            repository.findBySyncId(syncId)
                ?: throw dev.outerstellar.starter.model.MessageNotFoundException(syncId)
        val serverVersion =
            org.http4k.format.Jackson.asA(message.syncConflict!!, SyncMessage::class)

        val i18n = ctx.i18n
        return ConflictResolveViewModel(
            syncId = syncId,
            myAuthor = message.author,
            myContent = message.content,
            serverAuthor = serverVersion.author,
            serverContent = serverVersion.content,
            resolveUrl = ctx.url("/messages/resolve/$syncId"),
            modalTitle = i18n.translate("web.conflict.title"),
            myVersionLabel = i18n.translate("web.conflict.my.version"),
            serverVersionLabel = i18n.translate("web.conflict.server.version"),
            keepMineLabel = i18n.translate("web.conflict.keep.mine"),
            acceptServerLabel = i18n.translate("web.conflict.accept.server"),
            decideLaterLabel = i18n.translate("web.conflict.decide.later"),
            description = i18n.translate("web.conflict.description"),
            authorLabel = i18n.translate("web.conflict.author"),
        )
    }

    fun buildThemeSelector(ctx: WebContext): SidebarSelector {
        val i18n = ctx.i18n
        val pagePath = ctx.request.query("pagePath").orEmpty()

        return SidebarSelector(
            heading = i18n.translate("web.sidebar.themes"),
            label = i18n.translate("web.sidebar.theme.label"),
            selectId = "theme-selector",
            selectName = "theme",
            options =
                ThemeCatalog.allThemes().map {
                    ShellOption(it.id, it.name, it.id, it.id == ctx.theme)
                },
            hiddenFields =
                listOf(
                    HiddenField("pagePath", pagePath),
                    HiddenField("lang", ctx.lang),
                    HiddenField("layout", ctx.layout),
                ),
            refreshUrl = "/components/navigation/page",
        )
    }

    fun buildLanguageSelector(ctx: WebContext): SidebarSelector {
        val i18n = ctx.i18n
        val pagePath = ctx.request.query("pagePath").orEmpty()

        return SidebarSelector(
            heading = i18n.translate("web.sidebar.language"),
            label = i18n.translate("web.sidebar.language.label"),
            selectId = "language-selector",
            selectName = "lang",
            options =
                listOf("en" to "web.language.english", "fr" to "web.language.french").map {
                    (id, key) ->
                    ShellOption(id, i18n.translate(key), id, id == ctx.lang)
                },
            hiddenFields =
                listOf(
                    HiddenField("pagePath", pagePath),
                    HiddenField("theme", ctx.theme),
                    HiddenField("layout", ctx.layout),
                ),
            refreshUrl = "/components/navigation/page",
        )
    }

    fun buildLayoutSelector(ctx: WebContext): SidebarSelector {
        val i18n = ctx.i18n
        val pagePath = ctx.request.query("pagePath").orEmpty()

        return SidebarSelector(
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
                    .map { (id, key) ->
                        ShellOption(id, i18n.translate(key), id, id == ctx.layout)
                    },
            hiddenFields =
                listOf(
                    HiddenField("pagePath", pagePath),
                    HiddenField("theme", ctx.theme),
                    HiddenField("lang", ctx.lang),
                ),
            refreshUrl = "/components/navigation/page",
        )
    }

    fun buildNavigationRefresh(ctx: WebContext): Page<out ViewModel> {
        val pagePath = ctx.request.query("pagePath") ?: "/"
        return when {
            pagePath == "/" -> buildHomePage(ctx)
            pagePath == "/contacts" -> buildContactsPage(ctx)
            pagePath == "/auth" -> buildAuthPage(ctx)
            pagePath == "/admin/dev" ->
                buildDevDashboardPage(ctx, "", emptyMap(), OutboxStatsViewModel(0, 0, 0), "")
            pagePath == "/admin/users" -> buildUserAdminPage(ctx)
            pagePath == "/admin/audit" -> buildAuditLogPage(ctx)
            pagePath.startsWith("/errors") -> buildErrorPage(ctx, "not-found")
            else -> buildHomePage(ctx)
        }
    }

    fun buildChangePasswordPage(ctx: WebContext): Page<ChangePasswordPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.password.title"), "/auth")
        return Page(shell = shell, data = ChangePasswordPage(form = buildChangePasswordForm(ctx)))
    }

    fun buildChangePasswordForm(ctx: WebContext): ChangePasswordForm {
        val i18n = ctx.i18n
        return ChangePasswordForm(
            title = i18n.translate("web.password.title"),
            currentPasswordLabel = i18n.translate("web.password.current"),
            newPasswordLabel = i18n.translate("web.password.new"),
            confirmPasswordLabel = i18n.translate("web.password.confirm"),
            submitLabel = i18n.translate("web.password.submit"),
            submitUrl = ctx.url("/auth/components/change-password"),
            currentPasswordPlaceholder = i18n.translate("web.password.current.placeholder"),
            newPasswordPlaceholder = i18n.translate("web.password.new.placeholder"),
            confirmPasswordPlaceholder = i18n.translate("web.password.confirm.placeholder"),
        )
    }

    fun buildResetPasswordPage(ctx: WebContext, token: String): Page<ResetPasswordPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.reset.title"), "/auth")
        return Page(
            shell = shell,
            data =
                ResetPasswordPage(
                    token = token,
                    newPasswordLabel = i18n.translate("web.reset.newPassword"),
                    confirmPasswordLabel = i18n.translate("web.reset.confirmPassword"),
                    submitLabel = i18n.translate("web.reset.submit"),
                    submitUrl = ctx.url("/auth/components/reset-confirm"),
                    newPasswordPlaceholder = i18n.translate("web.auth.placeholder.password"),
                    confirmPasswordPlaceholder = i18n.translate("web.password.confirm.placeholder"),
                ),
        )
    }

    fun buildUserAdminPage(ctx: WebContext, limit: Int = 20, offset: Int = 0): Page<UserAdminPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.admin.users.title"), "/admin/users")
        val allUsers = securityService?.listUsers() ?: emptyList()
        val currentUserId = ctx.user?.id?.toString()
        val safeOffset = offset.coerceIn(0, maxOf(0, allUsers.size - 1))
        val pageUsers = allUsers.drop(safeOffset).take(limit)
        val currentPage = (safeOffset / limit) + 1
        val hasPrevious = safeOffset > 0
        val hasNext = safeOffset + limit < allUsers.size
        val previousUrl =
            ctx.url("/admin/users?limit=$limit&offset=${maxOf(0, safeOffset - limit)}")
        val nextUrl = ctx.url("/admin/users?limit=$limit&offset=${safeOffset + limit}")

        return Page(
            shell = shell,
            data =
                UserAdminPage(
                    title = i18n.translate("web.admin.users.title"),
                    description = i18n.translate("web.admin.users.description"),
                    users =
                        pageUsers.map { u ->
                            UserAdminRow(
                                id = u.id,
                                username = u.username,
                                email = u.email,
                                role = u.role,
                                enabled = u.enabled,
                                toggleEnabledUrl = ctx.url("/admin/users/${u.id}/toggle-enabled"),
                                toggleRoleUrl = ctx.url("/admin/users/${u.id}/toggle-role"),
                                isSelf = u.id == currentUserId,
                            )
                        },
                    currentPage = currentPage,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    previousUrl = previousUrl,
                    nextUrl = nextUrl,
                    headerUsername = i18n.translate("web.admin.users.header.username"),
                    headerEmail = i18n.translate("web.admin.users.header.email"),
                    headerRole = i18n.translate("web.admin.users.header.role"),
                    headerEnabled = i18n.translate("web.admin.users.header.enabled"),
                    headerActions = i18n.translate("web.admin.users.header.actions"),
                    actionDisable = i18n.translate("web.admin.users.action.disable"),
                    actionEnable = i18n.translate("web.admin.users.action.enable"),
                    actionDemote = i18n.translate("web.admin.users.action.demote"),
                    actionPromote = i18n.translate("web.admin.users.action.promote"),
                    selfLabel = i18n.translate("web.admin.users.self"),
                    previousLabel = i18n.translate("web.admin.pagination.previous"),
                    nextLabel = i18n.translate("web.admin.pagination.next"),
                ),
        )
    }

    fun buildAuditLogPage(ctx: WebContext, limit: Int = 20, offset: Int = 0): Page<AuditLogPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.admin.audit.title"), "/admin/audit")
        val allEntries = securityService?.getAuditLog(limit = limit + offset + 1) ?: emptyList()
        val safeOffset = offset.coerceIn(0, maxOf(0, allEntries.size - 1))
        val pageEntries = allEntries.drop(safeOffset).take(limit)
        val currentPage = (safeOffset / limit) + 1
        val hasPrevious = safeOffset > 0
        val hasNext = allEntries.size > safeOffset + limit
        val previousUrl =
            ctx.url("/admin/audit?limit=$limit&offset=${maxOf(0, safeOffset - limit)}")
        val nextUrl = ctx.url("/admin/audit?limit=$limit&offset=${safeOffset + limit}")

        return Page(
            shell = shell,
            data =
                AuditLogPage(
                    title = i18n.translate("web.admin.audit.title"),
                    entries =
                        pageEntries.map { e ->
                            AuditEntryViewModel(
                                actorUsername = e.actorUsername ?: "",
                                targetUsername = e.targetUsername ?: "",
                                action = e.action,
                                detail = e.detail ?: "",
                                timestamp = e.createdAt.toString(),
                            )
                        },
                    currentPage = currentPage,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    previousUrl = previousUrl,
                    nextUrl = nextUrl,
                    headerWhen = i18n.translate("web.admin.audit.header.when"),
                    headerActor = i18n.translate("web.admin.audit.header.actor"),
                    headerAction = i18n.translate("web.admin.audit.header.action"),
                    headerTarget = i18n.translate("web.admin.audit.header.target"),
                    headerDetail = i18n.translate("web.admin.audit.header.detail"),
                    previousLabel = i18n.translate("web.admin.pagination.previous"),
                    nextLabel = i18n.translate("web.admin.pagination.next"),
                ),
        )
    }

    fun buildApiKeysPage(
        ctx: WebContext,
        newKey: String? = null,
        newKeyName: String? = null,
    ): Page<ApiKeysPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.apikeys.title"), "/auth/api-keys")
        val userId = ctx.user?.id ?: throw IllegalStateException("User not logged in")
        val keys = securityService?.listApiKeys(userId) ?: emptyList()

        return Page(
            shell = shell,
            data =
                ApiKeysPage(
                    title = i18n.translate("web.apikeys.title"),
                    keys = keys,
                    createUrl = ctx.url("/auth/api-keys/create"),
                    newKey = newKey,
                    newKeyName = newKeyName,
                    description = i18n.translate("web.apikeys.description"),
                    newKeyBanner = i18n.translate("web.apikeys.created"),
                    createLabel = i18n.translate("web.apikeys.create"),
                    keyNameLabel = i18n.translate("web.apikeys.name"),
                    keyNamePlaceholder = i18n.translate("web.apikeys.name.placeholder"),
                    yourKeysHeading = i18n.translate("web.apikeys.your.keys"),
                    emptyLabel = i18n.translate("web.apikeys.empty"),
                    headerPrefix = i18n.translate("web.apikeys.table.prefix"),
                    headerName = i18n.translate("web.apikeys.table.name"),
                    headerCreated = i18n.translate("web.apikeys.table.created"),
                    headerLastUsed = i18n.translate("web.apikeys.table.last.used"),
                    headerActions = i18n.translate("web.apikeys.table.actions"),
                    neverLabel = i18n.translate("web.apikeys.table.never"),
                    deleteConfirm = i18n.translate("web.apikeys.delete.confirm"),
                    deleteLabel = i18n.translate("web.apikeys.delete"),
                ),
        )
    }

    fun buildProfilePage(ctx: WebContext): Page<ProfilePage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.profile.title"), "/auth/profile")
        val user = ctx.user!!
        val gravatarHash =
            java.security.MessageDigest.getInstance("MD5")
                .digest(user.email.trim().lowercase().toByteArray())
                .joinToString("") { "%02x".format(it) }
        val avatarUrl =
            user.avatarUrl?.takeIf { it.isNotBlank() }
                ?: "https://www.gravatar.com/avatar/$gravatarHash?d=identicon&s=80"

        return Page(
            shell = shell,
            data =
                ProfilePage(
                    title = i18n.translate("web.profile.title"),
                    username = user.username,
                    email = user.email,
                    role = user.role.name,
                    avatarUrl = avatarUrl,
                    submitUrl = ctx.url("/auth/components/profile-update"),
                    usernameLabel = i18n.translate("web.profile.username"),
                    usernamePlaceholder = i18n.translate("web.profile.username.placeholder"),
                    emailLabel = i18n.translate("web.profile.email"),
                    emailPlaceholder = i18n.translate("web.profile.email.placeholder"),
                    avatarLabel = i18n.translate("web.profile.avatar"),
                    avatarPlaceholder = i18n.translate("web.profile.avatar.placeholder"),
                    submitLabel = i18n.translate("web.profile.submit"),
                    emailNotificationsEnabled = user.emailNotificationsEnabled,
                    pushNotificationsEnabled = user.pushNotificationsEnabled,
                    notificationPrefsUrl = ctx.url("/auth/notification-preferences"),
                    notificationPrefsLabel = i18n.translate("web.profile.notif.prefs"),
                    emailNotifLabel = i18n.translate("web.profile.notif.email"),
                    pushNotifLabel = i18n.translate("web.profile.notif.push"),
                    savePrefsLabel = i18n.translate("web.profile.notif.save"),
                    deleteAccountUrl = ctx.url("/auth/account/delete"),
                    dangerZoneLabel = i18n.translate("web.profile.danger.zone"),
                    deleteAccountLabel = i18n.translate("web.profile.delete.account"),
                    deleteAccountDescription =
                        i18n.translate("web.profile.delete.account.description"),
                    deleteAccountConfirmLabel = i18n.translate("web.profile.delete.confirm"),
                    deleteAccountCancelLabel = i18n.translate("web.profile.delete.cancel"),
                ),
        )
    }

    fun buildNotificationsPage(ctx: WebContext): Page<NotificationsPage> {
        val i18n = ctx.i18n
        val user = ctx.user!!
        val unreadCount = notificationService?.countUnread(user.id) ?: 0
        val shell =
            ctx.shell(i18n.translate("web.notifications.title"), "/notifications")
                .copy(
                    notificationsUrl = ctx.url("/notifications"),
                    unreadNotificationCount = unreadCount,
                )
        val notifications = notificationService?.listForUser(user.id) ?: emptyList()
        return Page(
            shell = shell,
            data =
                NotificationsPage(
                    title = i18n.translate("web.notifications.title"),
                    description = i18n.translate("web.notifications.description"),
                    notifications =
                        notifications.map { n ->
                            NotificationViewModel(
                                id = n.id.toString(),
                                title = n.title,
                                body = n.body,
                                type = n.type,
                                read = n.isRead,
                                timeAgo = formatTimeAgo(n.createdAt),
                                markReadUrl = ctx.url("/notifications/${n.id}/read"),
                            )
                        },
                    unreadCount = unreadCount,
                    markAllReadUrl = ctx.url("/notifications/read-all"),
                    emptyLabel = i18n.translate("web.notifications.empty"),
                    markAllReadLabel = i18n.translate("web.notifications.mark.all.read"),
                    markReadLabel = i18n.translate("web.notifications.mark.read"),
                    readLabel = i18n.translate("web.notifications.read"),
                ),
        )
    }

    fun buildNotificationBell(ctx: WebContext): NotificationBellFragment {
        val unreadCount = ctx.user?.id?.let { notificationService?.countUnread(it) } ?: 0
        return NotificationBellFragment(
            unreadCount = unreadCount,
            notificationsUrl = ctx.url("/notifications"),
        )
    }

    fun buildContactForm(ctx: WebContext, syncId: String? = null): ContactFormFragment {
        val i18n = ctx.i18n
        val existing = syncId?.let { contactService?.getContactBySyncId(it) }
        return ContactFormFragment(
            syncId = existing?.syncId ?: "",
            name = existing?.name ?: "",
            emails = existing?.emails?.joinToString(", ") ?: "",
            phones = existing?.phones?.joinToString(", ") ?: "",
            socialMedia = existing?.socialMedia?.joinToString(", ") ?: "",
            company = existing?.company ?: "",
            companyAddress = existing?.companyAddress ?: "",
            department = existing?.department ?: "",
            submitUrl = ctx.url(if (syncId != null) "/contacts/$syncId/update" else "/contacts"),
            isEdit = syncId != null,
            titleLabel =
                if (syncId != null) i18n.translate("web.contacts.edit")
                else i18n.translate("web.contacts.create"),
            nameLabel = i18n.translate("web.contacts.form.name"),
            emailsLabel = i18n.translate("web.contacts.form.emails"),
            phonesLabel = i18n.translate("web.contacts.form.phones"),
            socialLabel = i18n.translate("web.contacts.form.social"),
            companyLabel = i18n.translate("web.contacts.form.company"),
            addressLabel = i18n.translate("web.contacts.form.address"),
            departmentLabel = i18n.translate("web.contacts.form.department"),
            saveLabel = i18n.translate("web.contacts.form.save"),
            cancelLabel = i18n.translate("web.contacts.form.cancel"),
        )
    }

    private fun formatTimeAgo(instant: java.time.Instant): String {
        val seconds = java.time.Duration.between(instant, java.time.Instant.now()).seconds
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }
}
