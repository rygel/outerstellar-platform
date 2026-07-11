package io.github.rygel.outerstellar.platform.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenHashingTest {
    @Test
    fun `pepper must contain at least 32 UTF-8 bytes`() {
        val error = assertFailsWith<IllegalArgumentException> { TokenHashing("known-short-pepper") }
        assertTrue(error.message?.contains("at least 32 UTF-8 bytes") == true)
    }

    @Test
    fun `deployment-specific peppers produce stable but distinct token hashes`() {
        val token = "oss_high-entropy-token"
        val first = TokenHashing("first-deployment-token-pepper-32-bytes")
        val second = TokenHashing("second-deployment-token-pepper-32-bytes")

        assertEquals(first.hash(token), first.hash(token))
        assertNotEquals(first.hash(token), second.hash(token))
    }
}
