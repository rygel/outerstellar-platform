package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for poll API and HTMX component routes.
 *
 * API tests:
 * - POST /api/v1/polls creates poll and returns 201
 * - POST /api/v1/polls returns 401 without auth
 * - POST /api/v1/polls returns 400 with too few options
 * - GET /api/v1/polls/{syncId} returns poll with results
 * - GET /api/v1/polls/{syncId} returns 404 for non-existent
 * - POST /api/v1/polls/{syncId}/vote casts vote and returns 200
 * - POST /api/v1/polls/{syncId}/vote returns 401 without auth
 * - DELETE /api/v1/polls/{syncId}/vote removes vote
 * - POST /api/v1/polls/{syncId}/close closes poll
 * - POST /api/v1/polls/{syncId}/close returns 403 for non-creator
 * - DELETE /api/v1/polls/{syncId} deletes poll
 * - GET /api/v1/polls lists open polls
 *
 * Component tests:
 * - GET /components/polls/{syncId} returns poll card fragment
 * - POST /components/polls/{syncId}/vote votes via HTMX
 */
class PollIntegrationTest : WebTest() {

    @Serializable private data class PollOptionDto(val id: Long, val position: Int, val text: String)

    @Serializable
    private data class PollResultsDto(
        val syncId: String,
        val question: String,
        val multiChoice: Boolean,
        val closed: Boolean,
        val deadline: String? = null,
        val options: List<PollOptionDto>,
        val voteCounts: Map<Long, Int>,
        val totalVotes: Int,
        val userVotedOptionIds: Set<Long> = emptySet(),
    )

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var securityService: SecurityService
    private lateinit var sessionToken: String

    private val pollResultsLens = Body.auto<PollResultsDto>().toLens()

    @BeforeEach
    fun setupTest() {
        testUser =
            User(
                id = UUID.randomUUID(),
                username = "polluser",
                email = "poll@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(testUser)

        securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )
        sessionToken = securityService.createSession(testUser.id)

        app = buildApp(securityService = securityService)
    }

    @AfterEach fun teardown() = cleanup()

    private fun sessionCookie() = Cookie(WebContext.SESSION_COOKIE, sessionToken)

    private fun createPollViaApi(
        question: String = "Test question?",
        options: List<String> = listOf("Option A", "Option B"),
    ): PollResultsDto {
        val body = """{"question":"$question","options":${options.joinToString(",", "[", "]") { "\"$it\"" }}}"""
        val response =
            app(
                Request(POST, "/api/v1/polls")
                    .header("content-type", "application/json")
                    .body(body)
                    .cookie(sessionCookie())
            )
        assertEquals(Status.CREATED, response.status, "Setup: creating poll should succeed")
        return pollResultsLens(response)
    }

    @Test
    fun `POST api-polls creates poll and returns 201`() {
        val response =
            app(
                Request(POST, "/api/v1/polls")
                    .header("content-type", "application/json")
                    .body("""{"question":"What?","options":["A","B"]}""")
                    .cookie(sessionCookie())
            )
        assertEquals(Status.CREATED, response.status)
        val result = pollResultsLens(response)
        assertEquals("What?", result.question)
        assertEquals(2, result.options.size)
    }

