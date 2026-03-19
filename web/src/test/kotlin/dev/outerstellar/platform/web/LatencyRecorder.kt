package dev.outerstellar.platform.web

import kotlin.math.ceil

/**
 * Collects wall-clock latency samples (nanosecond precision) and computes percentiles.
 *
 * Usage:
 * ```
 * val rec = LatencyRecorder("GET /health")
 * repeat(100) { rec.record { app(request) } }
 * val report = rec.report()
 * logger.info("{}", report)
 * assertTrue(report.p99Ms() < 20.0)
 * ```
 */
class LatencyRecorder(val name: String) {
    private val samples = mutableListOf<Long>()

    fun record(block: () -> Unit) {
        val start = System.nanoTime()
        block()
        samples += System.nanoTime() - start
    }

    fun report(): LatencyReport {
        check(samples.isNotEmpty()) { "No samples recorded for '$name'" }
        val sorted = samples.sorted()
        return LatencyReport(
            name = name,
            count = sorted.size,
            p50 = percentile(sorted, 50),
            p95 = percentile(sorted, 95),
            p99 = percentile(sorted, 99),
            max = sorted.last(),
        )
    }

    private fun percentile(sorted: List<Long>, pct: Int): Long {
        val idx = (ceil(sorted.size * pct / 100.0) - 1).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}

data class LatencyReport(
    val name: String,
    val count: Int,
    /** Nanoseconds at the 50th percentile. */
    val p50: Long,
    /** Nanoseconds at the 95th percentile. */
    val p95: Long,
    /** Nanoseconds at the 99th percentile. */
    val p99: Long,
    /** Maximum observed nanoseconds. */
    val max: Long,
) {
    fun p50Ms() = p50 / 1_000_000.0

    fun p95Ms() = p95 / 1_000_000.0

    fun p99Ms() = p99 / 1_000_000.0

    fun maxMs() = max / 1_000_000.0

    override fun toString() =
        "%-44s  n=%-4d  p50=%6.2fms  p95=%6.2fms  p99=%6.2fms  max=%6.2fms"
            .format(name, count, p50Ms(), p95Ms(), p99Ms(), maxMs())
}
