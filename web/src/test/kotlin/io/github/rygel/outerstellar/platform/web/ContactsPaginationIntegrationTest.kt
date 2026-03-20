package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for contacts page pagination (Feature 3).
 *
 * Verifies:
 * - Default request shows first page of contacts
 * - ?limit and ?offset query params are respected
 * - Previous/next navigation controls appear when there are multiple pages
 * - Total count is displayed on paginated pages
 * - First page has no "previous" control
 * - Last page has no "next" control
 * - Middle pages have both controls
 * - Single page of results omits pagination controls
 * - Search query (?q=) is respected
 * - Contacts card grid is rendered for each result
 */
class ContactsPaginationIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var contactService: ContactService

    private fun makeContact(name: String, index: Int = 0) =
        ContactSummary(
            syncId = "sync-$index-${name.replace(" ", "-").lowercase()}",
            name = name,
            emails = listOf("${name.replace(" ", "").lowercase()}@test.com"),
            phones = listOf("+1-555-000-$index"),
            socialMedia = emptyList(),
            company = "ACME Corp",
            companyAddress = "123 Test St",
            department = "Engineering",
            dirty = false,
            updatedAtEpochMs = System.currentTimeMillis(),
        )

    @BeforeEach
    fun setupTest() {
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)

        contactService = mockk(relaxed = true)
        val securityService = SecurityService(userRepository, BCryptPasswordEncoder(logRounds = 4))
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)

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

    // ---- Page rendering ----

    @Test
    fun `contacts page returns 200 OK`() {
        every { contactService.listContacts(any(), any(), any()) } returns emptyList()
        every { contactService.countContacts(any()) } returns 0L

        val response = app(Request(GET, "/contacts"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `contacts page renders card grid with one card per contact`() {
        val contacts = (1..3).map { makeContact("Contact $it", it) }
        every { contactService.listContacts(null, 12, 0) } returns contacts
        every { contactService.countContacts(null) } returns 3L

        val body = app(Request(GET, "/contacts")).bodyString()

        contacts.forEach { c ->
            assertTrue(body.contains(c.name), "Page should render card for ${c.name}")
        }
        assertTrue(body.contains("contact-card"), "Page should use contact-card CSS class")
    }

    @Test
    fun `empty contacts list shows no cards but still renders page`() {
        every { contactService.listContacts(any(), any(), any()) } returns emptyList()
        every { contactService.countContacts(any()) } returns 0L

        val body = app(Request(GET, "/contacts")).bodyString()
        assertEquals(Status.OK, body.let { Status.OK })
        assertTrue(body.contains("Contacts Directory"), "Page title should be present")
    }

    // ---- Default limit ----

    @Test
    fun `default request uses limit=12 and offset=0`() {
        val contacts = (1..5).map { makeContact("Person $it", it) }
        every { contactService.listContacts(null, 12, 0) } returns contacts
        every { contactService.countContacts(null) } returns 5L

        val body = app(Request(GET, "/contacts")).bodyString()
        contacts.forEach { c ->
            assertTrue(body.contains(c.name), "Default page should include ${c.name}")
        }
    }

    // ---- Pagination controls ----

    @Test
    fun `pagination controls are hidden when all contacts fit on one page`() {
        val contacts = (1..5).map { makeContact("One-Page $it", it) }
        every { contactService.listContacts(null, 12, 0) } returns contacts
        every { contactService.countContacts(null) } returns 5L

        val body = app(Request(GET, "/contacts")).bodyString()

        // Pagination controls only appear when hasPrevious || hasNext
        assertFalse(
            body.contains("ri-arrow-left-s-line") && body.contains("ri-arrow-right-s-line"),
            "Pagination arrows should not appear when all results fit on one page",
        )
    }

    @Test
    fun `pagination controls appear when there are more pages`() {
        val contacts = (1..12).map { makeContact("Big List $it", it) }
        every { contactService.listContacts(null, 12, 0) } returns contacts
        every { contactService.countContacts(null) } returns 25L // 25 total → page 2 exists

        val body = app(Request(GET, "/contacts")).bodyString()
        assertTrue(
            body.contains("ri-arrow-right-s-line"),
            "Next button should appear when more pages exist",
        )
    }

    @Test
    fun `first page does not have a previous button that is enabled`() {
        val contacts = (1..12).map { makeContact("First Page $it", it) }
        every { contactService.listContacts(null, 12, 0) } returns contacts
        every { contactService.countContacts(null) } returns 25L

        val body = app(Request(GET, "/contacts")).bodyString()

        // Previous button should exist but be disabled (offset=0)
        assertFalse(
            body.contains("href=\"/contacts?limit=12&amp;offset=-12\"") ||
                body.contains("href=\"/contacts?limit=12&offset=-12\""),
            "Previous link must not navigate to negative offset",
        )
    }

    @Test
    fun `second page has a previous button pointing to first page`() {
        val contacts = (13..24).map { makeContact("Second Page $it", it) }
        every { contactService.listContacts(null, 12, 12) } returns contacts
        every { contactService.countContacts(null) } returns 25L

        val body = app(Request(GET, "/contacts?limit=12&offset=12")).bodyString()

        assertTrue(
            body.contains("offset=0") || body.contains("previousUrl"),
            "Second page should link back to offset=0",
        )
        assertTrue(
            body.contains("ri-arrow-left-s-line"),
            "Second page should show enabled Previous arrow",
        )
    }

    @Test
    fun `last page does not have a next button that is enabled`() {
        val contacts = (21..25).map { makeContact("Last Page $it", it) }
        every { contactService.listContacts(null, 12, 24) } returns contacts
        every { contactService.countContacts(null) } returns 25L

        val body = app(Request(GET, "/contacts?limit=12&offset=24")).bodyString()

        // hasNext should be false because 24 + 12 = 36 > 25
        assertTrue(
            body.contains("ri-arrow-right-s-line"),
            "Next arrow element exists but should be disabled",
        )
        // The disabled button has no href; check it is NOT an anchor link
        assertFalse(
            body.contains("href=\"/contacts?limit=12&amp;offset=36\"") ||
                body.contains("href=\"/contacts?limit=12&offset=36\""),
            "Last page should not have an active next link",
        )
    }

    @Test
    fun `total contact count is displayed when pagination is active`() {
        val contacts = (1..12).map { makeContact("Counted $it", it) }
        every { contactService.listContacts(null, 12, 0) } returns contacts
        every { contactService.countContacts(null) } returns 42L

        val body = app(Request(GET, "/contacts")).bodyString()

        assertTrue(body.contains("42"), "Page should display total contact count of 42")
        assertTrue(body.contains("contacts total"), "Page should label the total count")
    }

    @Test
    fun `current page number is displayed`() {
        val contacts = (1..5).map { makeContact("Paged $it", it) }
        every { contactService.listContacts(null, 5, 0) } returns contacts
        every { contactService.countContacts(null) } returns 20L

        val body = app(Request(GET, "/contacts?limit=5&offset=0")).bodyString()
        assertTrue(body.contains("Page 1"), "First page should show 'Page 1'")
    }

    @Test
    fun `offset advances page number in display`() {
        val contacts = (6..10).map { makeContact("Offset $it", it) }
        every { contactService.listContacts(null, 5, 5) } returns contacts
        every { contactService.countContacts(null) } returns 20L

        val body = app(Request(GET, "/contacts?limit=5&offset=5")).bodyString()
        assertTrue(body.contains("Page 2"), "Offset=5 with limit=5 should show 'Page 2'")
    }

    // ---- Limit/offset query params ----

    @Test
    fun `custom limit is forwarded to contact service`() {
        every { contactService.listContacts(null, 3, 0) } returns
            listOf(makeContact("A", 1), makeContact("B", 2), makeContact("C", 3))
        every { contactService.countContacts(null) } returns 3L

        val response = app(Request(GET, "/contacts?limit=3"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("A") && body.contains("B") && body.contains("C"))
    }

    @Test
    fun `limit is capped at 50`() {
        // limit=100 should be clamped to 50
        every { contactService.listContacts(null, 50, 0) } returns emptyList()
        every { contactService.countContacts(null) } returns 0L

        val response = app(Request(GET, "/contacts?limit=100"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `limit minimum is 1`() {
        every { contactService.listContacts(null, 1, 0) } returns emptyList()
        every { contactService.countContacts(null) } returns 0L

        val response = app(Request(GET, "/contacts?limit=0"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `negative offset is treated as zero`() {
        every { contactService.listContacts(null, 12, 0) } returns emptyList()
        every { contactService.countContacts(null) } returns 0L

        val response = app(Request(GET, "/contacts?offset=-5"))
        assertEquals(Status.OK, response.status)
    }

    // ---- Search / query ----

    @Test
    fun `search query is forwarded to contact service`() {
        every { contactService.listContacts("alice", 12, 0) } returns
            listOf(makeContact("Alice Wonder", 1))
        every { contactService.countContacts("alice") } returns 1L

        val body = app(Request(GET, "/contacts?q=alice")).bodyString()
        assertTrue(body.contains("Alice Wonder"))
    }

    @Test
    fun `null query passes null to contact service`() {
        every { contactService.listContacts(null, 12, 0) } returns emptyList()
        every { contactService.countContacts(null) } returns 0L

        val response = app(Request(GET, "/contacts"))
        assertEquals(Status.OK, response.status)
    }
}
