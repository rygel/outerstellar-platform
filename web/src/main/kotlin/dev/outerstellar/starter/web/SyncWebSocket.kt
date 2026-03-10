package dev.outerstellar.starter.web

import dev.outerstellar.starter.service.EventPublisher
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object SyncWebSocket : EventPublisher {
    private val logger = LoggerFactory.getLogger(SyncWebSocket::class.java)
    private val connectedClients = ConcurrentHashMap.newKeySet<Websocket>()

    val handler: WsHandler = { _ ->
        WsResponse { ws: Websocket ->
            connectedClients.add(ws)
            logger.info("New WebSocket client connected. Total clients: ${connectedClients.size}")

            ws.onMessage { msg ->
                logger.info("Received WebSocket message: ${msg.bodyString()}")
            }

            ws.onClose {
                connectedClients.remove(ws)
                logger.info("WebSocket client disconnected. Total clients: ${connectedClients.size}")
            }
        }
    }

    override fun publishRefresh(targetId: String) {
        broadcastRefresh(targetId)
    }

    fun broadcastUpdate(targetId: String, html: String) {
        val payload = """<div id="$targetId" hx-swap-oob="true">$html</div>"""
        val message = WsMessage(payload)
        
        connectedClients.forEach { ws ->
            try {
                ws.send(message)
            } catch (e: Exception) {
                logger.warn("Failed to send WebSocket message to client, removing from list.")
                connectedClients.remove(ws)
            }
        }
    }
    
    fun broadcastRefresh(targetId: String) {
        val payload = """<div id="$targetId" hx-get="/components/message-list" hx-trigger="load" hx-swap="outerHTML"></div>"""
        val message = WsMessage(payload)
        
        connectedClients.forEach { ws ->
            try {
                ws.send(message)
            } catch (e: Exception) {
                connectedClients.remove(ws)
            }
        }
    }
}
