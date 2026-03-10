package dev.outerstellar.starter.security

import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

object SecurityRules {
    fun authenticated(next: HttpHandler): HttpHandler = { request ->
        // To be implemented with real session check
        next(request)
    }

    fun hasRole(role: UserRole, next: HttpHandler): HttpHandler = { request ->
        // Real implementation would extract user from context
        val user: User? = null 
        if (user?.role == role) {
            next(request)
        } else {
            Response(Status.FORBIDDEN)
        }
    }
}
