package com.Khalil.trackster

import com.Khalil.trackster.ui.customer.Business
import com.Khalil.trackster.ui.customer.BusinessFilter
import com.Khalil.trackster.ui.customer.BusinessFiltering
import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessFilteringTest {
    private val businesses = listOf(
        Business("1", "North Clinic", listOf("Checkup"), isQueueOpen = true, averageRating = 4.7, reviewCount = 8),
        Business("2", "City Garage", listOf("Oil Change"), isQueueOpen = false, averageRating = 3.9, reviewCount = 2, isFavorite = true),
        Business("3", "Fresh Spa", listOf("Massage"), isQueueOpen = true)
    )

    @Test fun searchMatchesBusinessAndServiceNames() {
        assertEquals(listOf("1"), BusinessFiltering.apply(businesses, "clinic", BusinessFilter.ALL).map { it.id })
        assertEquals(listOf("2"), BusinessFiltering.apply(businesses, "oil", BusinessFilter.ALL).map { it.id })
    }

    @Test fun filtersOpenRatedAndFavorites() {
        assertEquals(setOf("1", "3"), BusinessFiltering.apply(businesses, "", BusinessFilter.OPEN).map { it.id }.toSet())
        assertEquals(listOf("1"), BusinessFiltering.apply(businesses, "", BusinessFilter.TOP_RATED).map { it.id })
        assertEquals(listOf("2"), BusinessFiltering.apply(businesses, "", BusinessFilter.FAVORITES).map { it.id })
    }
}
