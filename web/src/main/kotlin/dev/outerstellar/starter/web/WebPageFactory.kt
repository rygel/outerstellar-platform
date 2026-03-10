package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.PaginationMetadata
import dev.outerstellar.starter.persistence.MessageRepository
import java.util.Locale
import org.http4k.core.Request
import org.http4k.template.ViewModel

private const val minimumPasswordLength = 8
private const val notFoundStatusCode = 404
private const val serverErrorStatusCode = 500

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
  val userName: String? = null,
  val isLoggedIn: Boolean = false,
  val logoutUrl: String? = null
)

data class HomeFeature(val label: String, val value: String)

data class Page<T : ViewModel>(
  val shell: ShellView,
  val data: T
) : ViewModel {
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
) : ViewModel

data class PaginationViewModel(
    val currentPage: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val previousUrl: String?,
    val nextUrl: String?,
    val pages: List<PageNumberViewModel>
)

data class PageNumberViewModel(
    val number: Int,
    val url: String,
    val isActive: Boolean
)

data class AuthModeTab(val key: String, val label: String, val url: String)

data class AuthPage(
  val heading: String,
  val intro: String,
  val helperText: String,
  val tabs: List<AuthModeTab>,
  val defaultFormUrl: String,
) : ViewModel

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
) : ViewModel

data class ErrorHelpFragment(val title: String, val items: List<String>) : ViewModel

data class FooterStatusFragment(val text: String) : ViewModel

data class DevDashboardPage(
  val metrics: String,
  val cacheStats: Map<String, Any>,
  val outboxPendingCount: Int,
  val outboxProcessedCount: Int,
  val outboxFailedCount: Int,
  val telemetryStatus: String,
) : ViewModel

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

