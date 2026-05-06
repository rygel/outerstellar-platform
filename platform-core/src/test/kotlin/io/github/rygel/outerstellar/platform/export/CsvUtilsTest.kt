package io.github.rygel.outerstellar.platform.export

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CsvUtilsTest {

    @Test
    fun `escapeCsv returns empty string for null`() {
        assertEquals("", CsvUtils.escapeCsv(null))
    }

    @Test
    fun `escapeCsv returns value unchanged when no special chars`() {
        assertEquals("hello", CsvUtils.escapeCsv("hello"))
    }

    @Test
    fun `escapeCsv quotes value containing comma`() {
        assertEquals("\"hello,world\"", CsvUtils.escapeCsv("hello,world"))
    }

    @Test
    fun `escapeCsv quotes value containing newline`() {
        assertEquals("\"hello\nworld\"", CsvUtils.escapeCsv("hello\nworld"))
    }

    @Test
    fun `escapeCsv quotes and doubles quotes containing quote`() {
        assertEquals("\"he said \"\"hello\"\"\"", CsvUtils.escapeCsv("he said \"hello\""))
    }

    @Test
    fun `escapeCsv handles combination of special chars`() {
        assertEquals("\"a,b\nc\"\"d\"", CsvUtils.escapeCsv("a,b\nc\"d"))
    }

    @Test
    fun `toCsvRow joins values with comma`() {
        assertEquals("a,b,c", CsvUtils.toCsvRow(listOf("a", "b", "c")))
    }

    @Test
    fun `toCsvRow escapes and quotes values as needed`() {
        val row = CsvUtils.toCsvRow(listOf("hello", "world,foo", "line\nbreak"))
        assertEquals("hello,\"world,foo\",\"line\nbreak\"", row)
    }
}
