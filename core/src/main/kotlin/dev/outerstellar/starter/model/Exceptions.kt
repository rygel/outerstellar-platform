package dev.outerstellar.starter.model

sealed class OuterstellarException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class MessageNotFoundException(syncId: String) : 
    OuterstellarException("Message with sync ID $syncId was not found.")

class DuplicateMessageException(syncId: String) : 
    OuterstellarException("A message with sync ID $syncId already exists.")

class SyncConflictException(syncId: String, val reason: String) : 
    OuterstellarException("Sync conflict for message $syncId: $reason")

class ValidationException(val errors: List<String>) : 
    OuterstellarException("Validation failed: ${errors.joinToString(", ")}")

class SyncException(message: String, cause: Throwable? = null) : 
    OuterstellarException(message, cause)
