package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncMessage

private const val MIN_PASSWORD_LENGTH = 8

private val gravatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()

fun gravatarUrl(email: String, customUrl: String?): String {
    if (!customUrl.isNullOrBlank()) return customUrl
    return gravatarCache.computeIfAbsent(email.trim().lowercase()) { normalized ->
        val hash =
            java.security.MessageDigest.getInstance("MD5").digest(normalized.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        "https://www.gravatar.com/avatar/$hash?d=identicon&s=80"
    }
}

private const val DEFAULT_LIMIT = 10
private const val HTTP_STATUS_NOT_FOUND = 404
private const val HTTP_STATUS_SERVER_ERROR = 500

@Suppress("TooManyFunctions")
open class WebPageFactory(
    private val repository: MessageRepository? = null,
    private val messageService: MessageService? = null,
    private val contactService: io.github.rygel.outerstellar.platform.service.ContactService? = null,
    private val securityService: io.github.rygel.outerstellar.platform.security.SecurityService? = null,
    private val notificationService: io.github.rygel.outerstellar.platform.service.NotificationService? = null,
) {
    private val messageListComponent = messageService?.let { MessageListComponent(it) }

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
        val messageList =
            checkNotNull(messageListComponent) { "MessageService is required for home page" }
                .build(ctx, query, limit, offset, year)

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
                            HomeFeature(i18n.translate("web.feature.db.label"), i18n.translate("web.feature.db.value")),
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
        return checkNotNull(messageListComponent) { "MessageService is required for message list" }
            .build(ctx, query, limit, offset, year, isTrash)
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
        val msgCount = repository?.countMessages() ?: 0L
        val dirtyCount = repository?.countDirtyMessages() ?: 0L
        return FooterStatusFragment(text = i18n.translate("web.footer.status", msgCount, dirtyCount))
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
            checkNotNull(repository) { "MessageRepository is required for conflict resolution" }.findBySyncId(syncId)
                ?: throw io.github.rygel.outerstellar.platform.model.MessageNotFoundException(syncId)
        val serverVersion = org.http4k.format.Jackson.asA(message.syncConflict!!, SyncMessage::class)

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
                ThemeCatalog.allThemes().map { theme ->
                    ShellOption(
                        id = theme.id,
                        label = theme.name,
                        url = theme.id,
                        active = theme.id == ctx.theme,
                        previewColors =
                            ThemePreviewColors(
                                background = theme.colors["background"] ?: "#1e1e1e",
                                foreground = theme.colors["foreground"] ?: "#d4d4d4",
                                accent = theme.colors["accent"] ?: "#007acc",
                                componentBackground = theme.colors["componentBackground"] ?: "#252526",
                            ),
                    )
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
                listOf("en" to "web.language.english", "fr" to "web.language.french").map { (id, key) ->
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
                listOf("nice" to "web.layout.nice", "cozy" to "web.layout.cozy", "compact" to "web.layout.compact")
                    .map { (id, key) -> ShellOption(id, i18n.translate(key), id, id == ctx.layout) },
            hiddenFields =
                listOf(
                    HiddenField("pagePath", pagePath),
                    HiddenField("theme", ctx.theme),
                    HiddenField("lang", ctx.lang),
                ),
            refreshUrl = "/components/navigation/page",
        )
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

    // Delegated admin methods - kept for backward compatibility
    private val adminPageFactory by lazy { AdminPageFactory(securityService, notificationService) }

    fun buildUserAdminPage(ctx: WebContext, limit: Int = 20, offset: Int = 0): Page<UserAdminPage> =
        adminPageFactory.buildUserAdminPage(ctx, limit, offset)

    fun buildAuditLogPage(ctx: WebContext, limit: Int = 20, offset: Int = 0): Page<AuditLogPage> =
        adminPageFactory.buildAuditLogPage(ctx, limit, offset)

    fun buildApiKeysPage(ctx: WebContext, newKey: String? = null, newKeyName: String? = null): Page<ApiKeysPage> =
        adminPageFactory.buildApiKeysPage(ctx, newKey, newKeyName)

    fun buildProfilePage(ctx: WebContext): Page<ProfilePage> = adminPageFactory.buildProfilePage(ctx)

    fun buildNotificationsPage(ctx: WebContext): Page<NotificationsPage> = adminPageFactory.buildNotificationsPage(ctx)

    fun buildNotificationBell(ctx: WebContext): NotificationBellFragment = adminPageFactory.buildNotificationBell(ctx)

    fun buildSearchPage(
        ctx: WebContext,
        query: String,
        providers: List<io.github.rygel.outerstellar.platform.search.SearchProvider>,
        limit: Int = 20,
    ): Page<SearchPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.search.title"), "/search")
        val results =
            if (query.isBlank()) {
                emptyList()
            } else {
                providers
                    .flatMap { it.search(query, limit) }
                    .sortedByDescending { it.score }
                    .take(limit)
                    .map { r ->
                        SearchResultViewModel(
                            id = r.id,
                            title = r.title,
                            subtitle = r.subtitle,
                            url = r.url,
                            type = r.type,
                        )
                    }
            }
        return Page(
            shell = shell,
            data =
                SearchPage(
                    title = i18n.translate("web.search.title"),
                    query = query,
                    results = results,
                    emptyLabel = i18n.translate("web.search.empty"),
                    searchPlaceholder = i18n.translate("web.search.placeholder"),
                    searchLabel = i18n.translate("web.search.label"),
                ),
        )
    }

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
            data = SettingsPage(title = i18n.translate("web.settings.title"), tabs = tabs, activeTab = normalizedTab),
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
                if (syncId != null) {
                    i18n.translate("web.contacts.edit")
                } else {
                    i18n.translate("web.contacts.create")
                },
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
}
