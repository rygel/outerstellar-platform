package dev.outerstellar.starter.security

import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.RequestKey

object SecurityRules {
    val USER_KEY = RequestKey.optional<User>("security.user")

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
