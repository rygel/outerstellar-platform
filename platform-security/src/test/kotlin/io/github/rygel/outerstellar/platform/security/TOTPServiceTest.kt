package io.github.rygel.outerstellar.platform.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TOTPServiceTest {

    private lateinit var totpService: TOTPService

    @BeforeEach
    fun setUp() {
        totpService = TOTPService()
    }

    @Test
    fun `generateSecret returns a non-blank string`() {
        val secret = totpService.generateSecret()
        assertTrue(secret.isNotBlank(), "Secret should not be blank")
    }

    @Test
    fun `generateQrDataUri returns a data URI`() {
        val secret = totpService.generateSecret()
        val uri = totpService.generateQrDataUri(secret, "test@example.com")
        assertTrue(uri.startsWith("data:image/png;base64,"), "Should return PNG data URI")
    }

    @Test
    fun `verifyCode returns false for invalid code`() {
        val secret = totpService.generateSecret()
        assertFalse(totpService.verifyCode(secret, "000000"), "Invalid code should not verify")
    }

    @Test
    fun `generateBackupCodes returns 16 codes`() {
        val (codes, _) = totpService.generateBackupCodes()
        assertEquals(16, codes.size, "Should generate 16 backup codes")
        codes.forEach { code -> assertTrue(code.isNotBlank(), "Each code should be non-blank") }
    }

    @Test
    fun `verifyBackupCode returns null for invalid code`() {
        val (_, hashed) = totpService.generateBackupCodes()
        val result = totpService.verifyBackupCode("invalid-code", hashed)
        assertNull(result, "Invalid code should return null")
    }

    @Test
    fun `verifyBackupCode consumes valid code`() {
        val (rawCodes, hashed) = totpService.generateBackupCodes()
        val result = totpService.verifyBackupCode(rawCodes[0], hashed)
        assertNotNull(result, "Valid code should return updated JSON")
        val result2 = totpService.verifyBackupCode(rawCodes[0], result!!)
        assertNull(result2, "Consumed code should not verify again")
    }

    @Test
    fun `different secrets produce different QR URIs`() {
        val secret1 = totpService.generateSecret()
        val secret2 = totpService.generateSecret()
        val uri1 = totpService.generateQrDataUri(secret1, "a@b.com")
        val uri2 = totpService.generateQrDataUri(secret2, "a@b.com")
        assertFalse(uri1 == uri2, "Different secrets should produce different QR URIs")
    }
}
