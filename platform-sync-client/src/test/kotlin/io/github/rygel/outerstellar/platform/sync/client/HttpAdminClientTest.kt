package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.model.UserSummary
import kotlin.test.assertEquals
import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpAdminClientTest {

    private val session = ApiSession()

    private fun makeClient(handler: (Request) -> Response): HttpAdminClient =
        HttpAdminClient("http://localhost:8080", session, handler)

    private fun withAuth(handler: (Request) -> Response): HttpAdminClient {
        session.apiToken = "tok"
        return makeClient(handler)
    }

    @Test
    fun `listUsers returns user list`() {
        val client = withAuth { Response(Status.OK).with(Body.auto<List<UserSummary>>().toLens() of emptyList()) }
        val result = client.listUsers()
        assertEquals(0, result.size)
    }

    @Test
    fun `listUsers sends auth header`() {
        var authHeader: String? = null
        val client = withAuth { req ->
            authHeader = req.header("Authorization")
            Response(Status.OK).with(Body.auto<List<UserSummary>>().toLens() of emptyList())
        }
        client.listUsers()
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `listUsers throws on failure`() {
        val client = withAuth { Response(Status.INTERNAL_SERVER_ERROR) }
        assertThrows<SyncException> { client.listUsers() }
    }

    @Test
    fun `setUserEnabled sends PUT with correct body`() {
        var body: String? = null
        val client = withAuth { req ->
            body = req.bodyString()
            Response(Status.OK)
        }
        client.setUserEnabled("user-uuid", true)
        assertEquals("{\"enabled\":true}", body)
    }

    @Test
    fun `setUserEnabled throws on failure`() {
        val client = withAuth { Response(Status.BAD_REQUEST) }
        assertThrows<SyncException> { client.setUserEnabled("user-uuid", true) }
    }

    @Test
    fun `setUserRole sends PUT with role`() {
        var body: String? = null
        val client = withAuth { req ->
            body = req.bodyString()
            Response(Status.OK)
        }
        client.setUserRole("user-uuid", "ADMIN")
        assertEquals("{\"role\":\"ADMIN\"}", body)
    }

    @Test
    fun `setUserRole throws on failure`() {
        val client = withAuth { Response(Status.BAD_REQUEST) }
        assertThrows<SyncException> { client.setUserRole("user-uuid", "ADMIN") }
    }
}
