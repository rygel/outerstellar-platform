package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.sync.SyncPullContactResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushContactResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.KotlinxSerialization
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for contact detail fields round-trip through sync.
 *
 * Covers:
 * - Emails list survives push → pull round-trip
 * - Phones list survives push → pull round-trip
 * - SocialMedia list survives push → pull round-trip
 * - Company and department fields survive round-trip
 * - Multiple emails/phones in a single contact all persist
 * - Updated contact preserves all detail fields
 * - Deleted flag is respected on push
 */
class ContactDetailsSyncIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var sessionToken: String

    @BeforeEach
    fun setupTest() {
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "contactdetailsuser",
                email = "contactdetails@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        sessionToken = securityService.createSession(testUser.id)

        app = buildApp(securityService = securityService)
    }

    @AfterEach fun teardown() = cleanup()

    private fun bearer() = "Bearer $sessionToken"

    private data class PushContactParams(
        val syncId: String,
        val name: String,
        val emails: List<String> = emptyList(),
        val phones: List<String> = emptyList(),
        val socialMedia: List<String> = emptyList(),
        val company: String = "",
        val companyAddress: String = "",
        val department: String = "",
        val timestamp: Long = 1000L,
        val deleted: Boolean = false,
    )

    private fun pushContact(params: PushContactParams): org.http4k.core.Response {
        val syncId = params.syncId
        val name = params.name
        val emails = params.emails
        val phones = params.phones
        val socialMedia = params.socialMedia
        val company = params.company
        val companyAddress = params.companyAddress
        val department = params.department
        val timestamp = params.timestamp
        val deleted = params.deleted
        val emailsJson = emails.joinToString(",") { "\"$it\"" }
        val phonesJson = phones.joinToString(",") { "\"$it\"" }
        val socialJson = socialMedia.joinToString(",") { "\"$it\"" }
        val body =
            """
            {
              "contacts": [{
                "syncId": "$syncId",
                "name": "$name",
                "emails": [$emailsJson],
                "phones": [$phonesJson],
                "socialMedia": [$socialJson],
                "company": "$company",
                "companyAddress": "$companyAddress",
                "department": "$department",
                "updatedAtEpochMs": $timestamp,
                "deleted": $deleted
              }]
            }
            """
                .trimIndent()
        return app(
            Request(POST, "/api/v1/sync/contacts")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body(body)
        )
    }

    private fun pullContacts(): SyncPullContactResponse {
        val response = app(Request(GET, "/api/v1/sync/contacts?since=0").header("Authorization", bearer()))
        assertEquals(Status.OK, response.status)
        return KotlinxSerialization.asA(response.bodyString(), SyncPullContactResponse::class)
    }

    @Test
    fun `email list survives push-pull round-trip`() {
        val syncId = UUID.randomUUID().toString()
        val emails = listOf("alice@example.com", "alice.work@corp.com")

        val pushResponse = pushContact(PushContactParams(syncId, "Alice", emails = emails))
        assertEquals(Status.OK, pushResponse.status)
        val pushBody = KotlinxSerialization.asA(pushResponse.bodyString(), SyncPushContactResponse::class)
        assertEquals(1, pushBody.appliedCount)

        val pulled = pullContacts()
        val contact = pulled.contacts.find { it.syncId == syncId }
        assertNotNull(contact, "Contact should be present in pull response")
        assertEquals(emails.size, contact.emails.size, "Email count should match")
        assertTrue(contact.emails.containsAll(emails), "All emails should be preserved")
    }

    @Test
    fun `phone list survives push-pull round-trip`() {
        val syncId = UUID.randomUUID().toString()
        val phones = listOf("+1-555-0100", "+1-555-0200")

        pushContact(PushContactParams(syncId, "Bob", phones = phones))

        val pulled = pullContacts()
        val contact = pulled.contacts.find { it.syncId == syncId }
        assertNotNull(contact, "Contact should be present")
        assertEquals(phones.size, contact.phones.size, "Phone count should match")
        assertTrue(contact.phones.containsAll(phones), "All phones should be preserved")
    }

    @Test
    fun `socialMedia list survives push-pull round-trip`() {
        val syncId = UUID.randomUUID().toString()
        val social = listOf("@charlie_twitter", "linkedin.com/in/charlie")

        pushContact(PushContactParams(syncId, "Charlie", socialMedia = social))

        val pulled = pullContacts()
        val contact = pulled.contacts.find { it.syncId == syncId }
        assertNotNull(contact, "Contact should be present")
        assertEquals(social.size, contact.socialMedia.size, "SocialMedia count should match")
        assertTrue(contact.socialMedia.containsAll(social), "All social handles should be preserved")
    }

    @Test
    fun `company and department fields survive round-trip`() {
        val syncId = UUID.randomUUID().toString()

        pushContact(
            PushContactParams(
                syncId = syncId,
                name = "Diana",
                company = "Acme Corp",
                companyAddress = "123 Main St",
                department = "Engineering",
            )
        )

        val pulled = pullContacts()
        val contact = pulled.contacts.find { it.syncId == syncId }
        assertNotNull(contact, "Contact should be present")
        assertEquals("Acme Corp", contact.company, "Company should be preserved")
        assertEquals("Engineering", contact.department, "Department should be preserved")
    }

    @Test
    fun `all detail fields survive round-trip in one contact`() {
        val syncId = UUID.randomUUID().toString()

        pushContact(
            PushContactParams(
                syncId = syncId,
                name = "Eve Complete",
                emails = listOf("eve@home.com", "eve@work.com"),
                phones = listOf("+44-20-1234-5678"),
                socialMedia = listOf("@eve_social"),
                company = "Eve Corp",
                companyAddress = "42 Baker Street",
                department = "Sales",
            )
        )

        val pulled = pullContacts()
        val contact = pulled.contacts.find { it.syncId == syncId }
        assertNotNull(contact, "Contact should be present")
        assertEquals("Eve Complete", contact.name)
        assertTrue(contact.emails.contains("eve@home.com"))
        assertTrue(contact.emails.contains("eve@work.com"))
        assertTrue(contact.phones.contains("+44-20-1234-5678"))
        assertTrue(contact.socialMedia.contains("@eve_social"))
        assertEquals("Eve Corp", contact.company)
        assertEquals("Sales", contact.department)
    }

    @Test
    fun `updated contact preserves all detail fields`() {
        val syncId = UUID.randomUUID().toString()

        // Push v1
        pushContact(
            PushContactParams(
                syncId = syncId,
                name = "Frank v1",
                emails = listOf("frank@old.com"),
                company = "Old Corp",
                timestamp = 1000L,
            )
        )

        // Push v2 with more details and newer timestamp
        pushContact(
            PushContactParams(
                syncId = syncId,
                name = "Frank v2",
                emails = listOf("frank@new.com", "frank@backup.com"),
                phones = listOf("+1-555-9999"),
                company = "New Corp",
                department = "R&D",
                timestamp = 2000L,
            )
        )

        val pulled = pullContacts()
        val contact = pulled.contacts.find { it.syncId == syncId }
        assertNotNull(contact, "Contact should be present")
        assertEquals("Frank v2", contact.name, "Name should be updated")
        assertTrue(contact.emails.contains("frank@new.com"), "Updated email should be present")
        assertEquals("New Corp", contact.company, "Company should be updated")
        assertEquals("R&D", contact.department, "Department should be updated")
    }
}
