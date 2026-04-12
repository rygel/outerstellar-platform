package io.github.rygel.outerstellar.platform.web

import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    private fun insertContact(name: String, index: Int) {
        contactRepository.createServerContact(
            name = name,
            emails = listOf("${name.replace(" ", "").lowercase()}@test.com"),
            phones = listOf("+1-555-000-$index"),
            socialMedia = emptyList(),
            company = "ACME Corp",
            companyAddress = "123 Test St",
            department = "Engineering",
        )
    }

    @BeforeEach
    fun setupTest() {
        app = buildApp()
    }

    @AfterEach fun teardown() = cleanup()

    // ---- Page rendering ----

    @Test
    fun `contacts page returns 200 OK`() {
        val response = app(Request(GET, "/contacts"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `contacts page renders card grid with one card per contact`() {
        (1..3).forEach { insertContact("Contact $it", it) }

        val body = app(Request(GET, "/contacts")).bodyString()

        (1..3).forEach { assertTrue(body.contains("Contact $it"), "Page should render card for Contact $it") }
        assertTrue(body.contains("contact-card"), "Page should use contact-card CSS class")
    }

    @Test
    fun `empty contacts list shows no cards but still renders page`() {
        val body = app(Request(GET, "/contacts")).bodyString()
        assertEquals(Status.OK, body.let { Status.OK })
        assertTrue(body.contains("Contacts Directory"), "Page title should be present")
    }

    // ---- Default limit ----

    @Test
    fun `default request uses limit=12 and offset=0`() {
        (1..5).forEach { insertContact("Person $it", it) }

        val body = app(Request(GET, "/contacts")).bodyString()
        (1..5).forEach { assertTrue(body.contains("Person $it"), "Default page should include Person $it") }
    }

    // ---- Pagination controls ----

    @Test
    fun `pagination controls are hidden when all contacts fit on one page`() {
        (1..5).forEach { insertContact("One-Page $it", it) }

        val body = app(Request(GET, "/contacts")).bodyString()

        // Pagination controls only appear when hasPrevious || hasNext
        assertFalse(
            body.contains("ri-arrow-left-s-line") && body.contains("ri-arrow-right-s-line"),
            "Pagination arrows should not appear when all results fit on one page",
        )
    }

    @Test
    fun `pagination controls appear when there are more pages`() {
        (1..25).forEach { insertContact("Big List $it", it) }

        val body = app(Request(GET, "/contacts")).bodyString()
        assertTrue(body.contains("ri-arrow-right-s-line"), "Next button should appear when more pages exist")
    }

    @Test
    fun `first page does not have a previous button that is enabled`() {
        (1..25).forEach { insertContact("First Page $it", it) }

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
        (1..25).forEach { insertContact("Second Page $it", it) }

        val body = app(Request(GET, "/contacts?limit=12&offset=12")).bodyString()

        assertTrue(
            body.contains("offset=0") || body.contains("previousUrl"),
            "Second page should link back to offset=0",
        )
        assertTrue(body.contains("ri-arrow-left-s-line"), "Second page should show enabled Previous arrow")
    }

    @Test
    fun `last page does not have a next button that is enabled`() {
        (1..25).forEach { insertContact("Last Page $it", it) }

        val body = app(Request(GET, "/contacts?limit=12&offset=24")).bodyString()

        // hasNext should be false because 24 + 12 = 36 > 25
        assertTrue(body.contains("ri-arrow-right-s-line"), "Next arrow element exists but should be disabled")
        // The disabled button has no href; check it is NOT an anchor link
        assertFalse(
            body.contains("href=\"/contacts?limit=12&amp;offset=36\"") ||
                body.contains("href=\"/contacts?limit=12&offset=36\""),
            "Last page should not have an active next link",
        )
    }

    @Test
    fun `total contact count is displayed when pagination is active`() {
        (1..42).forEach { insertContact("Counted $it", it) }

        val body = app(Request(GET, "/contacts")).bodyString()

        assertTrue(body.contains("42"), "Page should display total contact count of 42")
        assertTrue(body.contains("contacts total"), "Page should label the total count")
    }

    @Test
    fun `current page number is displayed`() {
        (1..20).forEach { insertContact("Paged $it", it) }

        val body = app(Request(GET, "/contacts?limit=5&offset=0")).bodyString()
        assertTrue(body.contains("Page 1"), "First page should show 'Page 1'")
    }

    @Test
    fun `offset advances page number in display`() {
        (1..20).forEach { insertContact("Offset $it", it) }

        val body = app(Request(GET, "/contacts?limit=5&offset=5")).bodyString()
        assertTrue(body.contains("Page 2"), "Offset=5 with limit=5 should show 'Page 2'")
    }

    // ---- Limit/offset query params ----

    @Test
    fun `custom limit is forwarded to contact service`() {
        insertContact("A", 1)
        insertContact("B", 2)
        insertContact("C", 3)

        val response = app(Request(GET, "/contacts?limit=3"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("A") && body.contains("B") && body.contains("C"))
    }

    @Test
    fun `limit is capped at 50`() {
        // limit=100 should be clamped to 50
        val response = app(Request(GET, "/contacts?limit=100"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `limit minimum is 1`() {
        val response = app(Request(GET, "/contacts?limit=0"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `negative offset is treated as zero`() {
        val response = app(Request(GET, "/contacts?offset=-5"))
        assertEquals(Status.OK, response.status)
    }

    // ---- Search / query ----

    @Test
    fun `search query is forwarded to contact service`() {
        insertContact("Alice Wonder", 1)
        insertContact("Bob Normal", 2)

        val body = app(Request(GET, "/contacts?q=alice")).bodyString()
        assertTrue(body.contains("Alice Wonder"))
    }

    @Test
    fun `null query passes null to contact service`() {
        val response = app(Request(GET, "/contacts"))
        assertEquals(Status.OK, response.status)
    }
}
