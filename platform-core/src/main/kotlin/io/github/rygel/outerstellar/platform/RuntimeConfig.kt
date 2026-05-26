package io.github.rygel.outerstellar.platform

data class RuntimeConfig(
    val hikariMaximumPoolSize: Int = 20,
    val hikariMinimumIdle: Int = 2,
    val hikariIdleTimeoutMs: Long = 300_000,
    val hikariMaxLifetimeMs: Long = 1_800_000,
    val hikariConnectionTimeoutMs: Long = 10_000,
    val hikariLeakDetectionThresholdMs: Long = 60_000,
    val flywayEnabled: Boolean = true,
    val jtePreloadEnabled: Boolean = false,
    val cacheMessageMaxSize: Int = 1_000,
    val cacheMessageExpireMinutes: Int = 10,
    val cacheGravatarMaxSize: Long = 10_000,
    val rateLimitIpCapacity: Int = 10,
    val rateLimitIpRefillPerMinute: Int = 10,
    val rateLimitAccountCapacity: Int = 20,
    val rateLimitAccountWindowMs: Long = 900_000,
)
