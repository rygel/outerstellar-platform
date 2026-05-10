package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.template.TemplateRenderer
import org.junit.jupiter.api.Test

class AdminSectionTest {

    @Test
    fun `default PlatformPlugin adminSections returns empty list`() {
        val plugin =
            object : PlatformPlugin {
                override val id: String = "test-plugin"
            }
        val renderer = mockk<TemplateRenderer>(relaxed = true)
        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val context = PluginContext.forTesting(renderer, securityService, userRepository)
        assertTrue(plugin.adminSections(context).isEmpty())
    }

    @Test
    fun `AdminMetric holds label value and optional trend`() {
        val withTrend = AdminMetric("Users", "42", "+5%")
        assertEquals("Users", withTrend.label)
        assertEquals("42", withTrend.value)
        assertEquals("+5%", withTrend.trend)

        val withoutTrend = AdminMetric("Active", "10")
        assertEquals("Active", withoutTrend.label)
        assertEquals("10", withoutTrend.value)
        assertNull(withoutTrend.trend)
    }

    @Test
    fun `AdminSummaryCard holds title metrics and link`() {
        val metrics = listOf(AdminMetric("Count", "5"))
        val card = AdminSummaryCard("Users", metrics, "/admin/users")
        assertEquals("Users", card.title)
        assertEquals(metrics, card.metrics)
        assertEquals("/admin/users", card.linkUrl)
        assertEquals("View details", card.linkLabel)

        val customLabel = AdminSummaryCard("Plugins", metrics, "/admin/plugins", "Manage")
        assertEquals("Manage", customLabel.linkLabel)
    }

    @Test
    fun `AdminSection holds id navLabel navIcon card and route`() {
        val metrics = listOf(AdminMetric("Count", "3"))
        val card = AdminSummaryCard("Test", metrics, "/admin/plugins/test")
        val route =
            "/admin/plugins/test" meta
                {
                    summary = "Test"
                } bindContract
                Method.GET to
                { _: org.http4k.core.Request ->
                    Response(Status.OK)
                }
        val section = AdminSection("test-plugins", "Plugins", "puzzle", card, route)

        assertEquals("test-plugins", section.id)
        assertEquals("Plugins", section.navLabel)
        assertEquals("puzzle", section.navIcon)
        assertEquals(card, section.summaryCard)
        assertEquals(route, section.route)
    }

    @Test
    fun `AdminNavItem holds label url and icon`() {
        val item = AdminNavItem("Users", "/admin/users", "people")
        assertEquals("Users", item.label)
        assertEquals("/admin/users", item.url)
        assertEquals("people", item.icon)
    }

    @Test
    fun `PluginOptions carries adminNavItems`() {
        val items =
            listOf(AdminNavItem("Users", "/admin/users", "people"), AdminNavItem("Audit", "/admin/audit", "shield"))
        val options = PluginOptions(adminNavItems = items)
        assertEquals(2, options.adminNavItems.size)
        assertEquals("Users", options.adminNavItems[0].label)
        assertEquals("Audit", options.adminNavItems[1].label)

        val defaults = PluginOptions()
        assertTrue(defaults.adminNavItems.isEmpty())
    }
}
