package io.github.rygel.stellar.web

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class StellarPluginTest {
    private val plugin = StellarPlugin()

    @Test
    fun `id is stellar`() {
        assertEquals("stellar", plugin.id)
    }

    @Test
    fun `appLabel is Stellar`() {
        assertEquals("Stellar", plugin.appLabel)
    }

    @Test
    fun `migrationLocation points to stellar migrations`() {
        assertEquals("classpath:db/migration/stellar", plugin.migrationLocation)
    }

    @Test
    fun `migrationHistoryTable is flyway_stellar_history`() {
        assertEquals("flyway_stellar_history", plugin.migrationHistoryTable)
    }

    @Test
    fun `excludeDefaultRoutes contains home contacts and messages`() {
        assertEquals(setOf("/", "/contacts", "/messages"), plugin.excludeDefaultRoutes)
    }

    @Test
    fun `navItems has 4 entries`() {
        assertEquals(4, plugin.navItems.size)
    }

    @Test
    fun `navItems labels are Home Popular Communities Search`() {
        val labels = plugin.navItems.map { it.label }
        assertEquals(listOf("Home", "Popular", "Communities", "Search"), labels)
    }

    @Test
    fun `navItems urls are correct`() {
        val urls = plugin.navItems.map { it.url }
        assertEquals(listOf("/", "/popular", "/communities", "/search"), urls)
    }

    @Test
    fun `navItems icons are remixicon classes`() {
        val icons = plugin.navItems.map { it.icon }
        assertEquals(listOf("ri-home-5-line", "ri-fire-line", "ri-group-2-line", "ri-search-line"), icons)
    }

    @Test
    fun `navItems activeSections match urls`() {
        plugin.navItems.forEach { item ->
            assertEquals(item.url, item.activeSection, "activeSection should match url for ${item.label}")
        }
    }

    @Test
    fun `koinModules returns 2 modules`() {
        val modules = plugin.koinModules()
        assertEquals(2, modules.size)
    }

    @Test
    fun `filters returns empty list`() {
        // filters() requires a PluginContext but should return empty regardless;
        // we use mockk to supply one without a real renderer.
        val context = io.mockk.mockk<io.github.rygel.outerstellar.platform.web.PluginContext>(relaxed = true)
        assertTrue(plugin.filters(context).isEmpty())
    }
}
