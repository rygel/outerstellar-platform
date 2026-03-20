package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.sync.SyncStats

interface SyncProvider {
    fun sync(): SyncStats
}
