package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.security.SecurityService
import org.http4k.core.Request
import org.http4k.lens.RequestKey

class WebContext internal constructor(val requestContext: RequestContext, val shellRenderer: ShellRenderer) {
    companion object {
        val KEY = RequestKey.required<WebContext>("web.context")

        const val LANG_COOKIE = RequestContext.LANG_COOKIE
        const val THEME_COOKIE = RequestContext.THEME_COOKIE
        const val LAYOUT_COOKIE = RequestContext.LAYOUT_COOKIE
        const val SHELL_COOKIE = RequestContext.SHELL_COOKIE
        const val SESSION_COOKIE = RequestContext.SESSION_COOKIE
        const val JWT_COOKIE = RequestContext.JWT_COOKIE
        const val CSRF_COOKIE = RequestContext.CSRF_COOKIE

        val SUPPORTED_LANGUAGES = RequestContext.SUPPORTED_LANGUAGES
        val SUPPORTED_LAYOUTS = RequestContext.SUPPORTED_LAYOUTS
        val SUPPORTED_SHELLS = RequestContext.SUPPORTED_SHELLS

        fun create(
            request: Request,
            devDashboardEnabled: Boolean = false,
            userRepository: UserRepository? = null,
            appVersion: String = "dev",
            jwtService: JwtService? = null,
            securityService: SecurityService? = null,
            shellConfig: ShellConfig = ShellConfig(),
        ): WebContext {
            val ctx = RequestContext(request, userRepository, jwtService, securityService)
            return WebContext(ctx, ShellRenderer(ctx, devDashboardEnabled, appVersion, shellConfig))
        }
    }

    val request: Request
        get() = requestContext.request

    val user by requestContext::user
    val sessionExpired by requestContext::sessionExpired
    val csrfToken by requestContext::csrfToken
    val lang by requestContext::lang
    val theme by requestContext::theme
    val layout by requestContext::layout
    val shellStyle by requestContext::shellStyle

    val i18n: I18nService by shellRenderer::i18n
    val textResolver: TextResolver by shellRenderer::textResolver

    fun url(path: String): String = shellRenderer.url(path)

    fun componentUrl(path: String, pagePath: String): String = shellRenderer.componentUrl(path, pagePath)

    fun shell(pageTitle: String, activeSection: String): ShellView = shellRenderer.shell(pageTitle, activeSection)
}
