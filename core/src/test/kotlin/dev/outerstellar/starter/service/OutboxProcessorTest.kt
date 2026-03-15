package dev.outerstellar.starter.service

import dev.outerstellar.starter.persistence.OutboxEntry
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class OutboxProcessorTest {

    private val outboxRepository = mockk<OutboxRepository>(relaxed = true)

    private fun entry(id: UUID = UUID.randomUUID()) =
        OutboxEntry(
            id = id,
            payloadType = "test.Event",
            payload = "{}",
            status = "pending",
            createdAt = Instant.now(),
        )

    @Test
    fun `processPending does nothing when no pending entries`() {
        every { outboxRepository.listPending(any()) } returns emptyList()

        val processor = OutboxProcessor(outboxRepository)
        processor.processPending()

        verify(exactly = 0) { outboxRepository.markProcessed(any()) }
        verify(exactly = 0) { outboxRepository.markFailed(any(), any()) }
    }

    @Test
    fun `processPending marks each entry processed`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        every { outboxRepository.listPending(any()) } returns listOf(entry(id1), entry(id2))

        val processor = OutboxProcessor(outboxRepository)
        processor.processPending()

        verify { outboxRepository.markProcessed(id1) }
        verify { outboxRepository.markProcessed(id2) }
    }

    @Test
    fun `processPending marks entry failed when processing throws`() {
        val id = UUID.randomUUID()
        every { outboxRepository.listPending(any()) } returns listOf(entry(id))
        every { outboxRepository.markProcessed(id) } throws RuntimeException("DB down")

        val processor = OutboxProcessor(outboxRepository)
        processor.processPending()

        verify { outboxRepository.markFailed(id, "DB down") }
    }

    @Test
    fun `processPending processes at most 10 entries`() {
        every { outboxRepository.listPending(10) } returns emptyList()

        val processor = OutboxProcessor(outboxRepository)
        processor.processPending()

        verify { outboxRepository.listPending(10) }
    }

    @Test
    fun `processPending uses transaction manager when present`() {
        val id = UUID.randomUUID()
        val txManager = mockk<TransactionManager>(relaxed = true)
        every { outboxRepository.listPending(any()) } returns listOf(entry(id))
        every { txManager.inTransaction<Unit>(any()) } answers { firstArg<() -> Unit>().invoke() }

        val processor = OutboxProcessor(outboxRepository, txManager)
        processor.processPending()

        verify { txManager.inTransaction<Unit>(any()) }
        verify { outboxRepository.markProcessed(id) }
    }

    @Test
    fun `processPending processes all entries in batch`() {
        val entries = (1..5).map { entry() }
        every { outboxRepository.listPending(any()) } returns entries

        val processor = OutboxProcessor(outboxRepository)
        processor.processPending()

        entries.forEach { e -> verify { outboxRepository.markProcessed(e.id) } }
    }
}
