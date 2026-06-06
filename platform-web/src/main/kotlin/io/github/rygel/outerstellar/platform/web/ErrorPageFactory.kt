package io.github.rygel.outerstellar.platform.web

class ErrorPageFactory {

    companion object {
        private const val HTTP_STATUS_NOT_FOUND = 404
        private const val HTTP_STATUS_SERVER_ERROR = 500
        private const val HTTP_STATUS_FORBIDDEN = 403
        private const val SUPPORTED_KINDS = "not-found, server-error, forbidden"

        fun isSupportedKind(kind: String): Boolean =
            when (kind) {
                "not-found",
                "server-error",
                "forbidden" -> true
                else -> false
            }
    }

    fun buildErrorPage(shellRenderer: ShellRenderer, kind: String): Page<ErrorPage> {
        require(isSupportedKind(kind)) { "Unknown error page kind '$kind'. Supported kinds: $SUPPORTED_KINDS" }

        val i18n = shellRenderer.i18n
        val shell = shellRenderer.shell(i18n.translate("web.nav.errors"), "/errors")
        val statusCode =
            when (kind) {
                "not-found" -> HTTP_STATUS_NOT_FOUND
                "server-error" -> HTTP_STATUS_SERVER_ERROR
                "forbidden" -> HTTP_STATUS_FORBIDDEN
                else -> error("Unsupported error kind after validation: $kind")
            }

        return Page(
            shell = shell,
            data =
                ErrorPage(
                    statusCode = statusCode,
                    heading = i18n.translate("web.error.$kind.title"),
                    message = i18n.translate("web.error.$kind.message"),
                    primaryActionLabel = i18n.translate("web.error.primary"),
                    primaryActionUrl = shellRenderer.url("/"),
                    secondaryActionLabel = i18n.translate("web.error.secondary"),
                    secondaryActionUrl = shellRenderer.url("/auth"),
                    helpButtonLabel = i18n.translate("web.error.help"),
                    helpUrl = shellRenderer.url("/errors/components/help/$kind"),
                    errorLabel = i18n.translate("web.error.label"),
                ),
        )
    }

    fun buildErrorHelp(shellRenderer: ShellRenderer, kind: String): ErrorHelpFragment {
        require(isSupportedKind(kind)) { "Unknown error help kind '$kind'. Supported kinds: $SUPPORTED_KINDS" }

        val i18n = shellRenderer.i18n
        return ErrorHelpFragment(
            title = i18n.translate("web.error.$kind.help.title"),
            items =
                listOf(
                    i18n.translate("web.error.$kind.help.item1"),
                    i18n.translate("web.error.$kind.help.item2"),
                    i18n.translate("web.error.$kind.help.item3"),
                ),
        )
    }
}
