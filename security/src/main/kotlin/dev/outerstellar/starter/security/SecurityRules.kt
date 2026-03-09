package dev.outerstellar.starter.security

import org.http4k.contract.security.Security
import org.http4k.core.Filter
import org.http4k.core.RequestContexts
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.RequestContextKey

object SecurityRules {
    private val contexts = RequestContexts()
    val userKey = RequestContextKey.optional<User>(contexts)

    fun hasRole(role: Role): Filter = Filter { next ->
        { request ->
            val user = userKey(request)
            if (user != null && user.roles.contains(role)) {
                next(request)
            } else {
                Response(Status.FORBIDDEN)
            }
        }
    }

    val noSecurity: Security = Security.None!!
}
