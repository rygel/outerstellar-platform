package dev.outerstellar.starter.service

import org.slf4j.LoggerFactory

interface EmailService {
    fun send(to: String, subject: String, body: String)
}

/** Default — drops all emails silently. Used in tests and when email is disabled. */
class NoOpEmailService : EmailService {
    override fun send(to: String, subject: String, body: String) = Unit
}

/** Logs emails to the console. Useful during local development without an SMTP server. */
class ConsoleEmailService : EmailService {
    private val logger = LoggerFactory.getLogger(ConsoleEmailService::class.java)

    override fun send(to: String, subject: String, body: String) {
        logger.info("=== EMAIL ===")
        logger.info("To: {}", to)
        logger.info("Subject: {}", subject)
        logger.info("Body:\n{}", body)
        logger.info("=============")
    }
}
