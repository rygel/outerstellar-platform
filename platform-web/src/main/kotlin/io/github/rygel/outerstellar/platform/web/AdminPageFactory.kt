package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException

private const val ADMIN_SECONDS_PER_MINUTE = 60
private const val ADMIN_SECONDS_PER_HOUR = 3600
private const val ADMIN_SECONDS_PER_DAY = 86400

class AdminPageFactory(
    private val securityService: io.github.rygel.outerstellar.platform.security.SecurityService? = null,
    private val notificationService: io.github.rygel.outerstellar.platform.service.NotificationService? = null,
) {

    fun buildUserAdminPage(ctx: WebContext, limit: Int = 20, offset: Int = 0): Page<UserAdminPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.admin.users.title"), "/admin/users")
        val totalCount = securityService?.countUsers() ?: 0L
        val safeOffset = offset.coerceIn(0, maxOf(0, totalCount.toInt() - 1))
        val pageUsers = securityService?.listUsers(limit, safeOffset) ?: emptyList()
        val currentUserId = ctx.user?.id?.toString()
        val currentPage = (safeOffset / limit) + 1
        val hasPrevious = safeOffset > 0
        val hasNext = safeOffset + limit < totalCount
        val previousUrl = ctx.url("/admin/users?limit=$limit&offset=${maxOf(0, safeOffset - limit)}")
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
        val totalCount = securityService?.countAuditEntries() ?: 0L
        val safeOffset = offset.coerceIn(0, maxOf(0, totalCount.toInt() - 1))
        val pageEntries = securityService?.getAuditLog(limit, safeOffset) ?: emptyList()
        val currentPage = (safeOffset / limit) + 1
        val hasPrevious = safeOffset > 0
        val hasNext = safeOffset + limit < totalCount
        val previousUrl = ctx.url("/admin/audit?limit=$limit&offset=${maxOf(0, safeOffset - limit)}")
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

    fun buildApiKeysPage(ctx: WebContext, newKey: String? = null, newKeyName: String? = null): Page<ApiKeysPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.apikeys.title"), "/auth/api-keys")
        val userId = checkNotNull(ctx.user?.id) { "User not logged in" }
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
        val user = ctx.user ?: throw InsufficientPermissionException("Authentication required")
        val avatarUrl = gravatarUrl(user.email, user.avatarUrl)

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
                    deleteAccountDescription = i18n.translate("web.profile.delete.account.description"),
                    deleteAccountConfirmLabel = i18n.translate("web.profile.delete.confirm"),
                    deleteAccountCancelLabel = i18n.translate("web.profile.delete.cancel"),
                ),
        )
    }

    fun buildNotificationsPage(ctx: WebContext): Page<NotificationsPage> {
        val i18n = ctx.i18n
        val user = ctx.user ?: throw InsufficientPermissionException("Authentication required")
        val unreadCount = notificationService?.countUnread(user.id) ?: 0
        val shell =
            ctx.shell(i18n.translate("web.notifications.title"), "/notifications")
                .copy(notificationsUrl = ctx.url("/notifications"), unreadNotificationCount = unreadCount)
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
        return NotificationBellFragment(unreadCount = unreadCount, notificationsUrl = ctx.url("/notifications"))
    }

    fun formatTimeAgo(instant: java.time.Instant): String {
        val seconds = java.time.Duration.between(instant, java.time.Instant.now()).seconds
        return when {
            seconds < ADMIN_SECONDS_PER_MINUTE -> "just now"
            seconds < ADMIN_SECONDS_PER_HOUR -> "${seconds / ADMIN_SECONDS_PER_MINUTE}m ago"
            seconds < ADMIN_SECONDS_PER_DAY -> "${seconds / ADMIN_SECONDS_PER_HOUR}h ago"
            else -> "${seconds / ADMIN_SECONDS_PER_DAY}d ago"
        }
    }
}
