package io.github.rygel.outerstellar.platform.banner

import java.util.UUID

enum class BannerSeverity {
    CRITICAL,
    WARNING,
    INFO,
    SCHEDULED_MAINTENANCE,
}

data class Banner(
    val id: String,
    val title: String,
    val body: String,
    val severity: BannerSeverity = BannerSeverity.INFO,
    val isDismissible: Boolean = true,
    val dismissUrl: String? = null,
)

interface BannerProvider {
    fun getBanners(userId: UUID, userRole: String): List<Banner>
}
