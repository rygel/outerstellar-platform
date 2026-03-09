package dev.outerstellar.starter.sync

import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.service.SyncProvider
import dev.outerstellar.starter.service.SyncStats
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ResilienceFilters
import org.http4k.format.Jackson.auto
import java.time.Duration

class SyncService(
  private val repository: MessageRepository,
  private val serverBaseUrl: String,
  private val outboxRepository: OutboxRepository? = null,
  private val transactionManager: TransactionManager? = null,
  httpClient: HttpHandler = ApacheClient(),
) : SyncProvider {
  private val retry = Retry.of("sync-retry", RetryConfig.custom<RetryConfig>()
    .maxAttempts(3)
    .waitDuration(Duration.ofMillis(100))
    .build())

  private val circuitBreaker = CircuitBreaker.of("sync-cb", CircuitBreakerConfig.custom()
    .slidingWindowSize(10)
    .failureRateThreshold(50f)
    .waitDurationInOpenState(Duration.ofSeconds(5))
    .build())

  private val httpClient = ResilienceFilters.RetryFailures(retry)
    .then(ResilienceFilters.CircuitBreak(circuitBreaker))
    .then(httpClient)

  private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
  private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()
  private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()

  override fun sync(): SyncStats {
    val dirtyMessages = repository.listDirtyMessages().map { it.toSyncMessage() }
    var pushedCount = 0
    var conflictCount = 0

    if (dirtyMessages.isNotEmpty()) {
      val pushResponse =
        httpClient(
          Request(POST, "$serverBaseUrl/api/v1/sync")
            .with(pushRequestLens of SyncPushRequest(dirtyMessages))
        )
      require(pushResponse.status == Status.OK) { "Unexpected push status: ${pushResponse.status}" }

      val pushBody = pushResponseLens(pushResponse)
      val conflictIds = pushBody.conflicts.map { it.syncId }.toSet()
      
      if (transactionManager != null && outboxRepository != null) {
        transactionManager.inTransaction {
          repository.markClean(dirtyMessages.map { it.syncId }.filterNot { it in conflictIds })
          outboxRepository.save(
            dev.outerstellar.starter.persistence.OutboxEntry(
              id = java.util.UUID.randomUUID(),
              payloadType = "SYNC_PUSH_SUCCESS",
              payload = "Pushed ${pushBody.appliedCount} messages, ${pushBody.conflicts.size} conflicts"
            )
          )
        }
      } else {
        repository.markClean(dirtyMessages.map { it.syncId }.filterNot { it in conflictIds })
      }

      pushedCount = pushBody.appliedCount
      conflictCount = pushBody.conflicts.size
    }

    val pullResponse =
      httpClient(
        Request(GET, "$serverBaseUrl/api/v1/sync?since=${repository.getLastSyncEpochMs()}")
      )
    require(pullResponse.status == Status.OK) { "Unexpected pull status: ${pullResponse.status}" }

    val pullBody = pullResponseLens(pullResponse)
    pullBody.messages.forEach { incoming ->
      val current = repository.findBySyncId(incoming.syncId)
      if (
        current == null || incoming.updatedAtEpochMs >= current.updatedAtEpochMs || !current.dirty
      ) {
        if (transactionManager != null && outboxRepository != null) {
          transactionManager.inTransaction {
            repository.upsertSyncedMessage(incoming, dirty = false)
            outboxRepository.save(
              dev.outerstellar.starter.persistence.OutboxEntry(
                id = java.util.UUID.randomUUID(),
                payloadType = "SYNC_PULL_UPSERT",
                payload = incoming.syncId
              )
            )
          }
        } else {
          repository.upsertSyncedMessage(incoming, dirty = false)
        }
      }
    }
    repository.setLastSyncEpochMs(pullBody.serverTimestamp)

    return SyncStats(
      pushedCount = pushedCount,
      pulledCount = pullBody.messages.size,
      conflictCount = conflictCount,
    )
  }
}
