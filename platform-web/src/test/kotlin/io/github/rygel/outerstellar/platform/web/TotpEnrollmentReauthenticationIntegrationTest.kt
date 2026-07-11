package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.time.SystemTimeProvider
import io.github.rygel.outerstellar.platform.security.TotpSecretEncryption
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus

class TotpEnrollmentReauthenticationIntegrationTest : WebTest() {
    private val app by lazy { buildApp() }

    @Test
    fun `web enrollment rejects an incorrect current password without enabling TOTP`() {
        val user = authenticatedUser()
        val setup = app(formRequest("/auth/components/totp-setup", user.sessionToken))
        val secret = setupSecret(setup.bodyString())

        val response =
            app(
                formRequest(
                    "/auth/components/totp-verify-setup",
                    user.sessionToken,
                    "secret=$secret&code=000000&password=wrong-password",
                )
            )

        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().contains("Your password is incorrect."))
        assertFalse(userRepository.findById(user.id)?.totpEnabled ?: true, "TOTP must remain disabled")
    }

    @Test
    fun `web enrollment enables TOTP only after current password and authenticator code are valid`() {
        val user = authenticatedUser()
        val setup = app(formRequest("/auth/components/totp-setup", user.sessionToken))
        val secret = setupSecret(setup.bodyString())
        val code = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, SystemTimeProvider().time / 30)

        val response =
            app(
                formRequest(
                    "/auth/components/totp-verify-setup",
                    user.sessionToken,
                    "secret=$secret&code=$code&password=$PASSWORD",
                )
            )

        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().contains("Backup Codes"), "Successful enrollment should show recovery codes")
        val stored = assertNotNull(userRepository.findById(user.id))
        assertTrue(stored.totpEnabled, "TOTP should be enabled")
        val encryptedSecret = assertNotNull(stored.totpSecret)
        assertTrue(encryptedSecret.startsWith(TotpSecretEncryption.STORAGE_PREFIX))
        assertEquals(secret, TotpSecretEncryption(testConfig.tokenPepper).decrypt(encryptedSecret))
        assertNotNull(stored.totpBackupCodes, "Recovery-code hashes should be persisted")
    }

    @Test
    fun `API enrollment confirmation rejects an incorrect current password`() {
        val user = authenticatedUser()
        val response =
            app(
                Request(POST, "/api/v1/auth/totp/confirm")
                    .cookie(Cookie(RequestContext.SESSION_COOKIE, user.sessionToken))
                    .header("Content-Type", "application/json")
                    .body("""{"secret":"JBSWY3DPEHPK3PXP","code":"000000","password":"wrong-password"}""")
            )

        assertThat(response, hasStatus(Status.UNAUTHORIZED))
        assertTrue(response.bodyString().contains("invalid_password"))
        assertFalse(userRepository.findById(user.id)?.totpEnabled ?: true, "TOTP must remain disabled")
    }

    @Test
    fun `TOTP-enabled user can disable TOTP after confirming the correct password`() {
        val user = authenticatedUser()
        userRepository.updateTotpSecret(user.id, "JBSWY3DPEHPK3PXP", "[]")
        userRepository.enableTotp(user.id)

        val response =
            app(
                Request(POST, "/api/v1/auth/totp/disable")
                    .cookie(Cookie(RequestContext.SESSION_COOKIE, user.sessionToken))
                    .header("Content-Type", "application/json")
                    .body("""{"password":"$PASSWORD"}""")
            )

        assertThat(response, hasStatus(Status.OK))
        val stored = assertNotNull(userRepository.findById(user.id))
        assertFalse(stored.totpEnabled, "TOTP should be disabled")
        assertEquals(null, stored.totpSecret)
        assertEquals(null, stored.totpBackupCodes)
    }

    @Test
    fun `successful TOTP login migrates a legacy plaintext seed`() {
        val user = authenticatedUser()
        val secret = "JBSWY3DPEHPK3PXP"
        userRepository.updateTotpSecret(user.id, secret, null)
        userRepository.enableTotp(user.id)

        val challenge =
            app(
                formRequest(
                    "/auth/components/result",
                    user.sessionToken,
                    "mode=sign-in&email=${user.username}&password=$PASSWORD",
                )
            )
        val partialToken =
            assertNotNull(Regex("""name="partialToken" value="([^"]+)"""").find(challenge.bodyString())).groupValues[1]
        val code = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, SystemTimeProvider().time / 30)

        val response =
            app(formRequest("/auth/components/totp-verify", user.sessionToken, "partialToken=$partialToken&code=$code"))

        assertThat(response, hasStatus(Status.OK))
        assertEquals("/", response.header("HX-Redirect"))
        val encryptedSecret = assertNotNull(userRepository.findById(user.id)?.totpSecret)
        assertTrue(encryptedSecret.startsWith(TotpSecretEncryption.STORAGE_PREFIX))
        assertEquals(secret, TotpSecretEncryption(testConfig.tokenPepper).decrypt(encryptedSecret))
    }

    private fun authenticatedUser(): TestUser {
        val username = "totp-${UUID.randomUUID()}@example.com"
        val (sessionToken, userId, _) =
            withAuthenticatedUser(username = username, passwordHash = encoder.encode(PASSWORD))
        return TestUser(sessionToken, UUID.fromString(userId), username)
    }

    private fun formRequest(path: String, sessionToken: String, body: String = ""): Request =
        Request(POST, path)
            .cookie(Cookie(RequestContext.SESSION_COOKIE, sessionToken))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(body)

    private fun setupSecret(body: String): String {
        assertTrue(body.contains("Confirm your current password"), "Setup form should require password confirmation")
        return assertNotNull(Regex("""name="secret" value="([A-Z2-7]+)"""").find(body)?.groupValues?.get(1))
    }

    private data class TestUser(val sessionToken: String, val id: UUID, val username: String)

    companion object {
        private const val PASSWORD = "CurrentPassword123!"
    }
}
