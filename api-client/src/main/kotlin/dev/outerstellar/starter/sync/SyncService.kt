package dev.outerstellar.starter.sync

import dev.outerstellar.starter.model.AuthTokenResponse
import dev.outerstellar.starter.model.LoginRequest
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.service.SyncProvider
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson.asA
import org.http4k.format.Jackson.asFormatString
import org.slf4j.LoggerFactory

class SyncService(
  private val baseUrl: String,
  private val repository: MessageRepository,
  private val outboxRepository: OutboxRepository,
  private val transactionManager: TransactionManager,
  private val client: HttpHandler = JavaHttpClient(),
) : SyncProvider {
  private val logger = LoggerFactory.getLogger(SyncService::class.java)
  private var apiToken: String? = null

  fun login(username: String, pass: String): AuthTokenResponse {
      val response = client(Request(POST, "$baseUrl/api/v1/auth/login")
          .header("content-type", "application/json")
          .body(asFormatString(LoginRequest(username, pass))))
      
      if (response.status == Status.OK) {
          val auth = asA(response.bodyString(), AuthTokenResponse::class)
          this.apiToken = auth.token
          return auth
      } else {
          throw RuntimeException("Login failed: ${response.status}")
      }
  }

  fun logout() {
      this.apiToken = null
  }

  override fun sync(): dev.outerstellar.starter.sync.SyncStats {
    val lastSync = repository.getLastSyncEpochMs()
    logger.info("Starting sync since {}", lastSync)

    // 1. Pull
    val pullRequest = Request(GET, "$baseUrl/api/v1/sync?since=$lastSync")
    val authenticatedPullRequest = apiToken?.let { pullRequest.header("Authorization", "Bearer $it") } ?: pullRequest
    
    val pullResponse = client(authenticatedPullRequest)
    if (pullResponse.status != Status.OK) {
        throw RuntimeException("Pull failed: ${pullResponse.status} - ${pullResponse.bodyString()}")
    }
    
    val pullBody = asA(pullResponse.bodyString(), SyncPullResponse::class)

    // 2. Push
    val dirtyMessages = repository.listDirtyMessages()
    val pushRequest = SyncPushRequest(dirtyMessages.map { it.toSyncMessage() })
    
    val httpRequest = Request(POST, "$baseUrl/api/v1/sync")
        .header("content-type", "application/json")
        .body(asFormatString(pushRequest))
    
    val authenticatedPushRequest: Request = apiToken?.let { httpRequest.header("Authorization", "Bearer $it") } ?: httpRequest
    
    val pushResponse = client(authenticatedPushRequest)
    if (pushResponse.status != Status.OK) {
        throw RuntimeException("Push failed: ${pushResponse.status} - ${pushResponse.bodyString()}")
    }
    
    val pushBody = asA(pushResponse.bodyString(), SyncPushResponse::class)

    // 3. Process results
    var conflictCount = pushBody.conflicts.size
    val pushedCount = pushBody.appliedCount

    transactionManager.inTransaction {
      pullBody.messages.forEach { incoming ->
        repository.upsertSyncedMessage(incoming, dirty = false)
      }
      pushBody.conflicts.forEach { conflict ->
        if (conflict.serverMessage != null) {
            repository.upsertSyncedMessage(conflict.serverMessage!!, dirty = false)
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
