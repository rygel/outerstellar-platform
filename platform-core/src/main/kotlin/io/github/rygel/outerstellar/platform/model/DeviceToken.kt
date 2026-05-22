package io.github.rygel.outerstellar.platform.model

import java.util.UUID

data class DeviceToken(val id: Long, val userId: UUID, val platform: String, val token: String, val appBundle: String?)
