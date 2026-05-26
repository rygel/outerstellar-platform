package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.sync.SyncMessage
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import kotlin.test.assertEquals
import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpSyncClientTest {

    private val session = ApiSession()

    private fun makeClient(handler: (Request) -> Response): HttpSyncClient =
        HttpSyncClient("http://localhost:8080", session, handler)

    private fun withAuth(handler: (Request) -> Response): HttpSyncClient {
        session.apiToken = "tok"
        return makeClient(handler)
    }

    @Test
    fun `pull returns SyncPullResponse on success`() {
        val pullResp =
            Response(Status.OK)
                .with(
                    Body.auto<SyncPullResponse>().toLens() of
                        SyncPullResponse(listOf(SyncMessage("id1", "alice", "hello", 100L)), 100L, false)
                )
        val client = withAuth { pullResp }
        val result = client.pull(0L)
        assertEquals(1, result.messages.size)
        assertEquals("id1", result.messages[0].syncId)
    }

    @Test
    fun `pull sends auth header`() {
        var authHeader: String? = null
        val pullResp = Response(Status.OK).with(Body.auto<SyncPullResponse>().toLens() of SyncPullResponse())
        val client = withAuth { req ->
            authHeader = req.header("Authorization")
            pullResp
        }
        client.pull(0L)
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `pull throws on failure`() {
        val client = withAuth { Response(Status.INTERNAL_SERVER_ERROR) }
        assertThrows<SyncException> { client.pull(0L) }
    }

    @Test
    fun `push returns SyncPushResponse on success`() {
        val pushResp =
            Response(Status.OK).with(Body.auto<SyncPushResponse>().toLens() of SyncPushResponse(3, emptyList()))
        val client = withAuth { pushResp }
        val result = client.push(SyncPushRequest(emptyList()))
        assertEquals(3, result.appliedCount)
    }

    @Test
    fun `push sends auth header`() {
        var authHeader: String? = null
        val pushResp = Response(Status.OK).with(Body.auto<SyncPushResponse>().toLens() of SyncPushResponse())
        val client = withAuth { req ->
            authHeader = req.header("Authorization")
            pushResp
        }
        client.push(SyncPushRequest(emptyList()))
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `push throws on failure`() {
        val client = withAuth { Response(Status.INTERNAL_SERVER_ERROR) }
        assertThrows<SyncException> { client.push(SyncPushRequest(emptyList())) }
    }
}
