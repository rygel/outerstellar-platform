package dev.outerstellar.platform.model

import kotlin.math.ceil

data class PaginationMetadata(val currentPage: Int, val pageSize: Int, val totalItems: Long) {
    val totalPages: Int = ceil(totalItems.toDouble() / pageSize).toInt()
    val hasPrevious = currentPage > 1
    val hasNext = currentPage < totalPages
    val previousPage = if (hasPrevious) currentPage - 1 else null
    val nextPage = if (hasNext) currentPage + 1 else null
}

data class PagedResult<T>(val items: List<T>, val metadata: PaginationMetadata)
