package dev.outerstellar.starter.security

import org.http4k.core.HttpHandler
import org.http4k.core.RequestContexts
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.RequestContextKey

object SecurityRules {
    val contexts = RequestContexts()
    val USER_KEY = RequestContextKey.optional<User>(contexts)

    fun authenticated(next: HttpHandler): HttpHandler = { request ->
        val user = USER_KEY(request)
        if (user != null) {
            next(request)
        } else {
            val returnTo = request.uri.toString()
            Response(Status.FOUND).header("location", "/auth?returnTo=$returnTo")
        }
    }

    fun hasRole(role: UserRole, next: HttpHandler): HttpHandler = { request ->
        val user = USER_KEY(request)
        if (user?.role == role) {
            next(request)
        } else {
            Response(Status.FORBIDDEN)
        }
    }
}
