package io.github.rygel.outerstellar.platform.web.assembly

import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.AuthRealm
import io.github.rygel.outerstellar.platform.security.AuthResult
import io.github.rygel.outerstellar.platform.security.SecurityRules
import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.security.Security

internal class SecurityConfigurator(private val realms: List<AuthRealm>) {
    fun bearerSecurityPair(): Pair<Security, Security> {
        val bearerAuthFilter = Filter { next ->
            { req ->
                val token = req.header("Authorization")?.removePrefix("Bearer ")
                if (token == null) {
                    Response(Status.UNAUTHORIZED).body("API token required")
                } else {
                    var finalResult: AuthResult = AuthResult.Skipped
                    for (realm in realms) {
                        val result = realm.authenticate(token)
                        if (result !is AuthResult.Skipped) {
                            finalResult = result
                            break
                        }
                    }
                    when (finalResult) {
                        is AuthResult.Authenticated -> next(req.with(SecurityRules.USER_KEY of finalResult.user))
                        is AuthResult.Expired ->
                            Response(Status.UNAUTHORIZED).header("X-Session-Expired", "true").body("Session expired")
                        is AuthResult.Skipped,
                        is AuthResult.TotpRequired -> Response(Status.UNAUTHORIZED).body("API token required")
                    }
                }
            }
        }
        val bearerSecurity =
            object : Security {
                override val filter = bearerAuthFilter
            }
        val bearerAdminSecurity =
            object : Security {
                override val filter = Filter { next ->
                    bearerAuthFilter.then(Filter { inner -> SecurityRules.hasRole(UserRole.ADMIN, inner) })(next)
                }
            }
        return bearerSecurity to bearerAdminSecurity
    }
}
