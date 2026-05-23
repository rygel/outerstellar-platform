package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.SyncException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpAuthClientTest {

    private val session = ApiSession()

    private fun makeClient(handler: (Request) -> Response): HttpAuthClient =
        HttpAuthClient("http://localhost:8080", session, handler)

    @Test
    fun `login stores token in session`() {
        val authResp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok123", "alice", "USER"))
        val client = makeClient { authResp }
        val result = client.login("alice", "password123")
        assertEquals("tok123", result.token)
        assertEquals("tok123", session.apiToken)
        assertEquals("USER", session.userRole)
    }

    @Test
    fun `login throws SyncException on failure`() {
        val client = makeClient { Response(Status.FORBIDDEN) }
        assertThrows<SyncException> { client.login("alice", "bad") }
    }

    @Test
    fun `register stores token in session`() {
        val authResp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok456", "bob", "USER"))
        val client = makeClient { authResp }
        val result = client.register("bob", "password123")
        assertEquals("tok456", result.token)
        assertEquals("tok456", session.apiToken)
        assertEquals("USER", session.userRole)
    }

    @Test
    fun `logout clears session`() {
        val authResp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok123", "alice", "USER"))
        val client = makeClient { authResp }
        client.login("alice", "pass")
        client.logout()
        assertNull(session.apiToken)
        assertNull(session.userRole)
    }

    @Test
    fun `changePassword sends authenticated request`() {
        val authResp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok", "alice", "USER"))
        var authHeader: String? = null
        val client = makeClient { req ->
            if (req.uri.toString().contains("auth/login")) {
                authResp
            } else {
                authHeader = req.header("Authorization")
                Response(Status.OK)
            }
        }
        client.login("alice", "pass")
        client.changePassword("old", "new")
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `changePassword throws on failure`() {
        val authResp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok", "alice", "USER"))
        val client = makeClient { req ->
            if (req.uri.toString().contains("auth/login")) authResp else Response(Status.BAD_REQUEST)
        }
        client.login("alice", "pass")
        assertThrows<SyncException> { client.changePassword("old", "new") }
    }

    @Test
    fun `requestPasswordReset does not require auth`() {
        var called = false
        val client = makeClient {
            called = true
            Response(Status.OK)
        }
        client.requestPasswordReset("alice@example.com")
        assert(called)
    }

    @Test
    fun `resetPassword does not require auth`() {
        var called = false
        val client = makeClient {
            called = true
            Response(Status.OK)
        }
        client.resetPassword("token123", "newPassword123")
        assert(called)
    }

    @Test
    fun `requestPasswordReset throws on failure`() {
        val client = makeClient { Response(Status.BAD_REQUEST) }
        assertThrows<SyncException> { client.requestPasswordReset("alice@example.com") }
    }

    @Test
    fun `resetPassword throws on failure`() {
        val client = makeClient { Response(Status.BAD_REQUEST) }
        assertThrows<SyncException> { client.resetPassword("token123", "newPass") }
    }
}
