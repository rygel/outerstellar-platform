package dev.outerstellar.starter.model

enum class ConflictStrategy {
    MINE,
    SERVER;

    companion object {
        fun fromString(value: String): ConflictStrategy =
            when (value.lowercase()) {
                "mine" -> MINE
                "server" -> SERVER
                else -> SERVER
            }
    }
}

sealed class OuterstellarException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class MessageNotFoundException(syncId: String) :
    OuterstellarException("Message with sync ID $syncId was not found.")

class ContactNotFoundException(syncId: String) :
    OuterstellarException("Contact with sync ID $syncId was not found.")

class DuplicateMessageException(syncId: String) :
    OuterstellarException("A message with sync ID $syncId already exists.")

class SyncConflictException(syncId: String, val reason: String) :
    OuterstellarException("Sync conflict for message $syncId: $reason")

class ValidationException(val errors: List<String>) :
    OuterstellarException("Validation failed: ${errors.joinToString(", ")}")

class OptimisticLockException(entityType: String, syncId: String) :
    OuterstellarException("$entityType with sync ID $syncId was modified by another process.")

class SyncException(message: String, cause: Throwable? = null) :
    OuterstellarException(message, cause)

class UsernameAlreadyExistsException(username: String) :
    OuterstellarException("Username '$username' is already taken.")

class WeakPasswordException(message: String) : OuterstellarException(message)
