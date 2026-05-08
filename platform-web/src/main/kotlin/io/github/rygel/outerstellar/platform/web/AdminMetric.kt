package io.github.rygel.outerstellar.platform.web

data class AdminMetric(
    val label: String,
    val value: String,
    val trend: String? = null,
)
