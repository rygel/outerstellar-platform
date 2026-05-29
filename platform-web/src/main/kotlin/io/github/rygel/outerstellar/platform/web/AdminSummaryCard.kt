package io.github.rygel.outerstellar.platform.web

data class AdminSummaryCard(
    val title: String,
    val metrics: List<AdminMetric>,
    val linkUrl: String,
    val linkLabel: String = "View details",
)
