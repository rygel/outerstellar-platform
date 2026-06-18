package io.github.rygel.outerstellar.platform.security

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
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
        // logRounds = 4 keeps the suite fast (repo convention) — production uses the default 12.
        totpService = TOTPService(BCryptPasswordEncoder(logRounds = 4))
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
    fun `generateBackupCodes stores BCrypt hashes`() {
        val (_, hashed) = totpService.generateBackupCodes()
        val stored = hashed.removeSurrounding("[", "]").split(",").map { it.trim().removeSurrounding("\"") }
        stored.forEach { hash ->
            assertTrue(
                hash.startsWith("\$2a$") || hash.startsWith("\$2b$"),
                "Backup code must be a BCrypt hash, was: $hash",
            )
        }
    }

    @Test
    fun `verifyBackupCode rejects legacy unsalted SHA-256 hashes`() {
        // Pre-fix backup codes were stored as 64-char hex SHA-256 digests. After the BCrypt migration
        // these must no longer verify (they cannot be re-hashed without the plaintext, so they are
        // invalidated by design). Simulate a stored legacy hash for a known code.
        val legacySha256 = "a".repeat(64)
        val stored = """["$legacySha256"]"""
        assertNull(totpService.verifyBackupCode("anything", stored), "Legacy SHA-256 hash must not verify")
    }

    @Test
    fun `different secrets produce different QR URIs`() {
        val secret1 = totpService.generateSecret()
        val secret2 = totpService.generateSecret()
        val uri1 = totpService.generateQrDataUri(secret1, "a@b.com")
        val uri2 = totpService.generateQrDataUri(secret2, "a@b.com")
        assertFalse(uri1 == uri2, "Different secrets should produce different QR URIs")
    }

    @Test
    fun `verifyBackupCode parses a stored hash containing commas and quotes without corruption`() {
        // Regression guard for issue #512: the old hand-rolled parser split on "," and stripped
        // surrounding quotes, so a hash containing those characters was silently corrupted. A
        // properly-escaped JSON array (as kotlinx.serialization now produces) must round-trip intact.
        // Build the stored JSON via the same codec so it's guaranteed valid despite the special chars.
        val trickyHash = """hash,with"quote\backslash"""
        val other = "plain"
        val stored = Json.encodeToString(ListSerializer(String.serializer()), listOf(trickyHash, other))
        val stubMatchingEncoder =
            object : PasswordEncoder {
                override fun encode(raw: String): String = ""

                override fun matches(raw: String, hashed: String): Boolean = hashed == trickyHash
            }
        val service = TOTPService(stubMatchingEncoder)
        val remaining = service.verifyBackupCode("anything", stored)
        assertNotNull(remaining, "A matching hash with special characters must parse and verify")
        // The matched hash should be consumed, leaving only "plain".
        assertEquals("""["plain"]""", remaining!!.replace(" ", ""))
    }

    @Test
    fun `verifyBackupCode treats the blank exhausted sentinel as an empty code set`() {
        // verifyBackupCode returns "" when the last code is consumed; that "" is stored and later re-parsed.
        // The parser must yield an empty list (not throw) for blank input — the old code relied on this too.
        assertNull(totpService.verifyBackupCode("anything", ""), "Blank sentinel must yield no match")
    }

    @Test
    fun `verifyBackupCode round-trips a full generate-then-consume cycle without data loss`() {
        // End-to-end: generate 16, consume all 16 in sequence; the final consume must return "" (exhausted),
        // and re-verifying any code against the exhausted sentinel must return null. Confirms the JSON
        // round-trip survives a realistic full-consumption sequence.
        val (rawCodes, hashed) = totpService.generateBackupCodes()
        var current = hashed
        for (code in rawCodes) {
            val updated = totpService.verifyBackupCode(code, current)
            assertNotNull(updated, "Each code must verify in sequence")
            current = updated!!
        }
        assertEquals("", current, "After consuming all codes the sentinel must be the empty string")
        assertNull(totpService.verifyBackupCode(rawCodes[0], current), "Exhausted set must yield no match")
    }
}
