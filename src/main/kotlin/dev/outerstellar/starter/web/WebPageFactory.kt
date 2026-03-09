package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.MessageSummary
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
)

data class HomeFeature(val label: String, val value: String)

data class HomePage(
  val shell: ShellView,
  val eyebrow: String,
  val intro: String,
  val features: List<HomeFeature>,
  val composerTitle: String,
  val composerIntro: String,
  val authorPlaceholder: String,
  val contentPlaceholder: String,
  val submitLabel: String,
  val submitUrl: String,
  val messagesHeading: String,
  val messages: List<MessageSummary>,
) : ViewModel

data class AuthModeTab(val key: String, val label: String, val url: String)

data class AuthPage(
  val shell: ShellView,
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
  val shell: ShellView,
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
class WebPageFactory(private val repository: MessageRepository) {
  fun buildHomePage(request: Request): HomePage {
    val i18n = i18n(request)
    val shell = shell(request, i18n.translate("web.nav.home"), "/")

    return HomePage(
      shell = shell,
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
      submitUrl = url("/messages", shell.localeTag, shell.themeId, layoutId(request)),
      messagesHeading = i18n.translate("web.home.messages"),
      messages = repository.listMessages(),
    )
  }

  fun buildAuthPage(request: Request): AuthPage {
    val i18n = i18n(request)
    val shell = shell(request, i18n.translate("web.nav.auth"), "/auth")
    val lang = shell.localeTag
    val theme = shell.themeId
    val layout = layoutId(request)

    return AuthPage(
      shell = shell,
      heading = i18n.translate("web.auth.heading"),
      intro = i18n.translate("web.auth.intro"),
      helperText = i18n.translate("web.auth.helper"),
      tabs =
        listOf(
          AuthModeTab(
            "sign-in",
            i18n.translate("web.auth.signin"),
            url("/auth/components/forms/sign-in", lang, theme, layout),
          ),
          AuthModeTab(
            "register",
            i18n.translate("web.auth.register"),
            url("/auth/components/forms/register", lang, theme, layout),
          ),
          AuthModeTab(
            "recover",
            i18n.translate("web.auth.recover"),
            url("/auth/components/forms/recover", lang, theme, layout),
          ),
        ),
      defaultFormUrl = url("/auth/components/forms/sign-in", lang, theme, layout),
    )
  }

  fun buildAuthForm(request: Request, mode: String): AuthFormFragment {
    val i18n = i18n(request)
    val lang = langTag(request)
    val theme = themeId(request)
    val layout = layoutId(request)
    val normalizedMode =
      when (mode) {
        "register",
        "recover" -> mode
        else -> "sign-in"
      }

    return AuthFormFragment(
      mode = normalizedMode,
      title = i18n.translate("web.auth.$normalizedMode.title"),
      description = i18n.translate("web.auth.$normalizedMode.description"),
      submitUrl = url("/auth/components/result", lang, theme, layout),
      submitLabel = i18n.translate("web.auth.$normalizedMode.submit"),
      language = lang,
      theme = theme,
      layout = layout,
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
    )
  }

  fun buildAuthResult(request: Request, formValues: Map<String, String?>): AuthResultFragment {
    val i18n = i18n(request)
    val mode = formValues["mode"] ?: "sign-in"
    val email = formValues["email"].orEmpty()
    val password = formValues["password"].orEmpty()
    val confirmPassword = formValues["confirmPassword"].orEmpty()
    val errors = mutableListOf<String>()

    if (email.isBlank()) {
      errors += i18n.translate("web.auth.error.email")
    }
    if (mode != "recover" && password.length < minimumPasswordLength) {
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

  fun buildErrorPage(request: Request, kind: String): ErrorPage {
    val i18n = i18n(request)
    val shell = shell(request, i18n.translate("web.nav.errors"), "/errors")
    val layout = layoutId(request)
    val normalizedKind = if (kind == "server-error") "server-error" else "not-found"
    val statusCode =
      if (normalizedKind == "server-error") serverErrorStatusCode else notFoundStatusCode

    return ErrorPage(
      shell = shell,
      statusCode = statusCode,
      heading = i18n.translate("web.error.$normalizedKind.title"),
      message = i18n.translate("web.error.$normalizedKind.message"),
      primaryActionLabel = i18n.translate("web.error.primary"),
      primaryActionUrl = url("/", shell.localeTag, shell.themeId, layout),
      secondaryActionLabel = i18n.translate("web.error.secondary"),
      secondaryActionUrl = url("/auth", shell.localeTag, shell.themeId, layout),
      helpButtonLabel = i18n.translate("web.error.help"),
      helpUrl = url("/errors/components/help/$normalizedKind", shell.localeTag, shell.themeId, layout),
    )
  }

  fun buildErrorHelp(request: Request, kind: String): ErrorHelpFragment {
    val i18n = i18n(request)
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

  fun buildFooterStatus(request: Request): FooterStatusFragment {
    val i18n = i18n(request)
    return FooterStatusFragment(
      text =
        i18n.translate(
          "web.footer.status",
          repository.listMessages().size,
          repository.listDirtyMessages().size,
        )
    )
  }

  fun buildThemeSelector(request: Request): SidebarSelector {
    val i18n = i18n(request)
    val pagePath = normalizePagePath(request.query("pagePath").orEmpty())
    val lang = langTag(request)
    val theme = themeId(request)
    val layout = layoutId(request)

    return SidebarSelector(
      heading = i18n.translate("web.sidebar.themes"),
      label = i18n.translate("web.sidebar.theme.label"),
      selectId = "theme-selector",
      selectName = "theme",
      options =
        ThemeCatalog.allThemes().map { definition ->
          ShellOption(definition.id, definition.name, definition.id, definition.id == theme)
        },
      hiddenFields = listOf(HiddenField("pagePath", pagePath), HiddenField("lang", lang), HiddenField("layout", layout)),
      refreshUrl = "/components/navigation/page",
    )
  }

  fun buildLanguageSelector(request: Request): SidebarSelector {
    val i18n = i18n(request)
    val pagePath = normalizePagePath(request.query("pagePath").orEmpty())
    val theme = themeId(request)
    val lang = langTag(request)
    val layout = layoutId(request)

    return SidebarSelector(
      heading = i18n.translate("web.sidebar.language"),
      label = i18n.translate("web.sidebar.language.label"),
      selectId = "language-selector",
      selectName = "lang",
      options =
        supportedLanguages().map { (localeId, labelKey) ->
          ShellOption(localeId, i18n.translate(labelKey), localeId, localeId == lang)
        },
      hiddenFields = listOf(HiddenField("pagePath", pagePath), HiddenField("theme", theme), HiddenField("layout", layout)),
      refreshUrl = "/components/navigation/page",
    )
  }

  fun buildLayoutSelector(request: Request): SidebarSelector {
    val i18n = i18n(request)
    val pagePath = normalizePagePath(request.query("pagePath").orEmpty())
    val theme = themeId(request)
    val lang = langTag(request)
    val layout = layoutId(request)

    return SidebarSelector(
      heading = i18n.translate("web.sidebar.layout"),
      label = i18n.translate("web.sidebar.layout.label"),
      selectId = "layout-selector",
      selectName = "layout",
      options =
        listOf(
          ShellOption("nice", i18n.translate("web.layout.nice"), "nice", layout == "nice"),
          ShellOption("cozy", i18n.translate("web.layout.cozy"), "cozy", layout == "cozy"),
          ShellOption("compact", i18n.translate("web.layout.compact"), "compact", layout == "compact")
        ),
      hiddenFields = listOf(HiddenField("pagePath", pagePath), HiddenField("theme", theme), HiddenField("lang", lang)),
      refreshUrl = "/components/navigation/page",
    )
  }

  fun langTag(request: Request): String {
    val language = request.query("lang")?.lowercase() ?: Locale.getDefault().language.lowercase()
    return if (language == "fr") "fr" else "en"
  }

  fun themeId(request: Request): String {
    val theme = request.query("theme")?.lowercase() ?: "dark"
    return if (ThemeCatalog.allThemes().any { it.id == theme }) theme else "dark"
  }

  fun layoutId(request: Request): String {
    val layout = request.query("layout")?.lowercase() ?: "nice"
    return if (listOf("nice", "cozy", "compact").any { it == layout }) layout else "nice"
  }


    val theme = themeId(request)
    val layout = layoutId(request)
    val currentPath = normalizePagePath(request.uri.path)
    val themeCss = ThemeCatalog.toCssVariables(theme)
    val layoutClass = if (layout == "nice") "" else "layout-$layout"

    return ShellView(
      pageTitle = pageTitle,
      appTitle = i18n.translate("web.app.title"),
      appTagline = i18n.translate("web.app.tagline"),
      currentPath = currentPath,
      localeTag = lang,
      themeId = theme,
      themeCss = themeCss,
      layoutClass = layoutClass,
      navLinks =
        listOf(
          ShellLink(i18n.translate("web.nav.home"), url("/", lang, theme, layout), "ri-home-5-line", activeSection == "/"),
          ShellLink(
            i18n.translate("web.nav.auth"),
            url("/auth", lang, theme, layout),
            "ri-shield-keyhole-line",
            activeSection == "/auth",
          ),
          ShellLink(
            i18n.translate("web.nav.errors"),
            url("/errors/not-found", lang, theme, layout),
            "ri-error-warning-line",
            activeSection == "/errors",
          ),
        ),
      themeSelectorUrl =
        componentUrl("/components/sidebar/theme-selector", currentPath, lang, theme, layout),
      languageSelectorUrl =
        componentUrl("/components/sidebar/language-selector", currentPath, lang, theme, layout),
      layoutSelectorUrl =
        componentUrl("/components/sidebar/layout-selector", currentPath, lang, theme, layout),
      footerCopy = i18n.translate("web.footer.copy"),
      footerStatusUrl = url("/components/footer-status", lang, theme, layout),
    )
  }

  private fun i18n(request: Request): I18nService =
    I18nService.create("web-messages").also {
      it.setLocale(Locale.forLanguageTag(langTag(request)))
    }

  private fun supportedLanguages(): List<Pair<String, String>> =
    listOf("en" to "web.language.english", "fr" to "web.language.french")
}
