package io.github.rygel.outerstellar.platform.web

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.service.MessageService
import java.util.concurrent.TimeUnit

private const val GRAVATAR_CACHE_MAX_SIZE = 10_000L

private val gravatarCache: Cache<String, String> =
    Caffeine.newBuilder().maximumSize(GRAVATAR_CACHE_MAX_SIZE).expireAfterAccess(1, TimeUnit.HOURS).build()

fun gravatarUrl(email: String, customUrl: String?): String {
    if (!customUrl.isNullOrBlank()) return customUrl
    val normalized = email.trim().lowercase()
    return gravatarCache.get(normalized) {
        val md5 = java.security.MessageDigest.getInstance("MD5") // nosemgrep
        val hash = md5.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        "https://www.gravatar.com/avatar/$hash?d=identicon&s=80"
    }!!
}

@Suppress("TooManyFunctions")
open class WebPageFactory(
    private val repository: MessageRepository? = null,
    private val messageService: MessageService? = null,
    private val contactService: io.github.rygel.outerstellar.platform.service.ContactService? = null,
    private val securityService: io.github.rygel.outerstellar.platform.security.SecurityService? = null,
    private val notificationService: io.github.rygel.outerstellar.platform.service.NotificationService? = null,
    private val appleOAuthEnabled: Boolean = false,
    private val userAdminService: io.github.rygel.outerstellar.platform.security.UserAdminService? = null,
) {
    fun buildContactsPage(
        shellRenderer: ShellRenderer,
        query: String? = null,
        limit: Int = 12,
        offset: Int = 0,
    ): Page<ContactsPage> = contactsFactory.buildContactsPage(shellRenderer, query, limit, offset)

    fun buildContactForm(shellRenderer: ShellRenderer, syncId: String? = null): ContactFormFragment =
        contactsFactory.buildContactForm(shellRenderer, syncId)

    fun buildHomePage(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        query: String? = null,
        limit: Int = 10,
        offset: Int = 0,
        year: Int? = null,
    ): Page<HomePage> = homeFactory.buildHomePage(ctx, shellRenderer, query, limit, offset, year)

    fun buildMessageList(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        query: String? = null,
        limit: Int = 10,
        offset: Int = 0,
        year: Int? = null,
        isTrash: Boolean = false,
    ): MessageListViewModel = homeFactory.buildMessageList(ctx, shellRenderer, query, limit, offset, year, isTrash)

    fun buildTrashPage(ctx: RequestContext, shellRenderer: ShellRenderer): Page<TrashPage> =
        homeFactory.buildTrashPage(ctx, shellRenderer)

    fun buildContactTrashList(shellRenderer: ShellRenderer): ContactTrashListViewModel =
        contactsFactory.buildTrashContactList(shellRenderer)

    fun buildMessageEditForm(shellRenderer: ShellRenderer, syncId: String): MessageEditFormFragment =
        homeFactory.buildMessageEditForm(shellRenderer, syncId)

    fun buildFooterStatus(shellRenderer: ShellRenderer): FooterStatusFragment =
        infraFactory.buildFooterStatus(shellRenderer)

    fun buildConflictResolveModal(shellRenderer: ShellRenderer, syncId: String): ConflictResolveViewModel =
        infraFactory.buildConflictResolveModal(shellRenderer, syncId)

    fun buildAuthPage(ctx: RequestContext, shellRenderer: ShellRenderer): Page<AuthViewModel> =
        authPageFactory.buildAuthPage(ctx, shellRenderer)

    fun buildAuthForm(ctx: RequestContext, shellRenderer: ShellRenderer, mode: String): AuthFormFragment =
        authPageFactory.buildAuthForm(ctx, shellRenderer, mode)

    fun buildAuthResult(shellRenderer: ShellRenderer, formValues: Map<String, String?>): AuthResultFragment =
        authPageFactory.buildAuthResult(shellRenderer, formValues)

    fun buildChangePasswordPage(shellRenderer: ShellRenderer): Page<ChangePasswordPage> =
        authPageFactory.buildChangePasswordPage(shellRenderer)

    fun buildChangePasswordForm(shellRenderer: ShellRenderer): ChangePasswordForm =
        authPageFactory.buildChangePasswordForm(shellRenderer)

    fun buildResetPasswordPage(shellRenderer: ShellRenderer, token: String): Page<ResetPasswordPage> =
        authPageFactory.buildResetPasswordPage(shellRenderer, token)

    fun buildErrorPage(shellRenderer: ShellRenderer, kind: String): Page<ErrorPage> =
        errorPageFactory.buildErrorPage(shellRenderer, kind)

    fun buildErrorHelp(shellRenderer: ShellRenderer, kind: String): ErrorHelpFragment =
        errorPageFactory.buildErrorHelp(shellRenderer, kind)

    fun buildThemeSelector(ctx: RequestContext, shellRenderer: ShellRenderer): SidebarSelector =
        sidebarFactory.buildThemeSelector(ctx, shellRenderer)

    fun buildLanguageSelector(ctx: RequestContext, shellRenderer: ShellRenderer): SidebarSelector =
        sidebarFactory.buildLanguageSelector(ctx, shellRenderer)

    fun buildLayoutSelector(ctx: RequestContext, shellRenderer: ShellRenderer): SidebarSelector =
        sidebarFactory.buildLayoutSelector(ctx, shellRenderer)

    fun buildSettingsPage(shellRenderer: ShellRenderer, activeTab: String = "profile"): Page<SettingsPage> =
        settingsPageFactory.buildSettingsPage(shellRenderer, activeTab)

    fun buildSettingsFragment(ctx: RequestContext, shellRenderer: ShellRenderer, tab: String): SettingsTabContent =
        settingsPageFactory.buildSettingsFragment(ctx, shellRenderer, tab)

    fun buildSearchPage(
        shellRenderer: ShellRenderer,
        query: String,
        providers: List<io.github.rygel.outerstellar.platform.search.SearchProvider>,
        limit: Int = 20,
        typeFilter: String = "",
    ): Page<SearchPage> = searchPageFactory.buildSearchPage(shellRenderer, query, providers, limit, typeFilter)

    fun buildDevDashboardPage(
        shellRenderer: ShellRenderer,
        metrics: String,
        cacheStats: Map<String, Any>,
        outboxStats: OutboxStatsViewModel,
        telemetryStatus: String,
    ): Page<DevDashboardPage> =
        devDashboardPageFactory.buildDevDashboardPage(shellRenderer, metrics, cacheStats, outboxStats, telemetryStatus)

    private val adminPageFactory by lazy { AdminPageFactory(securityService, notificationService, userAdminService) }
    private val authPageFactory by lazy { AuthPageFactory(appleOAuthEnabled) }
    private val errorPageFactory by lazy { ErrorPageFactory() }
    private val sidebarFactory by lazy { SidebarFactory() }
    private val settingsPageFactory by lazy { SettingsPageFactory(adminPageFactory, authPageFactory, sidebarFactory) }
    private val searchPageFactory by lazy { SearchPageFactory() }
    private val devDashboardPageFactory by lazy { DevDashboardPageFactory() }
    private val contactsFactory by lazy { ContactsPageFactory(contactService) }
    private val homeFactory by lazy { HomePageFactory(messageService, contactService) }
    private val infraFactory by lazy { InfraPageFactory(repository) }

    fun buildUserAdminPage(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        limit: Int = 20,
        offset: Int = 0,
    ): Page<UserAdminPage> = adminPageFactory.buildUserAdminPage(ctx, shellRenderer, limit, offset)

    fun buildAuditLogPage(shellRenderer: ShellRenderer, limit: Int = 20, offset: Int = 0): Page<AuditLogPage> =
        adminPageFactory.buildAuditLogPage(shellRenderer, limit, offset)

    fun buildApiKeysPage(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        newKey: String? = null,
        newKeyName: String? = null,
    ): Page<ApiKeysPage> = adminPageFactory.buildApiKeysPage(ctx, shellRenderer, newKey, newKeyName)

    fun buildProfilePage(ctx: RequestContext, shellRenderer: ShellRenderer): Page<ProfilePage> =
        adminPageFactory.buildProfilePage(ctx, shellRenderer)

    fun buildNotificationsPage(ctx: RequestContext, shellRenderer: ShellRenderer): Page<NotificationsPage> =
        adminPageFactory.buildNotificationsPage(ctx, shellRenderer)

    fun buildNotificationBell(ctx: RequestContext, shellRenderer: ShellRenderer): NotificationBellFragment =
        adminPageFactory.buildNotificationBell(ctx, shellRenderer)
}
