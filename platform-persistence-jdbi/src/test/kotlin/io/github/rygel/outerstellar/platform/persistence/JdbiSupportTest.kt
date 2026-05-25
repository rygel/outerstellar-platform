package io.github.rygel.outerstellar.platform.persistence

import io.mockk.every
import io.mockk.mockk
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.statement.StatementContext
import org.junit.jupiter.api.Test

class JdbiSupportTest {

    @Test
    fun `escapeLike escapes percent sign`() {
        assertEquals("50!%", "50%".escapeLike())
    }

    @Test
    fun `escapeLike escapes underscore`() {
        assertEquals("a!_b", "a_b".escapeLike())
    }

    @Test
    fun `escapeLike escapes exclamation mark`() {
        assertEquals("wow!!", "wow!".escapeLike())
    }

    @Test
    fun `escapeLike handles empty string`() {
        assertEquals("", "".escapeLike())
    }

    @Test
    fun `escapeLike handles string with no special characters`() {
        assertEquals("hello", "hello".escapeLike())
    }

    @Test
    fun `escapeLike handles string with all special characters mixed`() {
        assertEquals("!!50!%!_done!!", "!50%_done!".escapeLike())
    }

    @Test
    fun `InstantArgumentFactory returns present Optional for Instant type`() {
        val factory = InstantArgumentFactory()
        val instant = Instant.parse("2025-01-15T10:30:00Z")
        val result = factory.build(Instant::class.java, instant, ConfigRegistry())
        assertTrue(result.isPresent)
    }

    @Test
    fun `InstantArgumentFactory returns empty Optional for non-Instant type`() {
        val factory = InstantArgumentFactory()
        val result = factory.build(String::class.java, "not an instant", ConfigRegistry())
        assertFalse(result.isPresent)
    }

    @Test
    fun `InstantColumnMapper maps Timestamp via column index`() {
        val mapper = InstantColumnMapper()
        val instant = Instant.parse("2025-06-01T12:00:00Z")
        val rs: ResultSet = mockk { every { getTimestamp(any<Int>()) } returns Timestamp.from(instant) }
        val ctx: StatementContext = mockk()
        assertEquals(instant, mapper.map(rs, 1, ctx))
    }

    @Test
    fun `InstantColumnMapper returns null for null Timestamp via column index`() {
        val mapper = InstantColumnMapper()
        val rs: ResultSet = mockk { every { getTimestamp(any<Int>()) } returns null }
        val ctx: StatementContext = mockk()
        assertNull(mapper.map(rs, 1, ctx))
    }

    @Test
    fun `InstantColumnMapper maps Timestamp via column label`() {
        val mapper = InstantColumnMapper()
        val instant = Instant.parse("2025-06-01T12:00:00Z")
        val rs: ResultSet = mockk { every { getTimestamp(any<String>()) } returns Timestamp.from(instant) }
        val ctx: StatementContext = mockk()
        assertEquals(instant, mapper.map(rs, "created_at", ctx))
    }

    @Test
    fun `InstantColumnMapper returns null for null Timestamp via column label`() {
        val mapper = InstantColumnMapper()
        val rs: ResultSet = mockk { every { getTimestamp(any<String>()) } returns null }
        val ctx: StatementContext = mockk()
        assertNull(mapper.map(rs, "created_at", ctx))
    }
}
