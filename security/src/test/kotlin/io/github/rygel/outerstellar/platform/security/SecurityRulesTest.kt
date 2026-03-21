package io.github.rygel.outerstellar.platform.security

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.RequestContexts
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters

class SecurityRulesTest {

    private val contexts = RequestContexts()

    private fun requestWithUser(user: User, method: Method = Method.GET, path: String = "/test"): Request {
        var contextualRequest: Request? = null
        ServerFilters.InitialiseRequestContext(contexts).then { req ->
            contextualRequest = req
            Response(Status.OK)
        }(Request(method, path))
        return contextualRequest!!.with(SecurityRules.USER_KEY of user)
    }

    private fun plainRequest(method: Method = Method.GET, path: String = "/test"): Request {
        var contextualRequest: Request? = null
        ServerFilters.InitialiseRequestContext(contexts).then { req ->
            contextualRequest = req
            Response(Status.OK)
        }(Request(method, path))
        return contextualRequest!!
    }

    private val testUser =
        User(
            id = UUID.randomUUID(),
            username = "alice",
            email = "alice@example.com",
            passwordHash = "hash",
            role = UserRole.USER,
        )

    private val adminUser =
        User(
            id = UUID.randomUUID(),
            username = "admin",
            email = "admin@example.com",
            passwordHash = "hash",
            role = UserRole.ADMIN,
        )

    // ---- authenticated ----

    @Test
    fun `authenticated allows request when user is present`() {
        val request = requestWithUser(testUser)
        val handler = SecurityRules.authenticated { Response(Status.OK).body("welcome") }

        val response = handler(request)

        assertEquals(Status.OK, response.status)
        assertEquals("welcome", response.bodyString())
    }

    @Test
    fun `authenticated redirects to auth when no user`() {
        val request = plainRequest()
        val handler = SecurityRules.authenticated { Response(Status.OK) }

        val response = handler(request)

        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location")!!.startsWith("/auth?returnTo="))
    }

    @Test
    fun `authenticated redirect includes returnTo parameter`() {
        val request = plainRequest(path = "/dashboard")
        val handler = SecurityRules.authenticated { Response(Status.OK) }

        val response = handler(request)

        val location = response.header("location")!!
        assertTrue(location.contains("returnTo="), "location should contain returnTo: $location")
        assertTrue(location.contains("dashboard"), "location should contain dashboard: $location")
    }

    // ---- hasRole ----

    @Test
    fun `hasRole allows request when user has correct role`() {
        val request = requestWithUser(adminUser)
        val handler = SecurityRules.hasRole(UserRole.ADMIN) { Response(Status.OK).body("admin area") }

        val response = handler(request)

        assertEquals(Status.OK, response.status)
        assertEquals("admin area", response.bodyString())
    }

    @Test
    fun `hasRole returns 403 when user has wrong role`() {
        val request = requestWithUser(testUser)
        val handler = SecurityRules.hasRole(UserRole.ADMIN) { Response(Status.OK) }

        val response = handler(request)

        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `hasRole returns 403 when no user present`() {
        val request = plainRequest()
        val handler = SecurityRules.hasRole(UserRole.ADMIN) { Response(Status.OK) }

        val response = handler(request)

        assertEquals(Status.FORBIDDEN, response.status)
    }
}
