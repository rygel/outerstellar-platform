package io.github.rygel.outerstellar.platform.security

/** Result of an [AuthRealm] authentication attempt. */
sealed class AuthResult {
    /** Token was recognised and the user is authenticated. */
    data class Authenticated(val user: User) : AuthResult()

    /** Token was recognised but has expired (e.g. session timed out). */
    data object Expired : AuthResult()

    /** This realm does not recognise the token — try the next realm. */
    data object Skipped : AuthResult()
}

/**
 * An authentication realm that can resolve a bearer token to a [User].
 *
 * Multiple realms can be chained together — the first one to return [AuthResult.Authenticated] or [AuthResult.Expired]
 * wins. [AuthResult.Skipped] causes the next realm in the chain to be tried.
 *
 * This allows composing session-based auth, API key auth, LDAP, external OAuth token validation, or any custom source
 * without modifying the framework's filter chain.
 */
interface AuthRealm {
    /** Human-readable name for logging and debugging. */
    val name: String

    /**
     * Try to authenticate the given [token].
     *
     * @return [AuthResult.Authenticated] on success, [AuthResult.Expired] if the token is recognised but expired, or
     *   [AuthResult.Skipped] if this realm does not handle the token.
     */
    fun authenticate(token: String): AuthResult
}

/** Authenticates session tokens (prefixed `oss_`) via the [SessionRepository]. */
class SessionRealm(private val securityService: SecurityService) : AuthRealm {
    override val name = "session"

    override fun authenticate(token: String): AuthResult =
        when (val result = securityService.lookupSession(token)) {
            is SessionLookup.Active -> AuthResult.Authenticated(result.user)
            is SessionLookup.Expired -> AuthResult.Expired
            is SessionLookup.NotFound -> AuthResult.Skipped
        }
}

/** Authenticates API key tokens (prefixed `osk_`) via the [ApiKeyRepository]. */
class ApiKeyRealm(private val securityService: SecurityService) : AuthRealm {
    override val name = "api-key"

    override fun authenticate(token: String): AuthResult {
        val user = securityService.authenticateApiKey(token)
        return if (user != null) AuthResult.Authenticated(user) else AuthResult.Skipped
    }
}
