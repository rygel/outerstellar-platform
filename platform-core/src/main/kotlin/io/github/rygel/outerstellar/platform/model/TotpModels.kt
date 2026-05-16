package io.github.rygel.outerstellar.platform.model

import kotlinx.serialization.Serializable

@Serializable data class TotpVerifyRequest(val partialToken: String, val code: String)

@Serializable
data class TotpVerifyResponse(
    val status: String,
    val token: String? = null,
    val username: String? = null,
    val role: String? = null,
)

@Serializable data class TotpSetupResponse(val secret: String, val qrDataUri: String)

@Serializable data class TotpConfirmRequest(val secret: String, val code: String)

@Serializable data class TotpConfirmResponse(val status: String, val backupCodes: List<String>? = null)

@Serializable data class TotpDisableRequest(val password: String)
