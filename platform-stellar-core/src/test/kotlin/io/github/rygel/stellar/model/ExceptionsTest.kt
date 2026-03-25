package io.github.rygel.stellar.model

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class ExceptionsTest {

    @Test
    fun `StellarException extends RuntimeException`() {
        val exception = CommunityNotFoundException("test")
        assertIs<RuntimeException>(exception)
    }

    @Test
    fun `CommunityNotFoundException has correct message`() {
        val exception = CommunityNotFoundException("kotlin")
        assertEquals("Community 'kotlin' not found", exception.message)
    }

    @Test
    fun `CommunityNotFoundException extends StellarException`() {
        val exception = CommunityNotFoundException("test")
        assertIs<StellarException>(exception)
    }

    @Test
    fun `PostNotFoundException has correct message with UUID`() {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val exception = PostNotFoundException(id)
        assertEquals("Post 550e8400-e29b-41d4-a716-446655440000 not found", exception.message)
    }

    @Test
    fun `PostNotFoundException extends StellarException`() {
        val exception = PostNotFoundException(UUID.randomUUID())
        assertIs<StellarException>(exception)
    }

    @Test
    fun `CommentNotFoundException has correct message with UUID`() {
        val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val exception = CommentNotFoundException(id)
        assertEquals("Comment 123e4567-e89b-12d3-a456-426614174000 not found", exception.message)
    }

    @Test
    fun `CommentNotFoundException extends StellarException`() {
        val exception = CommentNotFoundException(UUID.randomUUID())
        assertIs<StellarException>(exception)
    }

    @Test
    fun `DuplicateCommunityException has correct message`() {
        val exception = DuplicateCommunityException("kotlin")
        assertEquals("Community 'kotlin' already exists", exception.message)
    }

    @Test
    fun `DuplicateCommunityException extends StellarException`() {
        val exception = DuplicateCommunityException("test")
        assertIs<StellarException>(exception)
    }

    @Test
    fun `NotModeratorException has correct message`() {
        val exception = NotModeratorException("kotlin")
        assertEquals("Not a moderator of 'kotlin'", exception.message)
    }

    @Test
    fun `NotModeratorException extends StellarException`() {
        val exception = NotModeratorException("test")
        assertIs<StellarException>(exception)
    }

    @Test
    fun `BannedFromCommunityException has correct message`() {
        val exception = BannedFromCommunityException("kotlin")
        assertEquals("Banned from 'kotlin'", exception.message)
    }

    @Test
    fun `BannedFromCommunityException extends StellarException`() {
        val exception = BannedFromCommunityException("test")
        assertIs<StellarException>(exception)
    }
}
