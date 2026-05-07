package io.github.rygel.outerstellar.platform.infra

import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteApiKeysPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteAuditLogPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteAuthFormFragmentGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteAuthPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteAuthResultFragmentGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteChangePasswordFormGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteChangePasswordPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteContactsPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteDevDashboardGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteErrorHelpFragmentGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteErrorPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteFooterStatusFragmentGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteHomePageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteLayoutRouterGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteNotificationsPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteProfilePageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteResetPasswordPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteSearchPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteSettingsPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteTrashPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteUserAdminPageGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JteConflictResolveModalGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JteContactFormGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JteMessageListGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JteModalGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JteModalOverlayGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JteNotificationBellGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JtePageHeaderGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JtePaginationGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.components.JteSidebarSelectorGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.layouts.JteLayoutHeadGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.layouts.JteSidebarLayoutGenerated
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.layouts.JteTopbarLayoutGenerated
import org.slf4j.LoggerFactory

object JteClassRegistry {
    private val logger = LoggerFactory.getLogger(JteClassRegistry::class.java)

    private val pageClasses =
        listOf(
            JteApiKeysPageGenerated::class.java,
            JteAuditLogPageGenerated::class.java,
            JteAuthPageGenerated::class.java,
            JteChangePasswordPageGenerated::class.java,
            JteContactsPageGenerated::class.java,
            JteDevDashboardGenerated::class.java,
            JteErrorPageGenerated::class.java,
            JteHomePageGenerated::class.java,
            JteLayoutRouterGenerated::class.java,
            JteNotificationsPageGenerated::class.java,
            JteProfilePageGenerated::class.java,
            JteResetPasswordPageGenerated::class.java,
            JteSearchPageGenerated::class.java,
            JteSettingsPageGenerated::class.java,
            JteTrashPageGenerated::class.java,
            JteUserAdminPageGenerated::class.java,
        )

    private val fragmentClasses =
        listOf(
            JteAuthFormFragmentGenerated::class.java,
            JteAuthResultFragmentGenerated::class.java,
            JteChangePasswordFormGenerated::class.java,
            JteErrorHelpFragmentGenerated::class.java,
            JteFooterStatusFragmentGenerated::class.java,
        )

    private val componentClasses =
        listOf(
            JteConflictResolveModalGenerated::class.java,
            JteContactFormGenerated::class.java,
            JteMessageListGenerated::class.java,
            JteModalGenerated::class.java,
            JteModalOverlayGenerated::class.java,
            JteNotificationBellGenerated::class.java,
            JtePageHeaderGenerated::class.java,
            JtePaginationGenerated::class.java,
            JteSidebarSelectorGenerated::class.java,
        )

    private val layoutClasses =
        listOf(
            JteLayoutHeadGenerated::class.java,
            JteSidebarLayoutGenerated::class.java,
            JteTopbarLayoutGenerated::class.java,
        )

    val allClasses: List<Class<*>> = pageClasses + fragmentClasses + componentClasses + layoutClasses

    init {
        logger.info("Initializing {} JTE template classes", allClasses.size)
        for (cls in allClasses) {
            try {
                Class.forName(cls.name, true, cls.classLoader)
            } catch (e: ClassNotFoundException) {
                logger.warn("Failed to force-load JTE template class {}: {}", cls.name, e.message)
            }
        }
    }

    fun getTemplateClass(templateName: String): Class<*>? {
        val templatePath = templateName.removeSuffix(".kte")
        val slash = templatePath.lastIndexOf('/')
        val packagePath = if (slash >= 0) templatePath.substring(0, slash).replace('/', '.') else ""
        val baseName = if (slash >= 0) templatePath.substring(slash + 1) else templatePath
        val className = "Jte${baseName.replace("-", "").replace(".", "")}Generated"
        val fullName =
            if (packagePath.isEmpty()) {
                "gg.jte.generated.precompiled.outerstellar.$className"
            } else {
                "gg.jte.generated.precompiled.outerstellar.$packagePath.$className"
            }
        return allClasses.find { it.name == fullName }
    }
}
