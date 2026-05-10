package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
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

class WebSocketSyncIntegrationTest : WebTest() {

    private lateinit var testUser: User
    private lateinit var testToken: String
    private lateinit var syncWebSocket: SyncWebSocket
    private lateinit var securityService: SecurityService

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
        securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )
        testToken = securityService.createSession(testUser.id)
        syncWebSocket = SyncWebSocket(securityService)
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
    fun `invalid session cookie is rejected with 4401`() {
        val request = Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=not-a-valid-token")
        val wsResponse = syncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify { mockWs.close(WsStatus(4401, "Authentication required")) }
    }

    @Test
    fun `unknown token in session cookie is rejected with 4401`() {
        val request = Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=oss_${"z".repeat(48)}")
        val wsResponse = syncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)

        verify { mockWs.close(WsStatus(4401, "Authentication required")) }
    }

    @Test
    fun `valid session cookie accepts connection and registers handlers`() {
        val request = Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=$testToken")
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
        val request = Request(GET, "/ws/sync").header("Cookie", "${WebContext.SESSION_COOKIE}=$testToken")
        val wsResponse = syncWebSocket.handler(request)
        val mockWs = mockk<Websocket>(relaxed = true)

        wsResponse(mockWs)
        syncWebSocket.publishRefresh("messages")

        verify { mockWs.send(WsMessage("refresh:messages")) }
    }
}
