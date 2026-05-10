package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.platform.sync.SyncStats
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach

internal abstract class DesktopSyncEngineTestBase {

    protected lateinit var syncService: SyncService
    protected lateinit var messageService: MessageService
    protected lateinit var contactService: ContactService
    protected lateinit var analytics: AnalyticsService
    protected lateinit var connectivityChecker: ConnectivityChecker
    protected lateinit var notifier: EngineNotifier
    protected lateinit var engine: DesktopSyncEngine

    protected var connectivityObserver: ((Boolean) -> Unit)? = null

    @BeforeEach
    fun setUp() {
        syncService = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        contactService = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)
        notifier = mockk(relaxed = true)

        val observerSlot = slot<(Boolean) -> Unit>()
        every { connectivityChecker.addObserver(capture(observerSlot)) } answers
            {
                connectivityObserver = observerSlot.captured
            }

        engine =
            DesktopSyncEngine(
                syncService = syncService,
                messageService = messageService,
                contactService = contactService,
                analytics = analytics,
                connectivityChecker = connectivityChecker,
                notifier = notifier,
            )
    }

    protected fun stubLoggedIn() {
        every { syncService.login("user", "pass") } returns AuthTokenResponse("tok", "user", "USER")
        engine.login("user", "pass")
    }

    protected fun stubSyncSuccess(pushed: Int = 0, pulled: Int = 0, conflicts: Int = 0): SyncStats {
        val stats = SyncStats(pushedCount = pushed, pulledCount = pulled, conflictCount = conflicts)
        every { syncService.sync() } returns stats
        return stats
    }
}
