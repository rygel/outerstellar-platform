package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.SessionLookup
import io.github.rygel.outerstellar.platform.model.ThemeCatalog
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.security.SessionService
import java.util.Locale
import org.http4k.core.Request
import org.http4k.core.cookie.cookie
import org.http4k.lens.RequestKey

class RequestContext(
    val request: Request,
    private val userRepository: UserRepository? = null,
    private val jwtService: JwtService? = null,
    private val sessionService: SessionService? = null,
) {
    companion object {
        val KEY = RequestKey.required<RequestContext>("request.context")

        const val SESSION_COOKIE = "app_session"
        const val JWT_COOKIE = "app_jwt"
        const val CSRF_COOKIE = "_csrf"
        const val LANG_COOKIE = "app_lang"
        const val THEME_COOKIE = "app_theme"
        const val LAYOUT_COOKIE = "app_layout"
        const val SHELL_COOKIE = "app_shell"

        val SUPPORTED_LANGUAGES = setOf("en", "fr")
        val SUPPORTED_LAYOUTS = listOf("nice", "cozy", "compact")
        val SUPPORTED_SHELLS = listOf("sidebar", "topbar")
    }

    private val sessionLookup: SessionLookup? by lazy {
        request.cookie(SESSION_COOKIE)?.value?.let { rawToken -> sessionService?.lookupSession(rawToken) }
    }

    val user: User? by lazy {
        val lookup = sessionLookup
        when (lookup) {
            is SessionLookup.Active -> lookup.user
            else ->
                request.cookie(JWT_COOKIE)?.value?.let { token ->
                    jwtService?.extractClaims(token)?.let { (userId, _) ->
                        userRepository?.findById(userId)?.takeIf { it.enabled }
                    }
                }
        }
    }

    val sessionExpired: Boolean by lazy { sessionLookup is SessionLookup.Expired }

    val csrfToken: String by lazy { request.cookie(CSRF_COOKIE)?.value ?: java.util.UUID.randomUUID().toString() }

    val cspNonce: String by lazy { Filters.CSP_NONCE_KEY(request).orEmpty() }

    val lang: String by lazy {
        request.query("lang")
            ?: request.cookie(LANG_COOKIE)?.value
            ?: user?.language
            ?: Locale.getDefault().language.lowercase().let { if (it == "fr") "fr" else "en" }
    }

    val theme: String by lazy {
        val value = request.query("theme") ?: request.cookie(THEME_COOKIE)?.value ?: user?.theme ?: "dark"
        if (ThemeCatalog.isValidTheme(value)) value else "dark"
    }

    val layout: String by lazy {
        val value = request.query("layout") ?: request.cookie(LAYOUT_COOKIE)?.value ?: user?.layout ?: "nice"
        if (value in SUPPORTED_LAYOUTS) value else "nice"
    }

    val shellStyle: String by lazy {
        val value = request.query("shell") ?: request.cookie(SHELL_COOKIE)?.value ?: "sidebar"
        if (value in SUPPORTED_SHELLS) value else "sidebar"
    }
}
