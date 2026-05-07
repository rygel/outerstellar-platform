package io.github.rygel.outerstellar.platform.service

import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties
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

    private val session: Session by lazy {
        val props = Properties()
        props["mail.smtp.host"] = config.host
        props["mail.smtp.port"] = config.port.toString()
        props["mail.smtp.starttls.enable"] = config.startTls.toString()
        props["mail.smtp.ssl.trust"] = config.host

        if (config.username.isNotBlank()) {
            props["mail.smtp.auth"] = "true"
            Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(config.username, config.password)
                    }
                },
            )
        } else {
            Session.getInstance(props)
        }
    }

    override fun send(to: String, subject: String, body: String) {
        try {
            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(config.from))
                    setRecipient(jakarta.mail.Message.RecipientType.TO, InternetAddress(to))
                    setSubject(subject, "UTF-8")
                    setText(body, "UTF-8")
                }

            Transport.send(message)

            logger.info("Email sent to {} subject='{}'", to, subject)
        } catch (e: jakarta.mail.MessagingException) {
            logger.warn("Failed to send email to {}: {}", to, e.message)
            throw EmailDeliveryException("Email delivery failed to $to: ${e.message}", e)
        }
    }
}
