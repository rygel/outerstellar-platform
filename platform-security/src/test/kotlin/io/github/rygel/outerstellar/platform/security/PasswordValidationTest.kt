package io.github.rygel.outerstellar.platform.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PasswordValidationTest {

    @Test
    fun `returns null for valid complex password`() {
        assertNull(validatePassword("Abc123!@"))
    }

    @Test
    fun `returns null for exactly min length with all rules`() {
        assertNull(validatePassword("A1b!cdef"))
    }

    @Test
    fun `returns null for exactly max length`() {
        val pw = "A1!" + "a".repeat(125)
        assertNull(validatePassword(pw))
    }

    @Test
    fun `returns error for short password`() {
        val error = validatePassword("Ab1!")
        assertNotNull(error)
        assertEquals("Password must be at least 8 characters", error)
    }

    @Test
    fun `returns error for long password`() {
        val pw = "A1!" + "a".repeat(126)
        val error = validatePassword(pw)
        assertNotNull(error)
        assertEquals("Password must be at most 128 characters", error)
    }

    @Test
    fun `returns error when missing uppercase`() {
        val error = validatePassword("abcdefg1!")
        assertNotNull(error)
        assertEquals("Password must contain at least one uppercase letter", error)
    }

    @Test
    fun `returns error when missing lowercase`() {
        val error = validatePassword("ABCDEFG1!")
        assertNotNull(error)
        assertEquals("Password must contain at least one lowercase letter", error)
    }

    @Test
    fun `returns error when missing digit`() {
        val error = validatePassword("Abcdefg!")
        assertNotNull(error)
        assertEquals("Password must contain at least one digit", error)
    }

    @Test
    fun `returns error when missing special character`() {
        val error = validatePassword("Abcdefg1")
        assertNotNull(error)
        assertEquals("Password must contain at least one special character", error)
    }

    @Test
    fun `allows leading and trailing whitespace as password characters`() {
        assertNull(validatePassword("  Abc123!@  "))
    }

    @Test
    fun `counts leading and trailing whitespace toward password length`() {
        assertNull(validatePassword("  Ab1!  "))
    }

    @Test
    fun `returns first failing rule`() {
        val error = validatePassword("abc")
        assertNotNull(error)
        assertEquals("Password must be at least 8 characters", error)
    }
}
