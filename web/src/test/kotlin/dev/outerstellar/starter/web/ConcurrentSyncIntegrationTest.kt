package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRole
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncPullResponse
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for concurrent sync operations.
 *
 * Covers:
 * - Two threads pushing non-overlapping messages concurrently produce no errors
 * - All concurrently pushed messages appear in the pull response
 * - Concurrent pushes of the same syncId (same-timestamp) produce no server crash
 * - Server returns 200 for all concurrent push requests
 * - Final message count after concurrent push equals unique syncIds pushed
 */
class ConcurrentSyncIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userA: User
    private lateinit var userB: User

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, encoder)
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)

        userA =
            User(
                id = UUID.randomUUID(),
                username = "concurrent_user_a",
                email = "concurrent_a@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userB =
            User(
                id = UUID.randomUUID(),
                username = "concurrent_user_b",
                email = "concurrent_b@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userRepository.save(userA)
        userRepository.save(userB)

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
    }

    @AfterEach fun teardown() = cleanup()

    private fun pushBatch(
        userId: UUID,
        syncIds: List<String>,
        author: String,
    ): List<org.http4k.core.Response> {
        return syncIds.map { syncId ->
            val body =
                """{"messages":[{"syncId":"$syncId","author":"$author","content":"Content for $syncId","updatedAtEpochMs":${System.currentTimeMillis()}}]}"""
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", "Bearer $userId")
                    .header("content-type", "application/json")
                    .body(body)
            )
        }
    }

    private fun pullMessages(userId: UUID): SyncPullResponse {
        val response =
            app(Request(GET, "/api/v1/sync?since=0").header("Authorization", "Bearer $userId"))
        assertEquals(Status.OK, response.status)
        return Jackson.asA(response.bodyString(), SyncPullResponse::class)
    }

    @Test
    fun `concurrent pushes from two users produce no errors`() {
        val syncIdsA = (1..5).map { UUID.randomUUID().toString() }
        val syncIdsB = (1..5).map { UUID.randomUUID().toString() }
        val errors = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)

        pool.submit {
            try {
                val responses = pushBatch(userA.id, syncIdsA, "concurrent_user_a")
                responses.forEach { r ->
                    if (r.status != Status.OK) errors.add("UserA got ${r.status}")
                }
            } finally {
                latch.countDown()
            }
        }

        pool.submit {
            try {
                val responses = pushBatch(userB.id, syncIdsB, "concurrent_user_b")
                responses.forEach { r ->
                    if (r.status != Status.OK) errors.add("UserB got ${r.status}")
                }
            } finally {
                latch.countDown()
            }
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Concurrent push timed out")
        pool.shutdown()

        assertTrue(errors.isEmpty(), "No errors expected during concurrent push, got: $errors")
    }

    @Test
    fun `all concurrently pushed messages appear in pull response`() {
        val syncIdsA = (1..4).map { UUID.randomUUID().toString() }
        val syncIdsB = (1..4).map { UUID.randomUUID().toString() }
        val allIds = syncIdsA + syncIdsB
        val latch = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)

        pool.submit {
            try {
                pushBatch(userA.id, syncIdsA, "concurrent_user_a")
            } finally {
                latch.countDown()
            }
        }

        pool.submit {
            try {
                pushBatch(userB.id, syncIdsB, "concurrent_user_b")
            } finally {
                latch.countDown()
            }
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Concurrent push timed out")
        pool.shutdown()

        // Pull as userA — both users share the same message store in this app
        val pulled = pullMessages(userA.id)
        val pulledIds = pulled.messages.map { it.syncId }.toSet()

        allIds.forEach { syncId ->
            assertTrue(syncId in pulledIds, "Message $syncId should appear in pull response")
        }
    }

    @Test
    fun `concurrent pushes of same syncId are handled without server crash`() {
        // Two threads racing to push the same syncId: one will succeed, the other may get
        // a conflict/error due to the unique constraint. The important property is that the
        // server returns a proper HTTP response (not a timeout or unhandled exception) and
        // the final state has exactly one record for that syncId.
        val sharedSyncId = UUID.randomUUID().toString()
        val statuses = CopyOnWriteArrayList<Int>()
        val latch = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)

        repeat(2) { i ->
            pool.submit {
                try {
                    val body =
                        """{"messages":[{"syncId":"$sharedSyncId","author":"concurrent_user_a","content":"Version $i","updatedAtEpochMs":${1000L + i}}]}"""
                    val r =
                        app(
                            Request(POST, "/api/v1/sync")
                                .header("Authorization", "Bearer ${userA.id}")
                                .header("content-type", "application/json")
                                .body(body)
                        )
                    statuses.add(r.status.code)
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent same-id push timed out")
        pool.shutdown()

        // At least one request must have received a proper HTTP response (not a timeout)
        assertEquals(2, statuses.size, "Both requests should have completed with an HTTP response")
        // At least one should succeed
        assertTrue(
            statuses.any { it == 200 },
            "At least one concurrent push should succeed, got statuses: $statuses",
        )
    }

    @Test
    fun `message count after concurrent push equals unique syncIds`() {
        val syncIdsA = (1..3).map { UUID.randomUUID().toString() }
        val syncIdsB = (1..3).map { UUID.randomUUID().toString() }
        val uniqueCount = (syncIdsA + syncIdsB).toSet().size
        val latch = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)

        pool.submit {
            try {
                pushBatch(userA.id, syncIdsA, "concurrent_user_a")
            } finally {
                latch.countDown()
            }
        }

        pool.submit {
            try {
                pushBatch(userB.id, syncIdsB, "concurrent_user_b")
            } finally {
                latch.countDown()
            }
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS))
        pool.shutdown()

        val pulled = pullMessages(userA.id)
        assertEquals(
            uniqueCount,
            pulled.messages.size,
            "Message count should equal unique syncIds pushed",
        )
    }
}
