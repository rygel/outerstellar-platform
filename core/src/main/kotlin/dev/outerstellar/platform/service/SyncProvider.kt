package dev.outerstellar.platform.service

import dev.outerstellar.platform.sync.SyncStats

interface SyncProvider {
    fun sync(): SyncStats
}
