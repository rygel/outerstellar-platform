package io.github.rygel.outerstellar.platform.sync.engine

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

interface ConnectivityChecker {
    val isOnline: Boolean

    fun addObserver(fn: (Boolean) -> Unit)

    fun start()

    fun stop()
}

class HttpConnectivityChecker(
    private val healthUrl: String,
    private val intervalSeconds: Long = 30L,
    private val timeoutSeconds: Long = 5L,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : ConnectivityChecker {
    private val logger = LoggerFactory.getLogger(HttpConnectivityChecker::class.java)

    private val _isOnline = AtomicBoolean(true)
    override val isOnline: Boolean
        get() = _isOnline.get()

    private val observers = mutableListOf<(Boolean) -> Unit>()
    private var scheduler: ScheduledExecutorService? = null

    override fun addObserver(fn: (Boolean) -> Unit) {
        observers.add(fn)
    }

    override fun start() {
        if (scheduler != null) return
        scheduler =
            Executors.newSingleThreadScheduledExecutor { r ->
                    Thread(r, "connectivity-checker").also { it.isDaemon = true }
                }
                .also { s -> s.scheduleAtFixedRate(::check, 0L, intervalSeconds, TimeUnit.SECONDS) }
    }

    override fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    fun check() {
        val online =
            try {
                val request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(healthUrl))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .GET()
                        .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
                response.statusCode() in 200..299
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.debug("Connectivity check failed for {}", healthUrl, e)
                false
            }

        val previous = _isOnline.getAndSet(online)
        if (previous != online) {
            observers.forEach { it(online) }
        }
    }
}
