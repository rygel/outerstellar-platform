package io.github.rygel.outerstellar.platform.sync

import io.github.rygel.outerstellar.platform.model.ValidationException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SyncValidationTest {

    @Test
    fun `rejects blank syncId`() {
        val msg = SyncMessage("", "author", "content", 0L)
        val request = SyncPushRequest(listOf(msg))
        assertThrows(ValidationException::class.java) { SyncPushRequest.validate(request) }
    }

    @Test
    fun `rejects blank author`() {
        val msg = SyncMessage("id", "", "content", 0L)
        val request = SyncPushRequest(listOf(msg))
        assertThrows(ValidationException::class.java) { SyncPushRequest.validate(request) }
    }

    @Test
    fun `rejects blank content`() {
        val msg = SyncMessage("id", "author", "", 0L)
        val request = SyncPushRequest(listOf(msg))
        assertThrows(ValidationException::class.java) { SyncPushRequest.validate(request) }
    }

    @Test
    fun `accepts valid message`() {
        val msg = SyncMessage("id", "author", "content", 0L)
        val request = SyncPushRequest(listOf(msg))
        assertDoesNotThrow { SyncPushRequest.validate(request) }
    }

    @Test
    fun `rejects blank contact syncId`() {
        val contact = SyncContact("", "name", emptyList(), emptyList(), emptyList(), "", "", "", 0L)
        val request = SyncPushContactRequest(listOf(contact))
        assertThrows(ValidationException::class.java) { SyncPushContactRequest.validate(request) }
    }

    @Test
    fun `rejects blank contact name`() {
        val contact = SyncContact("id", "", emptyList(), emptyList(), emptyList(), "", "", "", 0L)
        val request = SyncPushContactRequest(listOf(contact))
        assertThrows(ValidationException::class.java) { SyncPushContactRequest.validate(request) }
    }

    @Test
    fun `rejects message author exceeding max length`() {
        val longAuthor = "a".repeat(SyncMessage.MAX_AUTHOR_LENGTH + 1)
        val msg = SyncMessage("id", longAuthor, "content", 0L)
        val request = SyncPushRequest(listOf(msg))
        assertThrows(ValidationException::class.java) { SyncPushRequest.validate(request) }
    }

    @Test
    fun `rejects message content exceeding max length`() {
        val longContent = "c".repeat(SyncMessage.MAX_CONTENT_LENGTH + 1)
        val msg = SyncMessage("id", "author", longContent, 0L)
        val request = SyncPushRequest(listOf(msg))
        assertThrows(ValidationException::class.java) { SyncPushRequest.validate(request) }
    }

    @Test
    fun `accepts message at max field lengths`() {
        val msg =
            SyncMessage("id", "a".repeat(SyncMessage.MAX_AUTHOR_LENGTH), "c".repeat(SyncMessage.MAX_CONTENT_LENGTH), 0L)
        assertDoesNotThrow { SyncMessage.validate(msg) }
    }

    @Test
    fun `rejects contact name exceeding max length`() {
        val longName = "n".repeat(SyncContact.MAX_NAME_LENGTH + 1)
        val contact = SyncContact("id", longName, emptyList(), emptyList(), emptyList(), "", "", "", 0L)
        assertThrows(ValidationException::class.java) { SyncContact.validate(contact) }
    }

    @Test
    fun `accepts contact at max name length`() {
        val contact =
            SyncContact(
                "id",
                "n".repeat(SyncContact.MAX_NAME_LENGTH),
                emptyList(),
                emptyList(),
                emptyList(),
                "",
                "",
                "",
                0L,
            )
        assertDoesNotThrow { SyncContact.validate(contact) }
    }
}
