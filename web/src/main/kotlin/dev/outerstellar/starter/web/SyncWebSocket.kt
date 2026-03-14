package dev.outerstellar.starter.web

import java.util.concurrent.ConcurrentHashMap
import org.http4k.core.Request
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse
import org.slf4j.LoggerFactory

object SyncWebSocket : WebComponent<Nothing>, dev.outerstellar.starter.service.EventPublisher {
    private val logger = LoggerFactory.getLogger(SyncWebSocket::class.java)
    private val connections = ConcurrentHashMap.newKeySet<Websocket>()

    var userRepository: dev.outerstellar.starter.security.UserRepository? = null

    val handler: WsHandler = { request: Request ->
        WsResponse { ws: Websocket ->
            val sessionCookie =
                request
                    .header("Cookie")
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.find { it.startsWith("${WebContext.SESSION_COOKIE}=") }
                    ?.substringAfter("=")
            val user =
                sessionCookie?.let {
                    try {
                        userRepository?.findById(java.util.UUID.fromString(it))
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            if (user == null) {
                logger.warn("WebSocket connection rejected: no valid session")
                ws.close(org.http4k.websocket.WsStatus(4401, "Authentication required"))
                return@WsResponse
            }

            connections.add(ws)
            logger.info(
                "WebSocket connection established for user {}. Total: {}",
                user.username,
                connections.size,
            )

            ws.onMessage { msg -> logger.debug("Received message: {}", msg.bodyString()) }

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
        val failed = mutableListOf<Websocket>()
        connections.forEach { ws ->
            try {
                ws.send(message)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.warn("Failed to send refresh message to websocket: {}", e.message)
                failed += ws
            }
        }
        connections.removeAll(failed.toSet())
    }

    override fun build(ctx: WebContext, vararg args: Any?): Nothing {
        throw UnsupportedOperationException("SyncWebSocket is a handler, not a view component")
    }
}
