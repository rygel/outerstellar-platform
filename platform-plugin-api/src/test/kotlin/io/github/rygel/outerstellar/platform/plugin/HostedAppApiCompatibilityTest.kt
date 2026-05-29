package io.github.rygel.outerstellar.platform.plugin

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
}
