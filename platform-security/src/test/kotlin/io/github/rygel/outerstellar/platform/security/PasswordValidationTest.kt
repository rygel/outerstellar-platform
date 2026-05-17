package io.github.rygel.outerstellar.platform.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PasswordValidationTest {

    @Test
    fun `returns null for valid password`() {
        assertNull(validatePassword("abcdefgh"))
    }

    @Test
    fun `returns null for exactly min length`() {
        assertNull(validatePassword("A".repeat(8)))
    }

    @Test
    fun `returns null for exactly max length`() {
        assertNull(validatePassword("A".repeat(128)))
    }

    @Test
    fun `returns error for short password`() {
        val error = validatePassword("Abc123")
        assertNotNull(error)
        assertEquals("Password must be at least 8 characters", error)
    }

    @Test
    fun `returns error for long password`() {
        val error = validatePassword("A".repeat(129))
        assertNotNull(error)
        assertEquals("Password must be at most 128 characters", error)
    }

    @Test
    fun `trims whitespace before validation`() {
        assertNull(validatePassword("  abcdefgh  "))
    }

    @Test
    fun `returns error for password that is short after trimming`() {
        val error = validatePassword("  Ab  ")
        assertNotNull(error)
        assertEquals("Password must be at least 8 characters", error)
    }
}
