package com.Khalil.trackster.ui.business

/**
 * One row in the business owner's live queue, read back from Firestore.
 *
 * [queueNumber] drives the display ("#1", "#2") and the ordering of the queue,
 * as well as which "waiting" entry is next up for "Call Next".
 */
data class QueueEntry(
    val id: String,
    val queueNumber: Int,
    val customerName: String,
    val serviceType: String,
    val status: String,
    val appointmentDate: String,
    val appointmentTime: String,
    val priceDisplay: String = ""
)
