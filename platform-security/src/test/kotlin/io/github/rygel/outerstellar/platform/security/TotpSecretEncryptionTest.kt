package io.github.rygel.outerstellar.platform.security

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TotpSecretEncryptionTest {
    private val encryption = TotpSecretEncryption(TEST_TOKEN_PEPPER)

    @Test
    fun `encryption round trips without deterministic ciphertext`() {
        val first = encryption.encrypt(TOTP_SECRET)
        val second = encryption.encrypt(TOTP_SECRET)

        assertNotEquals(first, second)
        assertEquals(TOTP_SECRET, encryption.decrypt(first))
        assertEquals(TOTP_SECRET, encryption.decrypt(second))
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val encrypted = encryption.encrypt(TOTP_SECRET)
        val encoded = encrypted.removePrefix(TotpSecretEncryption.STORAGE_PREFIX)
        val payload = Base64.getUrlDecoder().decode(encoded)
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        val tampered =
            TotpSecretEncryption.STORAGE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)

        assertFailsWith<Exception> { encryption.decrypt(tampered) }
    }

    @Test
    fun `a different deployment pepper cannot decrypt the secret`() {
        val encrypted = encryption.encrypt(TOTP_SECRET)
        val otherDeployment = TotpSecretEncryption("different-deployment-token-pepper-32-bytes")

        assertFailsWith<Exception> { otherDeployment.decrypt(encrypted) }
    }

    @Test
    fun `short deployment pepper is rejected`() {
        assertFailsWith<IllegalArgumentException> { TotpSecretEncryption("short-pepper") }
    }

    companion object {
        private const val TEST_TOKEN_PEPPER = "totp-encryption-test-token-pepper-32-bytes"
        private const val TOTP_SECRET = "JBSWY3DPEHPK3PXP"
    }
}
