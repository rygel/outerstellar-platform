package io.github.rygel.outerstellar.platform.web.composition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformPageSetsTest {
    @Test
    fun `all page sets have unique ids`() {
        val ids = PlatformPageSets.entries.map { it.pageSet.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `page sets cover expected platform UI pages`() {
        val ids = PlatformPageSets.entries.map { it.pageSet.id }.toSet()
        val expected =
            setOf("home", "contacts", "settings", "search", "notifications", "profile", "admin", "dev-dashboard")
        assertEquals(expected, ids)
    }

    @Test
    fun `each page set has a non-blank description`() {
        PlatformPageSets.entries.forEach { ps ->
            assertTrue(ps.pageSet.description.isNotBlank()) { "${ps.name} has a blank description" }
        }
    }

    @Test
    fun `page sets can be collected into a Set for includePlatformPages`() {
        val included = setOf(PlatformPageSets.SETTINGS, PlatformPageSets.SEARCH)
        assertEquals(2, included.size)
    }
}
