package dev.outerstellar.starter.web

import org.http4k.core.Request
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object SyncWebSocket : WebComponent<Nothing>, dev.outerstellar.starter.service.EventPublisher {
    private val logger = LoggerFactory.getLogger(SyncWebSocket::class.java)
    private val connections = ConcurrentHashMap.newKeySet<Websocket>()

    val handler: WsHandler = { _: Request ->
        WsResponse { ws: Websocket ->
            connections.add(ws)
            logger.info("New WebSocket connection established. Total: {}", connections.size)

            ws.onMessage { msg ->
                logger.debug("Received message: {}", msg.bodyString())
            }

            ws.onClose {
                connections.remove(ws)
                logger.info("WebSocket connection closed. Total: {}", connections.size)
            }

            ws.onError { e ->
                logger.error("WebSocket error: {}", e.message)
                connections.remove(ws)
            }
        }
    }

    override fun publishRefresh(targetId: String) {
        val message = WsMessage("refresh:$targetId")
        connections.forEach { ws ->
            try {
                ws.send(message)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.warn("Failed to send refresh message to websocket: {}", e.message)
                connections.remove(ws)
            }
        }
    }

    override fun build(ctx: WebContext, vararg args: Any?): Nothing {
        throw UnsupportedOperationException("SyncWebSocket is a handler, not a view component")
    }
}