    @Test
    fun `POST api-polls returns 401 without auth`() {
        val response =
            app(
                Request(POST, "/api/v1/polls")
                    .header("content-type", "application/json")
                    .body("""{"question":"What?","options":["A","B"]}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST api-polls returns 400 with too few options`() {
        val response =
            app(
                Request(POST, "/api/v1/polls")
                    .header("content-type", "application/json")
                    .body("""{"question":"What?","options":["Only one"]}""")
                    .cookie(sessionCookie())
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `GET api-polls-syncId returns poll with results`() {
        val created = createPollViaApi()
        val response = app(Request(GET, "/api/v1/polls/${created.syncId}").cookie(sessionCookie()))
        assertEquals(Status.OK, response.status)
        val result = pollResultsLens(response)
        assertEquals(created.syncId, result.syncId)
        assertEquals(2, result.options.size)
    }

    @Test
    fun `GET api-polls-syncId returns 404 for non-existent`() {
        val response = app(Request(GET, "/api/v1/polls/non-existent-id").cookie(sessionCookie()))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `POST api-polls-syncId-vote casts vote and returns 200`() {
        val created = createPollViaApi()
        val optionId = created.options.first().id
        val response =
            app(
                Request(POST, "/api/v1/polls/${created.syncId}/vote")
                    .header("content-type", "application/json")
                    .body("""{"optionId":$optionId}""")
                    .cookie(sessionCookie())
            )
        assertEquals(Status.OK, response.status)
        val result = pollResultsLens(response)
        assertTrue((result.voteCounts[optionId] ?: 0) > 0)
    }

    @Test
    fun `POST api-polls-syncId-vote returns 401 without auth`() {
        val created = createPollViaApi()
        val optionId = created.options.first().id
        val response =
            app(
                Request(POST, "/api/v1/polls/${created.syncId}/vote")
                    .header("content-type", "application/json")
                    .body("""{"optionId":$optionId}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `DELETE api-polls-syncId-vote removes vote`() {
        val created = createPollViaApi()
        val optionId = created.options.first().id

        app(
            Request(POST, "/api/v1/polls/${created.syncId}/vote")
                .header("content-type", "application/json")
                .body("""{"optionId":$optionId}""")
                .cookie(sessionCookie())
        )

        val response =
            app(Request(DELETE, "/api/v1/polls/${created.syncId}/vote?optionId=$optionId").cookie(sessionCookie()))
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `POST api-polls-syncId-close closes poll`() {
        val created = createPollViaApi()
        val response = app(Request(POST, "/api/v1/polls/${created.syncId}/close").cookie(sessionCookie()))
        assertEquals(Status.OK, response.status)

        val getResponse = app(Request(GET, "/api/v1/polls/${created.syncId}").cookie(sessionCookie()))
        val result = pollResultsLens(getResponse)
        assertTrue(result.closed)
    }

    @Test
    fun `POST api-polls-syncId-close returns 403 for non-creator`() {
        val created = createPollViaApi()

        val otherUser =
            User(
                id = UUID.randomUUID(),
                username = "otheruser",
                email = "other@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(otherUser)
        val otherToken = securityService.createSession(otherUser.id)

        val response =
            app(
                Request(POST, "/api/v1/polls/${created.syncId}/close")
                    .cookie(Cookie(WebContext.SESSION_COOKIE, otherToken))
            )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `DELETE api-polls-syncId deletes poll`() {
        val created = createPollViaApi()
        val response = app(Request(DELETE, "/api/v1/polls/${created.syncId}").cookie(sessionCookie()))
        assertEquals(Status.NO_CONTENT, response.status)

        val getResponse = app(Request(GET, "/api/v1/polls/${created.syncId}").cookie(sessionCookie()))
        assertEquals(Status.NOT_FOUND, getResponse.status)
    }

    @Test
    fun `GET api-polls lists open polls`() {
        createPollViaApi("List test question?")
        val response = app(Request(GET, "/api/v1/polls").cookie(sessionCookie()))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("List test question?"), "Poll list should contain the created poll question")
    }

    @Test
    fun `GET components-polls-syncId returns poll card fragment`() {
        val created = createPollViaApi()
        val response = app(Request(GET, "/components/polls/${created.syncId}").cookie(sessionCookie()))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("Test question?"), "Fragment should contain poll question")
        assertTrue(body.contains("poll-card"), "Fragment should use poll-card CSS class")
    }

    @Test
    fun `POST components-polls-syncId-vote votes via HTMX`() {
        val created = createPollViaApi()
        val optionId = created.options.first().id
        val response =
            app(
                Request(POST, "/components/polls/${created.syncId}/vote")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body("optionId=$optionId")
                    .cookie(sessionCookie())
            )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("poll-card"), "Response should be a poll card fragment")
    }
}