@Suppress("TooManyFunctions")
class WebPageFactory(
    private val repository: MessageRepository,
    private val devDashboardEnabled: Boolean = false
) {
    private val messageListComponent = MessageListComponent(repository)

    fun buildHomePage(ctx: WebContext, query: String? = null, limit: Int = 10, offset: Int = 0, year: Int? = null): Page<HomePage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.home"), "/")
        val messageList = messageListComponent.build(ctx, query, limit, offset, year)

        return Page(
            shell = shell,
            data = HomePage(
                eyebrow = i18n.translate("web.home.eyebrow"),
                intro = i18n.translate("web.home.intro"),
                features = listOf(
                    HomeFeature(i18n.translate("web.feature.http.label"), i18n.translate("web.feature.http.value")),
                    HomeFeature(i18n.translate("web.feature.db.label"), i18n.translate("web.feature.db.value")),
                    HomeFeature(i18n.translate("web.feature.sync.label"), i18n.translate("web.feature.sync.value")),
                    HomeFeature(i18n.translate("web.feature.desktop.label"), i18n.translate("web.feature.desktop.value"))
                ),
                composerTitle = i18n.translate("web.home.composer.title"),
                composerIntro = i18n.translate("web.home.composer.intro"),
                authorPlaceholder = i18n.translate("web.home.composer.author"),
                contentPlaceholder = i18n.translate("web.home.composer.content"),
                submitLabel = i18n.translate("web.home.composer.submit"),
                submitUrl = ctx.url("/messages"),
                messageList = messageList
            )
        )
    }

    fun buildAuthPage(ctx: WebContext): Page<AuthPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.auth"), "/auth")
        val returnTo = ctx.request.query("returnTo") ?: "/"

        return Page(
            shell = shell,
            data = AuthPage(
                heading = i18n.translate("web.auth.heading"),
                intro = i18n.translate("web.auth.intro"),
                helperText = i18n.translate("web.auth.helper"),
                tabs = listOf(
                    AuthModeTab("sign-in", i18n.translate("web.auth.signin"), ctx.url("/auth/components/forms/sign-in?returnTo=$returnTo")),
                    AuthModeTab("register", i18n.translate("web.auth.register"), ctx.url("/auth/components/forms/register?returnTo=$returnTo")),
                    AuthModeTab("recover", i18n.translate("web.auth.recover"), ctx.url("/auth/components/forms/recover?returnTo=$returnTo"))
                ),
                defaultFormUrl = ctx.url("/auth/components/forms/sign-in?returnTo=$returnTo")
            )
        )
    }

    fun buildAuthForm(ctx: WebContext, mode: String): AuthFormFragment {
        val i18n = ctx.i18n
        val normalizedMode = when (mode) {
            "register", "recover" -> mode
            else -> "sign-in"
        }
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
            includeRememberField = normalizedMode == "sign-in"
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
        if (mode != "recover" && password.length < 8) errors += i18n.translate("web.auth.error.password")
        if (mode == "register" && confirmPassword != password) errors += i18n.translate("web.auth.error.confirm")

        return if (errors.isEmpty()) {
            AuthResultFragment(
                title = i18n.translate("web.auth.result.success.title"),
                message = i18n.translate("web.auth.result.success.body", email),
                toneClass = "panel-success"
            )
        } else {
            AuthResultFragment(
                title = i18n.translate("web.auth.result.error.title"),
                message = errors.joinToString(" "),
                toneClass = "panel-danger"
            )
        }
    }

    fun buildErrorPage(ctx: WebContext, kind: String): Page<ErrorPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.errors"), "/errors")
        val normalizedKind = if (kind == "server-error") "server-error" else "not-found"
        val statusCode = if (normalizedKind == "server-error") 500 else 404

        return Page(
            shell = shell,
            data = ErrorPage(
                statusCode = statusCode,
                heading = i18n.translate("web.error.$normalizedKind.title"),
                message = i18n.translate("web.error.$normalizedKind.message"),
                primaryActionLabel = i18n.translate("web.error.primary"),
                primaryActionUrl = ctx.url("/"),
                secondaryActionLabel = i18n.translate("web.error.secondary"),
                secondaryActionUrl = ctx.url("/auth"),
                helpButtonLabel = i18n.translate("web.error.help"),
                helpUrl = ctx.url("/errors/components/help/$normalizedKind")
            )
        )
    }

    fun buildErrorHelp(ctx: WebContext, kind: String): ErrorHelpFragment {
        val i18n = ctx.i18n
        val normalizedKind = if (kind == "server-error") "server-error" else "not-found"

        return ErrorHelpFragment(
            title = i18n.translate("web.error.$normalizedKind.help.title"),
            items = listOf(
                i18n.translate("web.error.$normalizedKind.help.item1"),
                i18n.translate("web.error.$normalizedKind.help.item2"),
                i18n.translate("web.error.$normalizedKind.help.item3")
            )
        )
    }

    fun buildMessageList(ctx: WebContext, query: String? = null, limit: Int = 10, offset: Int = 0, year: Int? = null): MessageListViewModel {
        return messageListComponent.build(ctx, query, limit, offset, year)
    }

    fun buildFooterStatus(ctx: WebContext): FooterStatusFragment {
        val i18n = ctx.i18n
        return FooterStatusFragment(
            // In a real app we'd use MessageService here too, keeping it simple for status line
            text = i18n.translate("web.footer.status", repository.listMessages().size, repository.listDirtyMessages().size)
        )
    }

    fun buildDevDashboardPage(
        ctx: WebContext,
        metrics: String,
        cacheStats: Map<String, Any>,
        outboxPendingCount: Int,
        outboxProcessedCount: Int,
        outboxFailedCount: Int,
        telemetryStatus: String
    ): Page<DevDashboardPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.dev"), "/admin/dev")

        return Page(
            shell = shell,
            data = DevDashboardPage(
                metrics = metrics,
                cacheStats = cacheStats,
                outboxPendingCount = outboxPendingCount,
                outboxProcessedCount = outboxProcessedCount,
                outboxFailedCount = outboxFailedCount,
                telemetryStatus = telemetryStatus
            )
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
            options = ThemeCatalog.allThemes().map { ShellOption(it.id, it.name, it.id, it.id == ctx.theme) },
            hiddenFields = listOf(HiddenField("pagePath", pagePath), HiddenField("lang", ctx.lang), HiddenField("layout", ctx.layout)),
            refreshUrl = "/components/navigation/page"
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
            options = listOf("en" to "web.language.english", "fr" to "web.language.french").map { (id, key) ->
                ShellOption(id, i18n.translate(key), id, id == ctx.lang)
            },
            hiddenFields = listOf(HiddenField("pagePath", pagePath), HiddenField("theme", ctx.theme), HiddenField("layout", ctx.layout)),
            refreshUrl = "/components/navigation/page"
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
            options = listOf("nice" to "web.layout.nice", "cozy" to "web.layout.cozy", "compact" to "web.layout.compact").map { (id, key) ->
                ShellOption(id, i18n.translate(key), id, id == ctx.layout)
            },
            hiddenFields = listOf(HiddenField("pagePath", pagePath), HiddenField("theme", ctx.theme), HiddenField("lang", ctx.lang)),
            refreshUrl = "/components/navigation/page"
        )
    }
}
