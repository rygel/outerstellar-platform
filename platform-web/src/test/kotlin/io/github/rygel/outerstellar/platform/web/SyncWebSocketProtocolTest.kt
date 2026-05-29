package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.service.PlatformEvent
import io.github.rygel.outerstellar.platform.service.RefreshTarget
import kotlin.test.Test
import kotlin.test.assertContains

class SyncWebSocketProtocolTest {
    @Test
    fun `refresh events encode as out-of-band htmx refresh fragments`() {
        val message = SyncWebSocketProtocol.encode(PlatformEvent.Refresh(RefreshTarget.MESSAGE_LIST_PANEL)).bodyString()

        assertContains(message, """id="ws-updates"""")
        assertContains(message, """ws-subscribe""")
        assertContains(message, """hx-swap-oob="true"""")
        assertContains(message, """data-refresh-target="message-list-panel"""")
        assertContains(message, """hx-on::load="htmx.trigger(document.body, 'refresh')"""")
    }
}
