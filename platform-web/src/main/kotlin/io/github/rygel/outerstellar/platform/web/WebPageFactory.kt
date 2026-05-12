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
) {
    fun buildContactsPage(
        ctx: WebContext,
        query: String? = null,
        limit: Int = 12,
        offset: Int = 0,
    ): Page<ContactsPage> = contactsFactory.buildContactsPage(ctx, query, limit, offset)

    fun buildContactForm(ctx: WebContext, syncId: String? = null): ContactFormFragment =
        contactsFactory.buildContactForm(ctx, syncId)

    fun buildHomePage(
        ctx: WebContext,
        query: String? = null,
        limit: Int = 10,
        offset: Int = 0,
        year: Int? = null,
    ): Page<HomePage> = homeFactory.buildHomePage(ctx, query, limit, offset, year)

    fun buildMessageList(
        ctx: WebContext,
        query: String? = null,
        limit: Int = 10,
        offset: Int = 0,
        year: Int? = null,
        isTrash: Boolean = false,
    ): MessageListViewModel = homeFactory.buildMessageList(ctx, query, limit, offset, year, isTrash)

    fun buildTrashPage(ctx: WebContext): Page<TrashPage> = homeFactory.buildTrashPage(ctx)

    fun buildFooterStatus(ctx: WebContext): FooterStatusFragment = infraFactory.buildFooterStatus(ctx)

    fun buildConflictResolveModal(ctx: WebContext, syncId: String): ConflictResolveViewModel =
        infraFactory.buildConflictResolveModal(ctx, syncId)

    fun buildAuthPage(ctx: WebContext): Page<AuthViewModel> = authPageFactory.buildAuthPage(ctx)

    fun buildAuthForm(ctx: WebContext, mode: String): AuthFormFragment = authPageFactory.buildAuthForm(ctx, mode)

    fun buildAuthResult(ctx: WebContext, formValues: Map<String, String?>): AuthResultFragment =
        authPageFactory.buildAuthResult(ctx, formValues)

    fun buildChangePasswordPage(ctx: WebContext): Page<ChangePasswordPage> =
        authPageFactory.buildChangePasswordPage(ctx)

    fun buildChangePasswordForm(ctx: WebContext): ChangePasswordForm = authPageFactory.buildChangePasswordForm(ctx)

    fun buildResetPasswordPage(ctx: WebContext, token: String): Page<ResetPasswordPage> =
        authPageFactory.buildResetPasswordPage(ctx, token)

    fun buildErrorPage(ctx: WebContext, kind: String): Page<ErrorPage> = errorPageFactory.buildErrorPage(ctx, kind)

    fun buildErrorHelp(ctx: WebContext, kind: String): ErrorHelpFragment = errorPageFactory.buildErrorHelp(ctx, kind)

    fun buildThemeSelector(ctx: WebContext): SidebarSelector = sidebarFactory.buildThemeSelector(ctx)

    fun buildLanguageSelector(ctx: WebContext): SidebarSelector = sidebarFactory.buildLanguageSelector(ctx)

    fun buildLayoutSelector(ctx: WebContext): SidebarSelector = sidebarFactory.buildLayoutSelector(ctx)

    fun buildSettingsPage(ctx: WebContext, activeTab: String = "profile"): Page<SettingsPage> =
        settingsPageFactory.buildSettingsPage(ctx, activeTab)

    fun buildSearchPage(
        ctx: WebContext,
        query: String,
        providers: List<io.github.rygel.outerstellar.platform.search.SearchProvider>,
        limit: Int = 20,
    ): Page<SearchPage> = searchPageFactory.buildSearchPage(ctx, query, providers, limit)

    fun buildDevDashboardPage(
        ctx: WebContext,
        metrics: String,
        cacheStats: Map<String, Any>,
        outboxStats: OutboxStatsViewModel,
        telemetryStatus: String,
    ): Page<DevDashboardPage> =
        devDashboardPageFactory.buildDevDashboardPage(ctx, metrics, cacheStats, outboxStats, telemetryStatus)

    // Delegated factory methods - kept for backward compatibility
    private val adminPageFactory by lazy { AdminPageFactory(securityService, notificationService) }
    private val authPageFactory by lazy { AuthPageFactory() }
    private val errorPageFactory by lazy { ErrorPageFactory() }
    private val sidebarFactory by lazy { SidebarFactory() }
    private val settingsPageFactory by lazy { SettingsPageFactory() }
    private val searchPageFactory by lazy { SearchPageFactory() }
    private val devDashboardPageFactory by lazy { DevDashboardPageFactory() }
    private val contactsFactory by lazy { ContactsPageFactory(contactService) }
    private val homeFactory by lazy { HomePageFactory(messageService) }
    private val infraFactory by lazy { InfraPageFactory(repository) }

    fun buildUserAdminPage(ctx: WebContext, limit: Int = 20, offset: Int = 0): Page<UserAdminPage> =
        adminPageFactory.buildUserAdminPage(ctx, limit, offset)

    fun buildAuditLogPage(ctx: WebContext, limit: Int = 20, offset: Int = 0): Page<AuditLogPage> =
        adminPageFactory.buildAuditLogPage(ctx, limit, offset)

    fun buildApiKeysPage(ctx: WebContext, newKey: String? = null, newKeyName: String? = null): Page<ApiKeysPage> =
        adminPageFactory.buildApiKeysPage(ctx, newKey, newKeyName)

    fun buildProfilePage(ctx: WebContext): Page<ProfilePage> = adminPageFactory.buildProfilePage(ctx)

    fun buildNotificationsPage(ctx: WebContext): Page<NotificationsPage> = adminPageFactory.buildNotificationsPage(ctx)

    fun buildNotificationBell(ctx: WebContext): NotificationBellFragment = adminPageFactory.buildNotificationBell(ctx)
}
