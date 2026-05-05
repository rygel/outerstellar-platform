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
}
