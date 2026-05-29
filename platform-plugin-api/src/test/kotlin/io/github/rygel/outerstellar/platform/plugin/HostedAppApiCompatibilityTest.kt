package io.github.rygel.outerstellar.platform.plugin

import io.github.rygel.outerstellar.platform.PluginMigrations
import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import kotlin.test.Test
import kotlin.test.assertEquals

class HostedAppApiCompatibilityTest {
    @Test
    fun `old PlatformPlugin compatibility interface satisfies HostedApp`() {
        val hostedApp: HostedApp =
            object : PlatformPlugin {
                override val id: String = "reports"
            }

        assertEquals("reports", hostedApp.id)
        assertEquals("Outerstellar", hostedApp.manifest.appLabel)
        assertEquals(listOf("/reports", "/plugin/reports"), hostedApp.manifest.ownership.uiPrefixes)
    }

    @Test
    fun `legacy migration properties still adapt into hosted app migrations`() {
        @Suppress("DEPRECATION")
        val hostedApp: HostedApp =
            object : PlatformPlugin {
                override val id: String = "reports"
                override val migrationLocation: String = "classpath:db/migration/reports"
                override val migrationHistoryTable: String = "flyway_reports_history"
                override val migrationNames: List<String> = listOf("V1__init", "V2__seed")
            }

        assertEquals(
            PluginMigrations(
                location = "classpath:db/migration/reports",
                historyTable = "flyway_reports_history",
                migrationNames = listOf("V1__init", "V2__seed"),
            ),
            hostedApp.migrations,
        )
    }
}
