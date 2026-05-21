package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse

interface SyncClient {
    fun pull(since: Long): SyncPullResponse

    fun push(request: SyncPushRequest): SyncPushResponse
}
