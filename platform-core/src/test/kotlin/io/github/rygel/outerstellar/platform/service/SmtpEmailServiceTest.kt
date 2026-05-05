package io.github.rygel.outerstellar.platform.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull

class SmtpEmailServiceTest {
    @Test
    fun `can be constructed with config`() {
        val service = SmtpEmailService(SmtpConfig(host = "localhost", port = 25, from = "test@test.com"))
        assertNotNull(service)
    }

    @Test
    fun `send throws on invalid host`() {
        val service = SmtpEmailService(SmtpConfig(host = "invalid.host.invalid", port = 25, from = "test@test.com"))
        assertThrows<EmailDeliveryException> { service.send("to@test.com", "Test", "Body") }
    }
}
