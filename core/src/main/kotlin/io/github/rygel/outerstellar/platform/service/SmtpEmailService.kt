package io.github.rygel.outerstellar.platform.service

import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import org.slf4j.LoggerFactory

class EmailDeliveryException(message: String, cause: Throwable) : RuntimeException(message, cause)

data class SmtpConfig(
    val host: String,
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val from: String = "noreply@example.com",
    val startTls: Boolean = true,
)

class SmtpEmailService(private val config: SmtpConfig) : EmailService {
    private val logger = LoggerFactory.getLogger(SmtpEmailService::class.java)

    private val mailer: Mailer by lazy {
        val builder =
            MailerBuilder
                .withSMTPServer(config.host, config.port)
                .withTransportStrategy(
                    if (config.startTls) TransportStrategy.SMTP_TLS else TransportStrategy.SMTP
                )

        if (config.username.isNotBlank()) {
            builder.withSMTPServerUsername(config.username)
            builder.withSMTPServerPassword(config.password)
        }

        builder.buildMailer()
    }

    override fun send(to: String, subject: String, body: String) {
        try {
            val email =
                EmailBuilder.startingBlank()
                    .from(config.from)
                    .to(to)
                    .withSubject(subject)
                    .withPlainText(body)
                    .buildEmail()

            mailer.sendMail(email)
            logger.info("Email sent to {} subject='{}'", to, subject)
        } catch (e: Exception) {
            logger.warn("Failed to send email to {}: {}", to, e.message)
            throw EmailDeliveryException("Email delivery failed to $to: ${e.message}", e)
        }
    }
}
