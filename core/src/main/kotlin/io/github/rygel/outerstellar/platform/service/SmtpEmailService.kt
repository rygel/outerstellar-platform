package io.github.rygel.outerstellar.platform.service

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import java.util.Properties

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

    private val session: Session by lazy {
        val props = Properties()
        props["mail.smtp.host"] = config.host
        props["mail.smtp.port"] = config.port.toString()
        props["mail.smtp.auth"] = (config.username.isNotBlank()).toString()
        props["mail.smtp.starttls.enable"] = config.startTls.toString()

        if (config.username.isNotBlank()) {
            Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(config.username, config.password)
                },
            )
        } else {
            Session.getInstance(props)
        }
    }

    override fun send(to: String, subject: String, body: String) {
        try {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(config.from))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            message.subject = subject
            message.setText(body)
            Transport.send(message)
            logger.info("Email sent to {} subject='{}'", to, subject)
        } catch (e: MessagingException) {
            logger.warn("Failed to send email to {}: {}", to, e.message)
            throw EmailDeliveryException("Email delivery failed to $to: ${e.message}", e)
        }
    }
}
