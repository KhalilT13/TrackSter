package com.Khalil.trackster.ui.customer

/**
 * One row in the customer's "My Appointments" list, read back from Firestore.
 *
 * [queueNumber] is the sequential position assigned per business at booking time.
 * [status] is one of "waiting", "in_service" or "completed" (older records may only
 * ever be "waiting"/"completed" - both still render fine).
 */
data class Appointment(
    val id: String,
    val businessId: String,
    val businessName: String,
    val serviceType: String,
    val status: String,
    val queueNumber: Int,
    val appointmentDate: String,
    val appointmentTime: String,
    val createdAtMillis: Long = 0L,
    val hasReview: Boolean = false,
    val customerName: String = "",
    val priceDisplay: String = "",
    val bookingSlotIds: List<String> = emptyList(),
    val appointmentDateKey: String = "",
    val appointmentStartMinutes: Int = 0,
    val appointmentEndMinutes: Int = 0,
    val serviceId: String = "",
    val serviceDurationMinutes: Int = 30
)
