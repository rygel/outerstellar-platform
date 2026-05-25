package io.github.rygel.outerstellar.platform.web

class ErrorPageFactory {

    companion object {
        private const val HTTP_STATUS_NOT_FOUND = 404
        private const val HTTP_STATUS_SERVER_ERROR = 500
    }

    fun buildErrorPage(shellRenderer: ShellRenderer, kind: String): Page<ErrorPage> {
        val i18n = shellRenderer.i18n
        val shell = shellRenderer.shell(i18n.translate("web.nav.errors"), "/errors")
        val normalizedKind = if (kind == "server-error") "server-error" else "not-found"
        val statusCode =
            if (normalizedKind == "server-error") {
                HTTP_STATUS_SERVER_ERROR
            } else {
                HTTP_STATUS_NOT_FOUND
            }

        return Page(
            shell = shell,
            data =
                ErrorPage(
                    statusCode = statusCode,
                    heading = i18n.translate("web.error.$normalizedKind.title"),
                    message = i18n.translate("web.error.$normalizedKind.message"),
                    primaryActionLabel = i18n.translate("web.error.primary"),
                    primaryActionUrl = shellRenderer.url("/"),
                    secondaryActionLabel = i18n.translate("web.error.secondary"),
                    secondaryActionUrl = shellRenderer.url("/auth"),
                    helpButtonLabel = i18n.translate("web.error.help"),
                    helpUrl = shellRenderer.url("/errors/components/help/$normalizedKind"),
                    errorLabel = i18n.translate("web.error.label"),
                ),
        )
    }

    fun buildErrorHelp(shellRenderer: ShellRenderer, kind: String): ErrorHelpFragment {
        val i18n = shellRenderer.i18n
        val normalizedKind = if (kind == "server-error") "server-error" else "not-found"

        return ErrorHelpFragment(
            title = i18n.translate("web.error.$normalizedKind.help.title"),
            items =
                listOf(
                    i18n.translate("web.error.$normalizedKind.help.item1"),
                    i18n.translate("web.error.$normalizedKind.help.item2"),
                    i18n.translate("web.error.$normalizedKind.help.item3"),
                ),
        )
    }
}
