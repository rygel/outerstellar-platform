package io.github.rygel.outerstellar.platform.extension

import io.github.rygel.outerstellar.platform.ExtensionMigrations
import io.github.rygel.outerstellar.platform.web.PlatformExtension
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformExtensionApiCompatibilityTest {
    @Test
    fun `old PlatformExtension compatibility interface satisfies PlatformExtension`() {
        val hostedApp: PlatformExtension =
            object : PlatformExtension {
                override val id: String = "reports"
            }

        assertEquals("reports", hostedApp.id)
        assertEquals("Outerstellar", hostedApp.manifest.appLabel)
        assertEquals(listOf("/reports", "/extension/reports"), hostedApp.manifest.ownership.uiPrefixes)
    }

    @Test
    fun `legacy migration properties still adapt into extension migrations`() {
        @Suppress("DEPRECATION")
        val hostedApp: PlatformExtension =
            object : PlatformExtension {
                override val id: String = "reports"
                override val migrationLocation: String = "classpath:db/migration/reports"
                override val migrationHistoryTable: String = "flyway_reports_history"
                override val migrationNames: List<String> = listOf("V1__init", "V2__seed")
            }

        assertEquals(
            ExtensionMigrations(
                location = "classpath:db/migration/reports",
                historyTable = "flyway_reports_history",
                migrationNames = listOf("V1__init", "V2__seed"),
            ),
            hostedApp.migrations,
        )
    }
}
