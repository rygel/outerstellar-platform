package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRole
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.MessageService
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for device push-notification token registration API.
 *
 * Covers:
 * - POST /api/v1/devices/register with android platform and valid token returns 204
 * - POST /api/v1/devices/register with ios platform and valid token returns 204
 * - POST /api/v1/devices/register with unsupported platform returns 400
 * - POST /api/v1/devices/register with blank token returns 400
 * - POST /api/v1/devices/register without auth returns 401
 * - DELETE /api/v1/devices/register with valid token returns 204
 * - POST (upsert) with same token updates the entry without error
 * - Token is stored after successful registration
 * - Token is removed after successful deregistration
 */
class DeviceRegistrationApiIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var deviceTokenRepository: InMemoryDeviceTokenRepository

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, encoder)
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)
        deviceTokenRepository = InMemoryDeviceTokenRepository()

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "deviceuser",
                email = "device@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)

        app =
            app(
                    messageService,
                    contactService,
                    outbox,
                    cache,
                    createRenderer(),
                    pageFactory,
                    testConfig,
                    securityService,
                    userRepository,
                    deviceTokenRepository,
                )
                .http!!
    }

    @AfterEach
    fun teardown() {
        deviceTokenRepository.clear()
        cleanup()
    }

    private fun bearer() = "Bearer ${testUser.id}"

    private fun registerRequest(
        platform: String,
        token: String,
        appBundle: String? = null,
    ): org.http4k.core.Response {
        val bundleField = if (appBundle != null) ""","appBundle":"$appBundle"""" else ""
        return app(
            Request(POST, "/api/v1/devices/register")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body("""{"platform":"$platform","token":"$token"$bundleField}""")
        )
    }

    private fun deregisterRequest(token: String): org.http4k.core.Response =
        app(
            Request(DELETE, "/api/v1/devices/register")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body("""{"token":"$token"}""")
        )

    @Test
    fun `POST register with android platform returns 204`() {
        val response = registerRequest("android", "fcm-token-abc123")
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `POST register with ios platform returns 204`() {
        val response = registerRequest("ios", "apns-token-xyz789")
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `POST register with unsupported platform returns 400`() {
        val response = registerRequest("windows", "some-token")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `POST register with blank token returns 400`() {
        val response = registerRequest("android", "")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `POST register without auth returns 401`() {
        val response =
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("content-type", "application/json")
                    .body("""{"platform":"android","token":"some-token"}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `DELETE deregister with valid token returns 204`() {
        val token = "fcm-delete-test-token"
        registerRequest("android", token)

        val response = deregisterRequest(token)
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE deregister without auth returns 401`() {
        val response =
            app(
                Request(DELETE, "/api/v1/devices/register")
                    .header("content-type", "application/json")
                    .body("""{"token":"some-token"}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `token is stored after successful registration`() {
        val token = "stored-token-${UUID.randomUUID()}"
        registerRequest("android", token)

        val tokens = deviceTokenRepository.all()
        assertTrue(tokens.any { it.token == token }, "Token should be stored after registration")
    }

    @Test
    fun `token is removed after deregistration`() {
        val token = "removable-token-${UUID.randomUUID()}"
        registerRequest("android", token)
        assertTrue(
            deviceTokenRepository.all().any { it.token == token },
            "Token should exist before deregistration",
        )

        deregisterRequest(token)

        assertFalse(
            deviceTokenRepository.all().any { it.token == token },
            "Token should be removed after deregistration",
        )
    }

    @Test
    fun `POST register with same token twice is idempotent (upsert)`() {
        val token = "idempotent-token-${UUID.randomUUID()}"

        val first = registerRequest("android", token)
        val second = registerRequest("android", token)

        assertEquals(Status.NO_CONTENT, first.status)
        assertEquals(Status.NO_CONTENT, second.status)

        // Should only have one entry for this token
        val count = deviceTokenRepository.all().count { it.token == token }
        assertEquals(1, count, "Upsert should not create duplicate entries")
    }

    @Test
    fun `stored token has correct userId`() {
        val token = "userid-check-token-${UUID.randomUUID()}"
        registerRequest("ios", token)

        val stored = deviceTokenRepository.all().find { it.token == token }
        assertEquals(
            testUser.id,
            stored?.userId,
            "Stored token should have the authenticated user's id",
        )
    }

    @Test
    fun `stored token has correct platform`() {
        val token = "platform-check-token-${UUID.randomUUID()}"
        registerRequest("ios", token)

        val stored = deviceTokenRepository.all().find { it.token == token }
        assertEquals("ios", stored?.platform, "Stored token should have the requested platform")
    }

    @Test
    fun `POST register with appBundle stores the bundle`() {
        val token = "bundle-token-${UUID.randomUUID()}"
        registerRequest("ios", token, appBundle = "com.example.app")

        val stored = deviceTokenRepository.all().find { it.token == token }
        assertEquals("com.example.app", stored?.appBundle, "App bundle should be stored")
    }
}
