package io.github.rygel.outerstellar.platform.web

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.mockk.mockk
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertTrue
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.slf4j.LoggerFactory

// ---------------------------------------------------------------------------
// Baseline persistence model
// ---------------------------------------------------------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class BenchmarkEntry(
    val name: String = "",
    val iterations: Int = 0,
    val p50Ms: Double = 0.0,
    val p95Ms: Double = 0.0,
    val p99Ms: Double = 0.0,
    val maxMs: Double = 0.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BenchmarkBaseline(
    val recordedAt: String = "",
    val javaVersion: String = "",
    val benchmarks: List<BenchmarkEntry> = emptyList(),
) {
    fun byName(): Map<String, BenchmarkEntry> = benchmarks.associateBy { it.name }
}

// ---------------------------------------------------------------------------
// Test class
// ---------------------------------------------------------------------------

/**
 * In-process latency benchmarks for critical HTTP paths.
 *
 * These tests use the full application stack (H2 in-memory + Jooq + HTTP4K) but bypass the network,
 * so they measure routing, serialisation, auth, and DB overhead without TCP noise.
 *
 * **Run with:** `mvn test -pl web -am -Pperformance`
 *
 * Excluded from the regular test suite via `surefire.excluded.groups=performance` in root
 * `pom.xml`.
 *
 * After each run the results are written to `benchmarks/baseline.json` at the project root. Commit
 * that file to capture a new reference point. The next run prints a comparison table so regressions
 * are immediately visible in the log.
 *
 * Absolute P99 thresholds (20–1500 ms depending on the endpoint) catch gross regressions even
 * without a committed baseline. The baseline file is the historical record.
 */
@Tag("performance")
class PerformanceBenchmarkTest : H2WebTest() {

    companion object {
        private const val WARMUP = 10
        private const val ITERATIONS = 200
        private val logger = LoggerFactory.getLogger(PerformanceBenchmarkTest::class.java)
        private val mapper = jacksonObjectMapper().writerWithDefaultPrettyPrinter()

        /** Written by @BeforeAll from the committed baseline.json, if present. */
        private var previousBaseline: BenchmarkBaseline? = null

        /** Accumulated by each @Test method; flushed to disk by @AfterAll. */
        private val collectedReports = mutableMapOf<String, LatencyReport>()

        /** Path of the baseline file relative to the project root. */
        private val baselineFile =
            Path.of(System.getProperty("user.dir"))
                .resolve("../benchmarks/baseline.json")
                .normalize()
                .toFile()

        @BeforeAll
        @JvmStatic
        fun loadPreviousBaseline() {
            collectedReports.clear()
            if (baselineFile.exists()) {
                previousBaseline = jacksonObjectMapper().readValue<BenchmarkBaseline>(baselineFile)
                logger.info(
                    "Loaded previous baseline from {} (recorded {})",
                    baselineFile,
                    previousBaseline!!.recordedAt,
                )
            } else {
                logger.info("No previous baseline found — this run will create {}", baselineFile)
            }
        }

        @AfterAll
        @JvmStatic
        fun writeBaselineAndCompare() {
            if (collectedReports.isEmpty()) return
            printComparisonTable()
            writeBaseline()
        }

        private fun printComparisonTable() {
            val prev = previousBaseline?.byName() ?: return
            val header =
                "%-44s  %8s  %8s  %8s  %8s"
                    .format("benchmark", "prev P99", "curr P99", "delta", "status")
            val divider = "-".repeat(header.length)
            logger.info("Benchmark comparison vs previous baseline:")
            logger.info(divider)
            logger.info(header)
            logger.info(divider)
            for ((name, report) in collectedReports.entries.sortedBy { it.key }) {
                logBenchmarkRow(name, report, prev[name])
            }
            logger.info(divider)
        }

        private fun logBenchmarkRow(
            name: String,
            report: LatencyReport,
            prevEntry: BenchmarkEntry?,
        ) {
            if (prevEntry != null) {
                val delta = report.p99Ms() - prevEntry.p99Ms
                val pct = if (prevEntry.p99Ms > 0) delta / prevEntry.p99Ms * 100 else 0.0
                val status =
                    when {
                        delta > prevEntry.p99Ms -> "REGRESSION"
                        delta > 0 -> "slower"
                        delta < 0 -> "faster"
                        else -> "unchanged"
                    }
                logger.info(
                    "%-44s  %7.2fms  %7.2fms  %+7.2f%%  %s"
                        .format(name, prevEntry.p99Ms, report.p99Ms(), pct, status)
                )
            } else {
                logger.info(
                    "%-44s  %8s  %7.2fms  %8s  new".format(name, "—", report.p99Ms(), "—")
                )
            }
        }

        private fun writeBaseline() {
            baselineFile.parentFile.mkdirs()
            val baseline =
                BenchmarkBaseline(
                    recordedAt = LocalDateTime.now().toString(),
                    javaVersion = System.getProperty("java.version"),
                    benchmarks =
                        collectedReports.values
                            .sortedBy { it.name }
                            .map { r ->
                                BenchmarkEntry(
                                    name = r.name,
                                    iterations = r.count,
                                    p50Ms = r.p50Ms(),
                                    p95Ms = r.p95Ms(),
                                    p99Ms = r.p99Ms(),
                                    maxMs = r.maxMs(),
                                )
                            },
                )
            mapper.writeValue(baselineFile, baseline)
            logger.info("Baseline written to {}", baselineFile.canonicalPath)
        }

        private fun record(report: LatencyReport) {
            collectedReports[report.name] = report
        }
    }

