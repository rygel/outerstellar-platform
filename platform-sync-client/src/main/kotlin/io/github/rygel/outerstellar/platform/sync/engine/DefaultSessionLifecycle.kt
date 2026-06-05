package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthState
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule

class DefaultSessionLifecycle : SessionLifecycle {
    private lateinit var syncDataModule: SyncDataModule
    private lateinit var authModule: AuthModule
    private lateinit var authClient: AuthClient

    fun initialize(syncDataModule: SyncDataModule, authModule: AuthModule, authClient: AuthClient) {
        this.syncDataModule = syncDataModule
        this.authModule = authModule
        this.authClient = authClient
    }

    override fun afterAuthSuccess() {
        syncDataModule.startAutoSync()
        syncDataModule.loadData()
    }

    override fun beforeLogout() {
        syncDataModule.stopAutoSync()
    }

    override fun onSessionExpired() {
        syncDataModule.stopAutoSync()
        syncDataModule.clearState()
        authModule.resetState()
        authClient.logout()
    }

    override val authState: AuthState
        get() = authModule.authState
}
