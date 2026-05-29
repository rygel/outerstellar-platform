package io.github.rygel.outerstellar.platform.composition

import org.junit.jupiter.api.Test

class PlatformPageSetTest {
    @Test
    fun `pageSet equality is by value`() {
        val a = PlatformPageSet("home", "Home page")
        val b = PlatformPageSet("home", "Different description")
        assert(a == b) { "Expected equal ids to be equal" }
    }

    @Test
    fun `pageSet with different ids are not equal`() {
        val a = PlatformPageSet("home", "Home page")
        val b = PlatformPageSet("settings", "Settings page")
        assert(a != b) { "Expected different ids to not be equal" }
    }

    @Test
    fun `pageSet can be used in a Set`() {
        val set =
            setOf(
                PlatformPageSet("home", "Home"),
                PlatformPageSet("settings", "Settings"),
                PlatformPageSet("home", "Duplicate"),
            )
        assert(set.size == 2) { "Expected 2 unique entries but got ${set.size}" }
    }
}
