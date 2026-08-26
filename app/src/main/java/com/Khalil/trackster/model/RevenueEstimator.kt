package com.Khalil.trackster.model

/**
 * Converts the price snapshot stored on a completed appointment into an estimated
 * revenue value. Range-priced services use their midpoint because the appointment
 * does not currently store the final amount charged by the business.
 *
 * [priceDisplay] is used only as a backwards-compatible fallback for appointments
 * created before the structured price fields were added.
 */
object RevenueEstimator {
    // Purpose: Calculates estimated revenue from completed appointments and their recorded pricing data.
    fun estimate(
        priceType: String?,
        minimumPrice: Number?,
        maximumPrice: Number?,
        priceDisplay: String?
    ): Double {
        val minimum = minimumPrice?.toDouble()?.takeIf { it >= 0.0 }
        val maximum = maximumPrice?.toDouble()?.takeIf { it >= 0.0 }

        val structuredEstimate = when (priceType) {
            ServiceOffering.PRICE_FIXED -> minimum ?: maximum
            ServiceOffering.PRICE_RANGE -> midpoint(minimum, maximum)
            ServiceOffering.PRICE_CONTACT -> null
            else -> midpoint(minimum, maximum)
        }
        return structuredEstimate ?: estimateFromDisplay(priceDisplay).coerceAtLeast(0.0)
    }

    // Purpose: Returns the midpoint of a valid price range for revenue estimation.
    private fun midpoint(minimum: Double?, maximum: Double?): Double? = when {
        minimum != null && maximum != null -> (minimum + maximum) / 2.0
        minimum != null -> minimum
        else -> maximum
    }

    // Purpose: Extracts a numeric estimate from legacy display-price text when structured pricing is unavailable.
    private fun estimateFromDisplay(display: String?): Double {
        val values = NUMBER.findAll(display.orEmpty()).mapNotNull { match ->
            match.value.replace(",", "").toDoubleOrNull()
        }.toList()
        return when {
            values.size >= 2 -> (values[0] + values[1]) / 2.0
            values.size == 1 -> values[0]
            else -> 0.0
        }
    }

    private val NUMBER = Regex("""\d[\d,]*(?:\.\d+)?""")
}
