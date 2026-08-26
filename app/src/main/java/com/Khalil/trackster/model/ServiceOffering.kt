package com.Khalil.trackster.model

import java.text.DecimalFormat
import java.util.UUID

data class ServiceOffering(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val priceType: String = PRICE_CONTACT,
    val minimumPrice: Double? = null,
    val maximumPrice: Double? = null,
    val durationMinutes: Int = 30,
    val active: Boolean = true
) {
    // Purpose: Returns the customer-facing price label for fixed, ranged, or starting prices.
    fun priceLabel(): String = when (priceType) {
        PRICE_FIXED -> minimumPrice?.let { "₪${PRICE_FORMAT.format(it)}" } ?: "Price not provided"
        PRICE_RANGE -> if (minimumPrice != null && maximumPrice != null) {
            "₪${PRICE_FORMAT.format(minimumPrice)}–₪${PRICE_FORMAT.format(maximumPrice)}"
        } else "Price not provided"
        else -> "Contact for price"
    }

    // Purpose: Returns a complete booking label containing the service name, price, and duration.
    fun bookingLabel(): String = "$name · ${priceLabel()} · $durationMinutes min"

    // Purpose: Converts this model into a map suitable for Firestore storage.
    fun toMap(): Map<String, Any> = buildMap {
        put("id", id)
        put("name", name)
        put("priceType", priceType)
        minimumPrice?.let { put("minimumPrice", it) }
        maximumPrice?.let { put("maximumPrice", it) }
        put("durationMinutes", durationMinutes)
        put("active", active)
    }

    companion object {
        const val PRICE_FIXED = "fixed"
        const val PRICE_RANGE = "range"
        const val PRICE_CONTACT = "contact"
        private val PRICE_FORMAT = DecimalFormat("#,##0.##")

        // Purpose: Converts a Firestore map into a validated app model.
        fun fromMap(raw: Map<*, *>): ServiceOffering? {
            val name = raw["name"]?.toString()?.trim().orEmpty()
            if (name.isBlank()) return null
            return ServiceOffering(
                id = raw["id"]?.toString()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                name = name,
                priceType = raw["priceType"]?.toString()?.takeIf {
                    it in setOf(PRICE_FIXED, PRICE_RANGE, PRICE_CONTACT)
                } ?: PRICE_CONTACT,
                minimumPrice = (raw["minimumPrice"] as? Number)?.toDouble(),
                maximumPrice = (raw["maximumPrice"] as? Number)?.toDouble(),
                durationMinutes = ((raw["durationMinutes"] as? Number)?.toInt() ?: 30).coerceIn(5, 480),
                active = raw["active"] as? Boolean ?: true
            )
        }

        // Purpose: Converts raw Firestore data into the structured model used by the app.
        fun fromFirestore(rawCatalog: Any?, legacyNames: List<String>): List<ServiceOffering> {
            val catalog = (rawCatalog as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::fromMap) }.orEmpty()
            if (catalog.isNotEmpty()) return catalog
            return legacyNames.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
                .map { ServiceOffering(name = it) }
        }
    }
}
