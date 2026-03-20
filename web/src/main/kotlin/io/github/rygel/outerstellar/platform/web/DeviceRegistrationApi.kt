package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.DeviceToken
import io.github.rygel.outerstellar.platform.security.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.security.SecurityRules
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Jackson
import org.slf4j.LoggerFactory

/**
 * REST API for managing device push-notification tokens.
 *
 * All routes require a valid bearer token (authenticated user).
 *
 * POST /api/v1/devices/register — register or refresh a device token DELETE
 * /api/v1/devices/register — deregister a token by value (body: { "token": "..." })
 */
class DeviceRegistrationApi(private val deviceTokenRepository: DeviceTokenRepository) :
    ServerRoutes {

    private val logger = LoggerFactory.getLogger(DeviceRegistrationApi::class.java)

    override val routes =
        listOf(
            "/api/v1/devices/register" meta
                {
                    summary = "Register a device push-notification token"
                } bindContract
                POST to
                { request: Request ->
                    val user =
                        request.getSecurityUser()
                            ?: return@to Response(Status.UNAUTHORIZED)
                                .body("Authentication required")

                    @Suppress("TooGenericExceptionCaught")
                    val body =
                        try {
                            Jackson.asA(request.bodyString(), RegisterDeviceRequest::class)
                        } catch (e: Exception) {
                            return@to Response(Status.BAD_REQUEST).body("Invalid request body")
                        }

                    if (body.platform !in SUPPORTED_PLATFORMS) {
                        return@to Response(Status.BAD_REQUEST)
                            .body("platform must be one of: ${SUPPORTED_PLATFORMS.joinToString()}")
                    }
                    if (body.token.isBlank()) {
                        return@to Response(Status.BAD_REQUEST).body("token is required")
                    }

                    val deviceToken =
                        DeviceToken(
                            id = 0L,
                            userId = user.id,
                            platform = body.platform,
                            token = body.token,
                            appBundle = body.appBundle,
                        )
                    deviceTokenRepository.upsert(deviceToken)
                    logger.info(
                        "Device token registered for user={} platform={}",
                        user.username,
                        body.platform,
                    )

                    Response(Status.NO_CONTENT)
                },
            "/api/v1/devices/register" meta
                {
                    summary = "Deregister a device push-notification token"
                } bindContract
                DELETE to
                { request: Request ->
                    val user =
                        request.getSecurityUser()
                            ?: return@to Response(Status.UNAUTHORIZED)
                                .body("Authentication required")

                    @Suppress("TooGenericExceptionCaught")
                    val token =
                        try {
                            Jackson.asA(request.bodyString(), DeregisterDeviceRequest::class).token
                        } catch (e: Exception) {
                            request.query("token")
                        } ?: return@to Response(Status.BAD_REQUEST).body("token is required")

                    deviceTokenRepository.delete(token)
                    logger.info("Device token deregistered for user={}", user.username)

                    Response(Status.NO_CONTENT)
                },
        )

    private fun Request.getSecurityUser() =
        try {
            SecurityRules.USER_KEY(this)
        } catch (e: Exception) {
            null
        }

    companion object {
        private val SUPPORTED_PLATFORMS = setOf("android", "ios")
    }
}

data class RegisterDeviceRequest(
    val platform: String,
    val token: String,
    val appBundle: String? = null,
)

data class DeregisterDeviceRequest(val token: String)
