package com.Khalil.trackster.ui.customer

enum class BusinessFilter { ALL, OPEN, TOP_RATED, FAVORITES }

object BusinessFiltering {
    // Purpose: Filters and sorts businesses by query, favorites, availability, and service criteria.
    fun apply(
        businesses: List<Business>,
        query: String,
        filter: BusinessFilter
    ): List<Business> {
        val normalized = query.trim().lowercase()
        return businesses.filter { business ->
            val matchesQuery = normalized.isBlank() ||
                business.name.lowercase().contains(normalized) ||
                business.services.any { it.lowercase().contains(normalized) }
            val matchesFilter = when (filter) {
                BusinessFilter.ALL -> true
                BusinessFilter.OPEN -> business.isQueueOpen
                BusinessFilter.TOP_RATED -> business.reviewCount > 0 && business.averageRating >= 4.0
                BusinessFilter.FAVORITES -> business.isFavorite
            }
            matchesQuery && matchesFilter
        }.sortedWith(
            compareByDescending<Business> { it.isFavorite }
                .thenByDescending { it.averageRating }
                .thenBy { it.name.lowercase() }
        )
    }
}
