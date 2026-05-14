package io.github.rygel.outerstellar.platform.search

import io.github.rygel.outerstellar.platform.service.ContactService

class ContactSearchProvider(private val contactService: ContactService?) : SearchProvider {
    override val type: String = "contact"

    companion object {
        private const val MAX_SEARCH_LIMIT = 50
    }

    override fun search(query: String, limit: Int): List<SearchResult> {
        if (query.isBlank() || contactService == null) return emptyList()
        return contactService.listContacts(
            query = query,
            limit = limit.coerceIn(1, MAX_SEARCH_LIMIT),
            offset = 0,
        ).map { c ->
            SearchResult(
                id = c.syncId,
                title = c.name,
                subtitle = c.emails.firstOrNull() ?: c.company.ifBlank { c.department },
                url = "/contacts",
                type = "contact",
                score = if (c.name.contains(query, ignoreCase = true)) 1.0 else 0.7,
            )
        }
    }
}
    }
}
