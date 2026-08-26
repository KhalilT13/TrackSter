package com.Khalil.trackster.ui.customer

data class Review(
    val appointmentId: String,
    val customerName: String,
    val rating: Int,
    val comment: String,
    val createdAtMillis: Long = 0L
)
