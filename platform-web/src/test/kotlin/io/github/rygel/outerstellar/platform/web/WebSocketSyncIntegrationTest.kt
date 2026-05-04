package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.mockk.mockk
import io.mockk.verify
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test

class WebSocketSyncIntegrationTest : H2WebTest() {

    private lateinit var testUser: User
    private lateinit var syncWebSocket: SyncWebSocket

    @BeforeEach
    fun setupTest() {
        testUser =
            User(
                id = UUID.randomUUID(),
                username = "wsuser",
                email = "ws@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        syncWebSocket = SyncWebSocket(userRepository)
    }

    @AfterEach fun teardown() = cleanup()

    @Test
    fun `unauthenticated WebSocket connection is closed with 4401`() {
        val wsResponse = syncWebSocket.handler(Request(GET, "/ws/sync"))
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify { mockWs.close(WsStatus(4401, "Authentication required")) }
    }

    @Test
    fun `invalid non-UUID session cookie is rejected with 4401`() {
        val request = Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=not-a-uuid-at-all")
        val wsResponse = syncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify { mockWs.close(WsStatus(4401, "Authentication required")) }
    }

    @Test
    fun `unknown UUID in session cookie is rejected with 4401`() {
        val request = Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=${UUID.randomUUID()}")
        val wsResponse = syncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify { mockWs.close(WsStatus(4401, "Authentication required")) }
    }

    @Test
    fun `valid session cookie accepts connection and registers handlers`() {
        val request = Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=${testUser.id}")
        val wsResponse = syncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify(exactly = 0) { mockWs.close(any()) }
        verify { mockWs.onMessage(any()) }
        verify { mockWs.onClose(any()) }
        verify { mockWs.onError(any()) }
    }

    @Test
    fun `publishRefresh broadcasts to authenticated connection`() {
        val request = Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=${testUser.id}")
        val wsResponse = syncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)
        syncWebSocket.publishRefresh("messages")

        verify { mockWs.send(WsMessage("refresh:messages")) }
    }
}
