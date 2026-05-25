package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import kotlin.test.assertEquals
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpProfileClientTest {

    private val session = ApiSession()

    private fun makeClient(handler: (Request) -> Response): HttpProfileClient =
        HttpProfileClient("http://localhost:8080", session, handler)

    private fun withAuth(handler: (Request) -> Response): HttpProfileClient {
        session.apiToken = "tok"
        return makeClient(handler)
    }

    @Test
    fun `fetchProfile returns UserProfileResponse`() {
        val profile = UserProfileResponse("alice", "alice@example.com", null, true, true)
        val client = withAuth { Response(Status.OK).with(Body.auto<UserProfileResponse>().toLens() of profile) }
        val result = client.fetchProfile()
        assertEquals("alice", result.username)
        assertEquals("alice@example.com", result.email)
    }

    @Test
    fun `fetchProfile throws on failure`() {
        val client = withAuth { Response(Status.INTERNAL_SERVER_ERROR) }
        assertThrows<SyncException> { client.fetchProfile() }
    }

    @Test
    fun `updateProfile sends PUT with email`() {
        var method: Method? = null
        var body: String? = null
        val client = withAuth { req ->
            method = req.method
            body = req.bodyString()
            Response(Status.OK)
        }
        client.updateProfile("alice@example.com", "aliceNew", null)
        assertEquals(Method.PUT, method)
        assert(body?.contains("\"email\":\"alice@example.com\"") == true)
    }

    @Test
    fun `updateProfile throws on failure`() {
        val client = withAuth { Response(Status.BAD_REQUEST) }
        assertThrows<SyncException> { client.updateProfile("alice@example.com", null, null) }
    }

    @Test
    fun `updateNotificationPreferences sends correct JSON`() {
        var body: String? = null
        val client = withAuth { req ->
            body = req.bodyString()
            Response(Status.OK)
        }
        client.updateNotificationPreferences(false, true)
        assertEquals("{\"emailEnabled\":false,\"pushEnabled\":true}", body)
    }

    @Test
    fun `updateNotificationPreferences throws on failure`() {
        val client = withAuth { Response(Status.INTERNAL_SERVER_ERROR) }
        assertThrows<SyncException> { client.updateNotificationPreferences(true, true) }
    }

    @Test
    fun `deleteAccount sends DELETE with auth`() {
        var method: Method? = null
        var body: String? = null
        var authHeader: String? = null
        val client = withAuth { req ->
            method = req.method
            body = req.bodyString()
            authHeader = req.header("Authorization")
            Response(Status.OK)
        }
        client.deleteAccount("pass")
        assertEquals(Method.DELETE, method)
        assertEquals("{\"currentPassword\":\"pass\"}", body)
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `deleteAccount throws on failure`() {
        val client = withAuth { Response(Status.BAD_REQUEST) }
        assertThrows<SyncException> { client.deleteAccount("pass") }
    }
}
