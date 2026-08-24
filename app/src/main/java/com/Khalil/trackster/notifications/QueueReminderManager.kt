package com.Khalil.trackster.notifications

import android.content.Context
import com.Khalil.trackster.ui.customer.QueueMath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class QueueReminderManager(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val notifications = QueueNotificationHelper(context.applicationContext)
    private val appointmentReminders = AppointmentReminderScheduler(context.applicationContext)
    private val preferences = context.getSharedPreferences("queue_reminders", Context.MODE_PRIVATE)
    private var appointmentListener: ListenerRegistration? = null
    private var counterListener: ListenerRegistration? = null
    private var activeAppointmentId: String? = null

    // Purpose: Starts real-time listeners for the customer's active appointment and business queue counter.
    fun start(customerId: String) {
        stop(); notifications.createChannel()
        appointmentListener = firestore.collection("appointments").whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.documents.orEmpty().forEach { appointment ->
                    val id = appointment.id
                    if (appointment.getString("status") == "waiting") {
                        appointmentReminders.schedule(
                            id,
                            appointment.getString("appointmentDateKey").orEmpty(),
                            appointment.getLong("appointmentStartMinutes")?.toInt() ?: 0,
                            appointment.getString("businessName").orEmpty(),
                            appointment.getString("serviceType").orEmpty(),
                            appointment.getString("appointmentTime").orEmpty()
                        )
                    } else appointmentReminders.cancel(id)
                }
                val active = snapshot?.documents.orEmpty().filter {
                    it.getString("status") in listOf("waiting", "in_service")
                }.sortedBy { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }.firstOrNull()
                if (active == null) { activeAppointmentId = null; counterListener?.remove(); counterListener = null; return@addSnapshotListener }
                val id = active.id; val businessName = active.getString("businessName").orEmpty()
                if (active.getString("status") == "in_service") {
                    sendOnce("turn_$id") { notifications.yourTurn(id, businessName) }
                    counterListener?.remove(); counterListener = null; activeAppointmentId = id; return@addSnapshotListener
                }
                if (activeAppointmentId == id && counterListener != null) return@addSnapshotListener
                activeAppointmentId = id; counterListener?.remove()
                val businessId = active.getString("businessId").orEmpty(); val queueNumber = active.getLong("queueNumber")?.toInt() ?: return@addSnapshotListener
                counterListener = firestore.collection("queueCounters").document(businessId).addSnapshotListener { counter, _ ->
                    val peopleAhead = QueueMath.peopleAhead(queueNumber, counter?.getLong("currentServingNumber")?.toInt() ?: 0)
                    if (peopleAhead in 0..2) sendOnce("soon_${id}_$peopleAhead") { notifications.almostTurn(id, peopleAhead, businessName) }
                }
            }
    }

    // Purpose: Removes queue listeners and clears the active reminder-tracking state.
    fun stop() { appointmentListener?.remove(); counterListener?.remove(); appointmentListener = null; counterListener = null; activeAppointmentId = null }

    // Purpose: Deduplicates queue alerts so each alert type is sent once per appointment.
    private inline fun sendOnce(key: String, action: () -> Unit) {
        if (!preferences.getBoolean(key, false)) { action(); preferences.edit().putBoolean(key, true).apply() }
    }
}
