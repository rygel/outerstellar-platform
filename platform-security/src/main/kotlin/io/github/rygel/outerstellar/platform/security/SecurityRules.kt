package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.UserRole
import java.net.URLEncoder
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
            val returnTo = URLEncoder.encode(request.uri.toString(), "UTF-8")
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

    /**
     * Requires the authenticated user to hold a [Permission] that implies [required].
     *
     * Uses the supplied [resolver] to look up the user's permission set. Returns 403 if the user lacks the permission,
     * or redirects to login if not authenticated.
     */
    fun hasPermission(required: Permission, resolver: PermissionResolver, next: HttpHandler): HttpHandler = { request ->
        val user = USER_KEY(request)
        if (user != null && resolver.permissionsFor(user).any { it.implies(required) }) {
            next(request)
        } else if (user == null) {
            val returnTo = URLEncoder.encode(request.uri.toString(), "UTF-8")
            Response(Status.FOUND).header("location", "/auth?returnTo=$returnTo")
        } else {
            Response(Status.FORBIDDEN)
        }
    }
}
