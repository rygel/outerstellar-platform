package io.github.rygel.stellar.model

import java.util.UUID

sealed class StellarException(message: String) : RuntimeException(message)

class CommunityNotFoundException(name: String) : StellarException("Community '$name' not found")

class PostNotFoundException(id: UUID) : StellarException("Post $id not found")

class CommentNotFoundException(id: UUID) : StellarException("Comment $id not found")

class DuplicateCommunityException(name: String) : StellarException("Community '$name' already exists")

class NotModeratorException(community: String) : StellarException("Not a moderator of '$community'")

class BannedFromCommunityException(community: String) : StellarException("Banned from '$community'")

class ReportNotFoundException(id: UUID) : StellarException("Report $id not found")
