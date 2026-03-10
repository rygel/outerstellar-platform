package dev.outerstellar.starter.service

import dev.outerstellar.starter.sync.SyncStats

interface SyncProvider {
    fun sync(): SyncStats
}
