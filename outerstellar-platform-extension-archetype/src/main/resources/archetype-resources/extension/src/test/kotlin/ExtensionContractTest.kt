#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.extension

import io.github.rygel.outerstellar.platform.extension.ExtensionContract
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionContractTest {
    @Test
    fun `extension publishes predictable diagnostics`() {
        val context = testHostContext()
        val diagnostics = ExtensionContract.diagnostics(ExtensionPlatformExtension(), context)

        assertEquals("${extensionId}", diagnostics.extensionId)
        assertEquals("${appLabel}", diagnostics.appLabel)
        assertEquals(listOf("/", "/${extensionId}/health", "/${extensionId}/me"), diagnostics.routes.map { it.pathPattern })
        assertEquals(3, diagnostics.capabilities.single { it.id == "routes" }.count)
        assertEquals(1, diagnostics.capabilities.single { it.id == "navigation" }.count)
    }
}
