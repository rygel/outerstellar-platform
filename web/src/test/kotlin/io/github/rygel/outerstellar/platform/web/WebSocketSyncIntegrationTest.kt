package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for the SyncWebSocket handler (WebSocket authentication and messaging).
 *
 * Covers:
 * - Unauthenticated WebSocket connection is rejected with WsStatus 4401
 * - Invalid (non-UUID) session cookie is rejected with 4401
 * - Unknown UUID in session cookie is rejected with 4401
 * - Authenticated connection is accepted and handlers registered
 * - publishRefresh broadcasts to authenticated connections
 */
class WebSocketSyncIntegrationTest : H2WebTest() {

    private lateinit var userRepository: JooqUserRepository
    private lateinit var testUser: User

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        userRepository = JooqUserRepository(testDsl)
        testUser =
            User(
                id = UUID.randomUUID(),
                username = "wsuser",
                email = "ws@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        SyncWebSocket.userRepository = userRepository
    }

    @AfterEach
    fun teardown() {
        SyncWebSocket.userRepository = null
        cleanup()
    }

    @Test
    fun `unauthenticated WebSocket connection is closed with 4401`() {
        val wsResponse = SyncWebSocket.handler(Request(GET, "/ws/sync"))
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify { mockWs.close(WsStatus(4401, "Authentication required")) }
    }

    @Test
    fun `invalid non-UUID session cookie is rejected with 4401`() {
        val request =
            Request(GET, "/ws/sync")
                .header("Cookie", "${WebContext.SESSION_COOKIE}=not-a-uuid-at-all")
        val wsResponse = SyncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify { mockWs.close(WsStatus(4401, "Authentication required")) }
    }

    @Test
    fun `unknown UUID in session cookie is rejected with 4401`() {
        val request =
            Request(GET, "/ws/sync")
                .header("Cookie", "${WebContext.SESSION_COOKIE}=${UUID.randomUUID()}")
        val wsResponse = SyncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify { mockWs.close(WsStatus(4401, "Authentication required")) }
    }

    @Test
    fun `valid session cookie accepts connection and registers handlers`() {
        val request =
            Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=${testUser.id}")
        val wsResponse = SyncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        // Connection should NOT be closed
        verify(exactly = 0) { mockWs.close(any()) }
        // Event handlers should be registered
        verify { mockWs.onMessage(any()) }
        verify { mockWs.onClose(any()) }
        verify { mockWs.onError(any()) }
    }

    @Test
    fun `publishRefresh broadcasts to authenticated connection`() {
        val request =
            Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=${testUser.id}")
        val wsResponse = SyncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)
        SyncWebSocket.publishRefresh("messages")

        verify { mockWs.send(WsMessage("refresh:messages")) }
    }
}
