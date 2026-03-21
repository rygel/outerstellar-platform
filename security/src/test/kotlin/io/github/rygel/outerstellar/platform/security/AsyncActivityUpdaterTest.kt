package io.github.rygel.outerstellar.platform.security

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AsyncActivityUpdaterTest {

    private val repo = mockk<UserRepository> { every { updateLastActivity(any()) } just runs }

    @Test
    fun `record deduplicates multiple calls for the same user`() {
        val updater = AsyncActivityUpdater(repo)
        val id = UUID.randomUUID()

        repeat(50) { updater.record(id) }
        updater.flush()

        verify(exactly = 1) { repo.updateLastActivity(id) }
    }

    @Test
    fun `flush writes all distinct pending users`() {
        val updater = AsyncActivityUpdater(repo)
        val ids = (1..5).map { UUID.randomUUID() }

        ids.forEach { updater.record(it) }
        updater.flush()

        assertEquals(
            5,
            ids.count { id ->
                runCatching { verify(exactly = 1) { repo.updateLastActivity(id) } }.isSuccess
            },
        )
        verify(exactly = 5) { repo.updateLastActivity(any()) }
    }

    @Test
    fun `flush on empty pending map does nothing`() {
        val updater = AsyncActivityUpdater(repo)
        updater.flush()

        verify(exactly = 0) { repo.updateLastActivity(any()) }
    }

    @Test
    fun `pending is cleared after flush so second flush does not re-write`() {
        val updater = AsyncActivityUpdater(repo)
        val id = UUID.randomUUID()

        updater.record(id)
        updater.flush()
        updater.flush()

        verify(exactly = 1) { repo.updateLastActivity(id) }
    }

    @Test
    fun `flush continues after individual write failure`() {
        val failId = UUID.randomUUID()
        val successId = UUID.randomUUID()
        every { repo.updateLastActivity(failId) } throws RuntimeException("DB error")

        val updater = AsyncActivityUpdater(repo)
        updater.record(failId)
        updater.record(successId)
        updater.flush()

        verify(exactly = 1) { repo.updateLastActivity(successId) }
    }
}
