package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.service.PlatformEvent
import org.http4k.websocket.WsMessage

object SyncWebSocketProtocol {
    fun encode(event: PlatformEvent): WsMessage = WsMessage(render(event))

    private fun render(event: PlatformEvent): String =
        when (event) {
            is PlatformEvent.Refresh ->
                """
                <div id="ws-updates" ws-subscribe aria-live="polite" hx-swap-oob="true">
                    <div data-refresh-target="${event.target.panelId}" hx-on::load="htmx.trigger(document.body, 'refresh')"></div>
                </div>
                """
                    .trimIndent()
        }
}