    private lateinit var app: HttpHandler
    private lateinit var bearerToken: String

    @BeforeEach
    fun setupBenchmark() {
        cleanup()

        val userRepository = JooqUserRepository(testDsl)
        val messageRepository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = CaffeineMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(messageRepository, outbox, txManager, cache)
        // logRounds=4 keeps non-BCrypt benchmarks fast; the login benchmark uses its own encoder
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = JooqSessionRepository(testDsl),
            )
        val contactService = mockk<ContactService>(relaxed = true)
        val pageFactory =
            WebPageFactory(messageRepository, messageService, contactService, securityService)

        app =
            app(
                    messageService,
                    contactService,
                    outbox,
                    cache,
                    createRenderer(),
                    pageFactory,
                    testConfig,
                    securityService,
                    userRepository,
                )
                .http!!

        val registerLens = Body.auto<RegisterRequest>().toLens()
        val loginLens = Body.auto<LoginRequest>().toLens()
        val tokenLens = Body.auto<AuthTokenResponse>().toLens()
        app(
            Request(POST, "/api/v1/auth/register")
                .with(registerLens of RegisterRequest("perfuser", "pass123!"))
        )
        val loginResp =
            app(
                Request(POST, "/api/v1/auth/login")
                    .with(loginLens of LoginRequest("perfuser", "pass123!"))
            )
        bearerToken = tokenLens(loginResp).token
    }

    @AfterEach fun teardownBenchmark() = cleanup()

    // ---------------------------------------------------------------------------
    // Benchmarks
    // ---------------------------------------------------------------------------

    /** Baseline: no auth, single JSON object. Should be effectively free. */
    @Test
    fun `GET health latency`() {
        val req = Request(GET, "/health")
        val rec = LatencyRecorder("GET /health")
        repeat(WARMUP) { app(req) }
        repeat(ITERATIONS) { rec.record { app(req) } }
        val report = rec.report()
        logger.info("{}", report)
        record(report)
        assertTrue(report.p99Ms() < 20.0, "P99 should be < 20ms, was %.2fms".format(report.p99Ms()))
    }

    /**
     * Session lookup path: SHA-256 hash, DB lookup, sliding-window expiry update, user hydration.
     * This is the hot path executed on every authenticated API request.
     */
    @Test
    fun `GET profile latency (session lookup + user read)`() {
        val req =
            Request(GET, "/api/v1/auth/profile").header("Authorization", "Bearer $bearerToken")
        val rec = LatencyRecorder("GET /api/v1/auth/profile")
        repeat(WARMUP) { app(req) }
        repeat(ITERATIONS) { rec.record { app(req) } }
        val report = rec.report()
        logger.info("{}", report)
        record(report)
        assertTrue(report.p99Ms() < 50.0, "P99 should be < 50ms, was %.2fms".format(report.p99Ms()))
    }

    /**
     * Sync pull: authenticated DB read returning all changes since epoch 0. Exercises the session
     * filter + message repository with no Caffeine benefit (sync pull bypasses MessageCache).
     */
    @Test
    fun `GET sync pull latency (changes since 0)`() {
        val req =
            Request(GET, "/api/v1/sync?since=0").header("Authorization", "Bearer $bearerToken")
        val rec = LatencyRecorder("GET /api/v1/sync?since=0")
        repeat(WARMUP) { app(req) }
        repeat(ITERATIONS) { rec.record { app(req) } }
        val report = rec.report()
        logger.info("{}", report)
        record(report)
        assertTrue(report.p99Ms() < 50.0, "P99 should be < 50ms, was %.2fms".format(report.p99Ms()))
    }

    /**
     * Sync push: single-message upsert. Exercises session lookup + message upsert + cache
     * invalidation + outbox write within a single request.
     */
    @Test
    fun `POST sync push latency (single message upsert)`() {
        val payload =
            """{"messages":[{"syncId":"perf-bench-msg","author":"perf",""" +
                """"content":"benchmark payload","updatedAtEpochMs":1000,"deleted":false}]}"""
        val req =
            Request(POST, "/api/v1/sync")
                .header("Authorization", "Bearer $bearerToken")
                .header("Content-Type", "application/json")
                .body(payload)
        val rec = LatencyRecorder("POST /api/v1/sync (1 message)")
        repeat(WARMUP) { app(req) }
        repeat(ITERATIONS) { rec.record { app(req) } }
        val report = rec.report()
        logger.info("{}", report)
        record(report)
        assertTrue(
            report.p99Ms() < 100.0,
            "P99 should be < 100ms, was %.2fms".format(report.p99Ms()),
        )
    }

    /**
     * Cache effectiveness: measures the latency difference between a cold message-list request
     * (cache invalidated) and a warm one (Caffeine hit). Both variants are recorded in the baseline
     * so the warm/cold ratio is tracked over time.
     */
    @Test
    fun `message list cache warm vs cold`() {
        val req =
            Request(GET, "/api/v1/sync?since=0").header("Authorization", "Bearer $bearerToken")

        val coldRec = LatencyRecorder("GET /api/v1/sync?since=0 (cold)")
        repeat(WARMUP) { app(req) }
        repeat(ITERATIONS) {
            testDsl.execute("TRUNCATE TABLE SYNC_STATE")
            coldRec.record { app(req) }
        }

        val warmRec = LatencyRecorder("GET /api/v1/sync?since=0 (warm)")
        repeat(WARMUP) { app(req) }
        repeat(ITERATIONS) { warmRec.record { app(req) } }

        val cold = coldRec.report()
        val warm = warmRec.report()
        logger.info("{}", cold)
        logger.info("{}", warm)
        record(cold)
        record(warm)
        assertTrue(
            warm.p99Ms() <= cold.p50Ms() * 3.0,
            "Warm P99 (%.2fms) should be within 3× of cold P50 (%.2fms)"
                .format(warm.p99Ms(), cold.p50Ms()),
        )
    }

    /**
     * BCrypt login latency at production strength (logRounds=10). Intentionally uses a separate
     * SecurityService instance so it doesn't affect the main app or other benchmarks.
     *
     * Runs fewer iterations because each call takes ~100 ms.
     */
    @Test
    fun `POST login latency (BCrypt logRounds=10)`() {
        val prodEncoder = BCryptPasswordEncoder(logRounds = 10)
        val userRepository = JooqUserRepository(testDsl)
        val prodSecurityService =
            SecurityService(
                userRepository,
                prodEncoder,
                sessionRepository = JooqSessionRepository(testDsl),
            )

        userRepository.save(
            io.github.rygel.outerstellar.platform.security.User(
                id = java.util.UUID.randomUUID(),
                username = "prodperfuser",
                email = "prodperf@example.com",
                passwordHash = prodEncoder.encode("prodpass123!"),
                role = io.github.rygel.outerstellar.platform.security.UserRole.USER,
            )
        )

        val loginLens = Body.auto<LoginRequest>().toLens()
        val contactService = mockk<ContactService>(relaxed = true)
        val messageRepository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = CaffeineMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(messageRepository, outbox, txManager, cache)
        val pageFactory =
            WebPageFactory(messageRepository, messageService, contactService, prodSecurityService)

        val prodApp =
            app(
                    messageService,
                    contactService,
                    outbox,
                    cache,
                    createRenderer(),
                    pageFactory,
                    testConfig,
                    prodSecurityService,
                    userRepository,
                )
                .http!!

        val req =
            Request(POST, "/api/v1/auth/login")
                .with(loginLens of LoginRequest("prodperfuser", "prodpass123!"))

        val rec = LatencyRecorder("POST /api/v1/auth/login (BCrypt rounds=10)")
        repeat(3) { prodApp(req) }
        repeat(10) { rec.record { prodApp(req) } }
        val report = rec.report()
        logger.info("{}", report)
        record(report)
        assertTrue(
            report.p99Ms() < 1500.0,
            "Login P99 should be < 1500ms, was %.2fms".format(report.p99Ms()),
        )
    }
}
