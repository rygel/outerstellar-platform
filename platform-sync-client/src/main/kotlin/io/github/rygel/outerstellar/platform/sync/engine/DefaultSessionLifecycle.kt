package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthState
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule

class DefaultSessionLifecycle : SessionLifecycle {
    private var syncDataModule: SyncDataModule? = null
    private var authModule: AuthModule? = null
    private var authClient: AuthClient? = null

    fun initialize(syncDataModule: SyncDataModule, authModule: AuthModule, authClient: AuthClient) {
        this.syncDataModule = syncDataModule
        this.authModule = authModule
        this.authClient = authClient
    }

    override fun afterAuthSuccess() {
        val sm = requireNotNull(syncDataModule)
        sm.startAutoSync()
        sm.loadData()
    }

    override fun beforeLogout() {
        requireNotNull(syncDataModule).stopAutoSync()
    }

    override fun onSessionExpired() {
        requireNotNull(syncDataModule).stopAutoSync()
        requireNotNull(syncDataModule).clearState()
        requireNotNull(authModule).resetState()
        requireNotNull(authClient).logout()
    }

    override val authState: AuthState
        get() = requireNotNull(authModule).authState
}
