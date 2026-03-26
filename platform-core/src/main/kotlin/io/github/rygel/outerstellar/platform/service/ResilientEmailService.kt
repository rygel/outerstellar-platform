package io.github.rygel.outerstellar.platform.service

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.slf4j.LoggerFactory
import java.time.Duration

private const val FAILURE_RATE_THRESHOLD = 50f
private const val WAIT_DURATION_SECONDS = 60L
private const val SLIDING_WINDOW_SIZE = 10
private const val PERMITTED_CALLS_IN_HALF_OPEN = 3

/**
 * Wraps an [EmailService] with a Resilience4j circuit breaker. When the SMTP server is down, the circuit opens after
 * [SLIDING_WINDOW_SIZE] calls with a [FAILURE_RATE_THRESHOLD]% failure rate, preventing cascading failures.
 */
class ResilientEmailService(private val delegate: EmailService) : EmailService {
    private val logger = LoggerFactory.getLogger(ResilientEmailService::class.java)

    private val circuitBreaker: CircuitBreaker =
        CircuitBreaker.of(
            "email",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(Duration.ofSeconds(WAIT_DURATION_SECONDS))
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .permittedNumberOfCallsInHalfOpenState(PERMITTED_CALLS_IN_HALF_OPEN)
                .build(),
        )

    override fun send(to: String, subject: String, body: String) {
        try {
            circuitBreaker.executeRunnable { delegate.send(to, subject, body) }
        } catch (e: io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            logger.warn("Email circuit breaker is open — dropping email to {}: {}", to, e.message)
        }
    }
}
