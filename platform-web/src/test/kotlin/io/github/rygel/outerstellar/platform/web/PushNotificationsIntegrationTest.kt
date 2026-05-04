package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.DeviceToken
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.ApnsPushNotificationService
import io.github.rygel.outerstellar.platform.service.ConsolePushNotificationService
import io.github.rygel.outerstellar.platform.service.FcmPushNotificationService
import io.github.rygel.outerstellar.platform.service.PushNotification
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
 * Integration tests for push notification device registration API (Feature 5).
 *
 * Verifies:
 * - POST /api/v1/devices/register with valid bearer token + valid body → 204
 * - POST with unsupported platform → 400
 * - POST with blank token → 400
 * - POST without Authorization header → 401
 * - POST with malformed JSON body → 400
 * - DELETE /api/v1/devices/register with valid bearer + JSON body → 204
 * - DELETE with ?token query param fallback → 204
 * - DELETE without Authorization header → 401
 * - Token is actually stored in the repository on successful POST
 * - Token is actually removed from the repository on successful DELETE
 * - Multiple tokens for the same user can be registered
 * - ConsolePushNotificationService.send does not throw and logs correctly
 * - FcmPushNotificationService.send skips non-android platforms
 * - ApnsPushNotificationService.send skips non-ios platforms
 * - PushNotificationService.sendToAll dispatches to all provided tokens
 */
class PushNotificationsIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var deviceTokenRepository: InMemoryDeviceTokenRepository
    private lateinit var testUser: User
    private lateinit var sessionToken: String

    @BeforeEach
    fun setupTest() {
        deviceTokenRepository = InMemoryDeviceTokenRepository()
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "pushtest",
                email = "pushtest@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        sessionToken = securityService.createSession(testUser.id)

        app =
            buildApp(
                securityService = securityService,
                overrides = TestOverrides(deviceTokenRepository = deviceTokenRepository),
            )
    }

    @AfterEach
    fun teardown() {
        deviceTokenRepository.clear()
        cleanup()
    }

    // ---- POST /api/v1/devices/register ----

    @Test
    fun `POST register with valid bearer and android platform returns 204`() {
        val response =
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("""{"platform":"android","token":"fcm-token-abc123"}""")
            )
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `POST register with valid bearer and ios platform returns 204`() {
        val response =
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("""{"platform":"ios","token":"apns-token-xyz789"}""")
            )
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `POST register with optional appBundle field returns 204`() {
        val response =
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("""{"platform":"ios","token":"apns-bundle-token","appBundle":"com.example.app"}""")
            )
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `POST register without Authorization returns 401`() {
        val response =
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("content-type", "application/json")
                    .body("""{"platform":"android","token":"fcm-token-noauth"}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST register with unsupported platform returns 400`() {
        val response =
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("""{"platform":"windows","token":"some-token"}""")
            )
        assertEquals(Status.BAD_REQUEST, response.status)
        assertTrue(
            response.bodyString().contains("platform"),
            "400 response should mention 'platform' in the error message",
        )
    }

    @Test
    fun `POST register with blank token returns 400`() {
        val response =
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("""{"platform":"android","token":""}""")
            )
        assertEquals(Status.BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("token"), "400 response should mention 'token'")
    }

    @Test
    fun `POST register with malformed JSON returns 400`() {
        val response =
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("not-valid-json{{{")
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    // ---- Repository state after POST ----

    @Test
    fun `POST register stores the token in the device token repository`() {
        val tokenValue = "fcm-stored-token-${UUID.randomUUID()}"
        app(
            Request(POST, "/api/v1/devices/register")
                .header("Authorization", "Bearer $sessionToken")
                .header("content-type", "application/json")
                .body("""{"platform":"android","token":"$tokenValue"}""")
        )

        val stored = deviceTokenRepository.findByUserId(testUser.id)
        assertTrue(stored.isNotEmpty(), "Token should be stored after successful registration")
        assertEquals(tokenValue, stored.first().token)
        assertEquals("android", stored.first().platform)
    }

    @Test
    fun `POST register stores appBundle when provided`() {
        app(
            Request(POST, "/api/v1/devices/register")
                .header("Authorization", "Bearer $sessionToken")
                .header("content-type", "application/json")
                .body("""{"platform":"ios","token":"apns-bundle-stored","appBundle":"com.myapp.bundle"}""")
        )

        val stored = deviceTokenRepository.findByUserId(testUser.id)
        assertEquals("com.myapp.bundle", stored.firstOrNull()?.appBundle)
    }

    @Test
    fun `multiple tokens for the same user can be registered`() {
        repeat(3) { i ->
            app(
                Request(POST, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("""{"platform":"android","token":"fcm-multi-token-$i"}""")
            )
        }

        val stored = deviceTokenRepository.findByUserId(testUser.id)
        assertEquals(3, stored.size, "All three device tokens should be stored")
    }

    // ---- DELETE /api/v1/devices/register ----

    @Test
    fun `DELETE register with valid bearer and JSON body returns 204`() {
        val tokenValue = "fcm-to-delete-${UUID.randomUUID()}"
        // First register it
        app(
            Request(POST, "/api/v1/devices/register")
                .header("Authorization", "Bearer $sessionToken")
                .header("content-type", "application/json")
                .body("""{"platform":"android","token":"$tokenValue"}""")
        )

        val response =
            app(
                Request(DELETE, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("""{"token":"$tokenValue"}""")
            )
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE register removes the token from the repository`() {
        val tokenValue = "fcm-removed-${UUID.randomUUID()}"
        app(
            Request(POST, "/api/v1/devices/register")
                .header("Authorization", "Bearer $sessionToken")
                .header("content-type", "application/json")
                .body("""{"platform":"android","token":"$tokenValue"}""")
        )

        app(
            Request(DELETE, "/api/v1/devices/register")
                .header("Authorization", "Bearer $sessionToken")
                .header("content-type", "application/json")
                .body("""{"token":"$tokenValue"}""")
        )

        val remaining = deviceTokenRepository.findByUserId(testUser.id)
        assertFalse(remaining.any { it.token == tokenValue }, "Token should be removed from repository after DELETE")
    }

    @Test
    fun `DELETE register with token as query param returns 204`() {
        val tokenValue = "fcm-query-param-${UUID.randomUUID()}"
        app(
            Request(POST, "/api/v1/devices/register")
                .header("Authorization", "Bearer $sessionToken")
                .header("content-type", "application/json")
                .body("""{"platform":"android","token":"$tokenValue"}""")
        )

        val response =
            app(
                Request(DELETE, "/api/v1/devices/register?token=$tokenValue")
                    .header("Authorization", "Bearer $sessionToken")
            )
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE register without Authorization returns 401`() {
        val response =
            app(
                Request(DELETE, "/api/v1/devices/register")
                    .header("content-type", "application/json")
                    .body("""{"token":"some-token"}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `DELETE register without token body or query param returns 400`() {
        val response =
            app(
                Request(DELETE, "/api/v1/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .header("content-type", "application/json")
                    .body("{}")
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    // ---- ConsolePushNotificationService ----

    @Test
    fun `ConsolePushNotificationService send does not throw`() {
        val notification = PushNotification(title = "Hello", body = "World")
        // Should complete without throwing regardless of platform
        ConsolePushNotificationService.send("android", "fake-fcm-token", notification)
        ConsolePushNotificationService.send("ios", "fake-apns-token", notification)
    }

    @Test
    fun `ConsolePushNotificationService send accepts data payload`() {
        val notification =
            PushNotification(
                title = "Alert",
                body = "Something happened",
                data = mapOf("key" to "value", "count" to "42"),
            )
        // Must not throw
        ConsolePushNotificationService.send("android", "fcm-data-token", notification)
    }

    // ---- FcmPushNotificationService ----

    @Test
    fun `FcmPushNotificationService send does not throw for android platform`() {
        val service = FcmPushNotificationService()
        val notification = PushNotification(title = "FCM Test", body = "body")
        // Stub implementation logs a warning but must not throw
        service.send("android", "fcm-device-token", notification)
    }

    @Test
    fun `FcmPushNotificationService skips non-android platforms`() {
        val service = FcmPushNotificationService()
        val notification = PushNotification(title = "FCM Test", body = "body")
        // Should silently skip iOS without throwing
        service.send("ios", "apns-token-ignored", notification)
    }

    // ---- ApnsPushNotificationService ----

    @Test
    fun `ApnsPushNotificationService send does not throw for ios platform`() {
        val service = ApnsPushNotificationService()
        val notification = PushNotification(title = "APNs Test", body = "body")
        // Stub implementation logs a warning but must not throw
        service.send("ios", "apns-device-token", notification)
    }

    @Test
    fun `ApnsPushNotificationService skips non-ios platforms`() {
        val service = ApnsPushNotificationService()
        val notification = PushNotification(title = "APNs Test", body = "body")
        // Should silently skip Android without throwing
        service.send("android", "fcm-token-ignored", notification)
    }

    // ---- sendToAll ----

    @Test
    fun `sendToAll dispatches to all provided tokens`() {
        val received = mutableListOf<Pair<String, String>>()
        val capturingService =
            object : io.github.rygel.outerstellar.platform.service.PushNotificationService {
                override fun send(platform: String, deviceToken: String, notification: PushNotification) {
                    received.add(platform to deviceToken)
                }
            }

        val tokens = listOf("android" to "fcm-token-1", "ios" to "apns-token-2", "android" to "fcm-token-3")
        val notification = PushNotification(title = "Broadcast", body = "All devices")

        capturingService.sendToAll(tokens, notification)

        assertEquals(3, received.size, "sendToAll should dispatch once per token")
        assertTrue(received.contains("android" to "fcm-token-1"))
        assertTrue(received.contains("ios" to "apns-token-2"))
        assertTrue(received.contains("android" to "fcm-token-3"))
    }

    @Test
    fun `sendToAll with empty list sends nothing`() {
        var sendCallCount = 0
        val countingService =
            object : io.github.rygel.outerstellar.platform.service.PushNotificationService {
                override fun send(platform: String, deviceToken: String, notification: PushNotification) {
                    sendCallCount++
                }
            }

        countingService.sendToAll(emptyList(), PushNotification("title", "body"))

        assertEquals(0, sendCallCount, "sendToAll with empty list must not call send")
    }

    // ---- DeviceToken model ----

    @Test
    fun `DeviceToken data class holds expected fields`() {
        val userId = UUID.randomUUID()
        val token =
            DeviceToken(id = 1L, userId = userId, platform = "ios", token = "apns-abc", appBundle = "com.example.myapp")
        assertEquals(1L, token.id)
        assertEquals(userId, token.userId)
        assertEquals("ios", token.platform)
        assertEquals("apns-abc", token.token)
        assertEquals("com.example.myapp", token.appBundle)
    }

    @Test
    fun `DeviceToken appBundle can be null`() {
        val token =
            DeviceToken(
                id = 2L,
                userId = UUID.randomUUID(),
                platform = "android",
                token = "fcm-no-bundle",
                appBundle = null,
            )
        assertEquals(null, token.appBundle)
    }
}
