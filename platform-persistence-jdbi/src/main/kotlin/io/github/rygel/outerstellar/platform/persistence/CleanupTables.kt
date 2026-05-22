package io.github.rygel.outerstellar.platform.persistence

import java.util.Collections

object CleanupTables {
    private val TABLES =
        listOf(
            "plt_sessions",
            "plt_notifications",
            "plt_device_tokens",
            "plt_oauth_connections",
            "plt_api_keys",
            "plt_password_reset_tokens",
            "plt_audit_log",
            "plt_outbox",
            "plt_contact_emails",
            "plt_contact_phones",
            "plt_contact_socials",
            "plt_contacts",
            "plt_messages",
            "plt_poll_votes",
            "plt_poll_options",
            "plt_polls",
            "plt_sync_state",
            "plt_users",
        )

    val ALL: List<String> = Collections.unmodifiableList(TABLES)
}
