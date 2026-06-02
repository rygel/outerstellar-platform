package com.example.outerstellar.starter.extension

import io.github.rygel.outerstellar.platform.extension.ExtensionContract
import kotlin.test.Test
import kotlin.test.assertEquals

class StarterExtensionContractTest {
    @Test
    fun `starter scaffold publishes predictable diagnostics`() {
        val diagnostics = ExtensionContract.diagnostics(StarterPlatformExtension(), starterPlatformExtensionTestContext())

        assertEquals("starter", diagnostics.extensionId)
        assertEquals("Starter App", diagnostics.appLabel)
        assertEquals(listOf("/", "/starter/health", "/starter/me"), diagnostics.routes.map { it.pathPattern })
        assertEquals(3, diagnostics.capabilities.single { it.id == "routes" }.count)
        assertEquals(1, diagnostics.capabilities.single { it.id == "navigation" }.count)
    }
}
