package com.Khalil.trackster.ui.customer

import androidx.annotation.DrawableRes
import com.Khalil.trackster.R
import com.Khalil.trackster.model.ServiceOffering
import com.Khalil.trackster.model.DaySchedule

/**
 * A business/service loaded from the public Firestore directory.
 *
 * [iconRes] is the drawable shown in the item's circular badge; it defaults to a
 * generic business icon so real Firestore data (which won't carry an icon) still renders.
 */
data class Business(
    val id: String,
    val name: String,
    val services: List<String>,
    val photoUrls: List<String> = emptyList(),
    val openingHours: String = "",
    val isQueueOpen: Boolean = true,
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
    val isFavorite: Boolean = false,
    @param:DrawableRes val iconRes: Int = R.drawable.ic_business,
    val serviceCatalog: List<ServiceOffering> = services.map { ServiceOffering(name = it) },
    val logoUrl: String = "",
    val weeklySchedule: Map<String, DaySchedule> = emptyMap()
) {
    val serviceType: String
        get() = services.firstOrNull() ?: "General Service"
}
