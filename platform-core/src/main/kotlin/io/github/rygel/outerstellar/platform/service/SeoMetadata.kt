package io.github.rygel.outerstellar.platform.service

class SeoMetadata(
    private val title: String,
    private val description: String,
    private val canonicalUrl: String,
    private val ogImage: String = "",
    private val ogType: String = "website",
    private val twitterCard: String = "summary",
    private val locale: String = "en",
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun forPage(
            title: String,
            description: String,
            canonicalUrl: String,
            ogImage: String = "",
            locale: String = "en",
            robots: String = "index, follow",
        ) =
            SeoMetadata(
                title = title,
                description = description,
                canonicalUrl = canonicalUrl,
                ogImage = ogImage,
                locale = locale,
            )
    }

    fun generateAllMetaTags(): String = buildString {
        if (title.isNotBlank()) {
            appendLine("<meta property=\"og:title\" content=\"${escapeHtml(title)}\">")
            appendLine("<meta name=\"twitter:title\" content=\"${escapeHtml(title)}\">")
        }
        if (description.isNotBlank()) {
            appendLine("<meta property=\"og:description\" content=\"${escapeHtml(description)}\">")
            appendLine("<meta name=\"twitter:description\" content=\"${escapeHtml(description)}\">")
        }
        if (canonicalUrl.isNotBlank()) {
            appendLine("<meta property=\"og:url\" content=\"${escapeHtml(canonicalUrl)}\">")
        }
        if (ogImage.isNotBlank()) {
            appendLine("<meta property=\"og:image\" content=\"${escapeHtml(ogImage)}\">")
            appendLine("<meta name=\"twitter:image\" content=\"${escapeHtml(ogImage)}\">")
        }
        if (ogType.isNotBlank()) {
            appendLine("<meta property=\"og:type\" content=\"${escapeHtml(ogType)}\">")
        }
        if (twitterCard.isNotBlank()) {
            appendLine("<meta name=\"twitter:card\" content=\"${escapeHtml(twitterCard)}\">")
        }
        if (locale.isNotBlank()) {
            appendLine("<meta property=\"og:locale\" content=\"${escapeHtml(locale)}\">")
        }
        if (canonicalUrl.isNotBlank() && title.isNotBlank()) {
            appendLine("<script type=\"application/ld+json\">")
            appendLine("{")
            appendLine("  \"@context\": \"https://schema.org\",")
            appendLine("  \"@type\": \"WebSite\",")
            appendLine("  \"name\": \"${escapeJson(title)}\",")
            appendLine("  \"url\": \"${escapeJson(canonicalUrl)}\"")
            if (description.isNotBlank()) {
                appendLine("  ,\"description\": \"${escapeJson(description)}\"")
            }
            appendLine("}")
            appendLine("</script>")
        }
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}
