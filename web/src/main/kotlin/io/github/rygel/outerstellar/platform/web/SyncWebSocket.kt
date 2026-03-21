package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.UserRepository
import org.http4k.core.Request
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse
import org.http4k.websocket.WsStatus
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val WS_AUTH_REQUIRED_STATUS = 4401

class SyncWebSocket(private val userRepository: UserRepository) :
    io.github.rygel.outerstellar.platform.service.EventPublisher {
    private val logger = LoggerFactory.getLogger(SyncWebSocket::class.java)
    private val connections = ConcurrentHashMap.newKeySet<Websocket>()

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
                        userRepository.findById(UUID.fromString(it))
                    } catch (e: IllegalArgumentException) {
                        logger.debug("Invalid session cookie UUID: {}", e.message)
                        null
                    }
                }
            if (user == null) {
                logger.warn("WebSocket connection rejected: no valid session")
                ws.close(WsStatus(WS_AUTH_REQUIRED_STATUS, "Authentication required"))
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
}
