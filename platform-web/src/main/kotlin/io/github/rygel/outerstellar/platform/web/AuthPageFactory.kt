package io.github.rygel.outerstellar.platform.web

class AuthPageFactory {

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
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
                toneClass = "bg-success/10 border-success/30 text-success",
            )
        } else {
            AuthResultFragment(
                title = i18n.translate("web.auth.result.error.title"),
                message = errors.joinToString(" "),
                toneClass = "bg-error/10 border-error/30 text-error",
            )
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
}
