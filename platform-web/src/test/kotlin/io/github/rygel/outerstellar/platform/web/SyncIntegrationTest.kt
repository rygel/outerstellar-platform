package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson.asA
import org.junit.jupiter.api.AfterEach

class SyncIntegrationTest : H2WebTest() {

    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `can pull changes from api`() {
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )

        // Pre-register an admin user for Bearer Auth
        val adminId = UUID.randomUUID()
        userRepository.save(
            User(
                id = adminId,
                username = "admin",
                email = "admin@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.ADMIN,
            )
        )
        val adminToken = securityService.createSession(adminId)

        val app = buildApp(securityService = securityService)

        // Add some data
        messageRepository.createServerMessage("Alice", "Hello")
        messageRepository.createServerMessage("Bob", "Hi")

        // Pull changes with Bearer Auth
        val response = app(Request(GET, "/api/v1/sync?since=0").header("Authorization", "Bearer $adminToken"))

        assertEquals(Status.OK, response.status)
        val pullResponse = asA(response.bodyString(), SyncPullResponse::class)
        assertEquals(2, pullResponse.messages.size)
    }
}
