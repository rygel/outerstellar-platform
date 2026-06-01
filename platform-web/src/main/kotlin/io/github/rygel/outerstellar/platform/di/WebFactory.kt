package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.analytics.SegmentAnalyticsService
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import io.github.rygel.outerstellar.platform.infra.ExtensionTemplateRenderer
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import io.github.rygel.outerstellar.platform.persistence.PollRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.persistence.VoteRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.security.UserAdminService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.service.PollService
import io.github.rygel.outerstellar.platform.service.VoteService
import io.github.rygel.outerstellar.platform.web.AdminPageFactory
import io.github.rygel.outerstellar.platform.web.AuthPageFactory
import io.github.rygel.outerstellar.platform.web.ContactTrashListFactory
import io.github.rygel.outerstellar.platform.web.ContactsPageFactory
import io.github.rygel.outerstellar.platform.web.DevDashboardPageFactory
import io.github.rygel.outerstellar.platform.web.ErrorPageFactory
import io.github.rygel.outerstellar.platform.web.ExtensionHostContextFactory
import io.github.rygel.outerstellar.platform.web.HomePageFactory
import io.github.rygel.outerstellar.platform.web.InfraPageFactory
import io.github.rygel.outerstellar.platform.web.SearchPageFactory
import io.github.rygel.outerstellar.platform.web.SettingsPageFactory
import io.github.rygel.outerstellar.platform.web.SidebarFactory
import io.github.rygel.outerstellar.platform.web.SyncWebSocket
import org.http4k.template.TemplateRenderer

class WebRuntimeComponents(
    val templateRenderer: TemplateRenderer,
    val analyticsService: AnalyticsService,
    val syncWebSocket: SyncWebSocket,
)

@Suppress("LongParameterList")
class WebPageFactories(
    val adminPageFactory: AdminPageFactory,
    val authPageFactory: AuthPageFactory,
    val errorPageFactory: ErrorPageFactory,
    val sidebarFactory: SidebarFactory,
    val settingsPageFactory: SettingsPageFactory,
    val searchPageFactory: SearchPageFactory,
    val devDashboardPageFactory: DevDashboardPageFactory,
    val homePageFactory: HomePageFactory,
    val infraPageFactory: InfraPageFactory,
    val contactsPageFactory: ContactsPageFactory,
)

class WebComponents(
    val runtime: WebRuntimeComponents,
    val pages: WebPageFactories,
    val hostedAppContextFactory: ExtensionHostContextFactory,
    val voteService: VoteService,
    val pollService: PollService,
    val notificationService: NotificationService,
)

@Suppress("LongParameterList")
fun createWebComponents(
    config: AppConfig,
    extension: PlatformExtension? = null,
    apiKeyService: ApiKeyService,
    oauthService: OAuthService,
    syncWebSocket: SyncWebSocket,
    userAdminService: UserAdminService,
    userRepository: UserRepository,
    messageRepository: MessageRepository,
    messageService: MessageService? = null,
    contactService: ContactService? = null,
    voteRepository: VoteRepository,
    pollRepository: PollRepository,
    notificationRepository: NotificationRepository,
): WebComponents {
    val baseRenderer = createRenderer(config.runtime)
    val overrides = extension?.templateOverrides()
    val templateRenderer: TemplateRenderer =
        if (extension != null && overrides != null && overrides.isNotEmpty()) {
            ExtensionTemplateRenderer(baseRenderer, overrides, extension::class.java.classLoader)
        } else {
            baseRenderer
        }

    val notificationService = NotificationService(notificationRepository)
    val adminPageFactory = AdminPageFactory(apiKeyService, notificationService, userAdminService)
    val authPageFactory = AuthPageFactory(config.appleOAuth.enabled)
    val errorPageFactory = ErrorPageFactory()
    val sidebarFactory = SidebarFactory()
    val settingsPageFactory = SettingsPageFactory(adminPageFactory, authPageFactory, sidebarFactory)
    val searchPageFactory = SearchPageFactory()
    val devDashboardPageFactory = DevDashboardPageFactory()
    val contactTrashListFactory = ContactTrashListFactory(contactService)
    val contactsPageFactory = ContactsPageFactory(contactService, contactTrashListFactory)
    val homePageFactory = HomePageFactory(messageService, contactTrashListFactory)
    val infraPageFactory = InfraPageFactory(messageRepository)
    val runtime = config.runtime
    val analyticsService: AnalyticsService =
        if (config.segment.enabled && config.segment.writeKey.isNotBlank()) {
            SegmentAnalyticsService(config.segment.writeKey)
        } else {
            NoOpAnalyticsService()
        }

    val voteService = VoteService(voteRepository, messageRepository)
    val pollService = PollService(pollRepository)
    val runtimeComponents =
        WebRuntimeComponents(
            templateRenderer = templateRenderer,
            analyticsService = analyticsService,
            syncWebSocket = syncWebSocket,
        )
    val hostedAppContextFactory =
        ExtensionHostContextFactory(
            renderer = templateRenderer,
            apiKeyService = apiKeyService,
            oauthService = oauthService,
            userRepository = userRepository,
            analytics = analyticsService,
            notificationService = notificationService,
        )
    val pageFactories =
        WebPageFactories(
            adminPageFactory = adminPageFactory,
            authPageFactory = authPageFactory,
            errorPageFactory = errorPageFactory,
            sidebarFactory = sidebarFactory,
            settingsPageFactory = settingsPageFactory,
            searchPageFactory = searchPageFactory,
            devDashboardPageFactory = devDashboardPageFactory,
            homePageFactory = homePageFactory,
            infraPageFactory = infraPageFactory,
            contactsPageFactory = contactsPageFactory,
        )

    return WebComponents(
        runtime = runtimeComponents,
        pages = pageFactories,
        hostedAppContextFactory = hostedAppContextFactory,
        voteService = voteService,
        pollService = pollService,
        notificationService = notificationService,
    )
}
