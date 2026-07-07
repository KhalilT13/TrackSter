package com.Khalil.trackster.ui.customer

/** One row in the customer's "My Appointments" list, read back from Firestore. */
data class Appointment(
    val businessName: String,
    val serviceType: String,
    val status: String
)
