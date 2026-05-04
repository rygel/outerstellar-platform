package io.github.rygel.outerstellar.platform.service

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ResilientEmailServiceTest {

    @Test
    fun `send delegates to underlying EmailService with correct parameters`() {
        val delegate = mockk<EmailService>()
        every { delegate.send("alice@example.com", "Hello", "Body") } just Runs

        val service = ResilientEmailService(delegate)
        service.send("alice@example.com", "Hello", "Body")

        verify(exactly = 1) { delegate.send("alice@example.com", "Hello", "Body") }
    }

    @Test
    fun `send propagates delegate exceptions`() {
        val delegate = mockk<EmailService>()
        every { delegate.send(any(), any(), any()) } throws RuntimeException("SMTP down")

        val service = ResilientEmailService(delegate)

        assertFailsWith<RuntimeException> { service.send("alice@example.com", "Hello", "Body") }
    }

    @Test
    fun `send silently drops email when circuit is open after repeated failures`() {
        val delegate = mockk<EmailService>()
        every { delegate.send(any(), any(), any()) } throws RuntimeException("SMTP down")

        val service = ResilientEmailService(delegate)

        repeat(105) { runCatching { service.send("to", "subj", "body") } }

        service.send("to", "subj", "body")

        verify(atMost = 105) { delegate.send(any(), any(), any()) }
    }
}
