package com.Khalil.trackster.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.Khalil.trackster.model.AppointmentReferences
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot

class ReminderRestoreReceiver : BroadcastReceiver() {
    // Purpose: Handles the received Android system event and performs its reminder-related action.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val pending = goAsync()
        val scheduler = AppointmentReminderScheduler(context.applicationContext)
        FirebaseFirestore.getInstance().collection("appointments").whereEqualTo("customerId", uid).get()
            .addOnCompleteListener { task ->
                val appointments = task.result?.documents.orEmpty().filter { it.getString("status") == "waiting" }
                val refs = appointments.map { appointment ->
                    FirebaseFirestore.getInstance().collection("businesses").document(appointment.getString("businessId").orEmpty())
                }
                Tasks.whenAllSuccess<DocumentSnapshot>(refs.map { it.get() }).addOnCompleteListener { businessesTask ->
                    val loadedBusinesses = if (businessesTask.isSuccessful) businessesTask.result.orEmpty() else emptyList()
                    val businesses = loadedBusinesses.associateBy { it.id }
                    appointments.forEach { appointment ->
                    if (appointment.getString("status") == "waiting") {
                        val business = businesses[appointment.getString("businessId").orEmpty()]
                        @Suppress("UNCHECKED_CAST") val legacy = (business?.get("services") as? List<String>).orEmpty()
                        scheduler.schedule(
                            appointment.id,
                            appointment.getString("appointmentDateKey").orEmpty(),
                            appointment.getLong("appointmentStartMinutes")?.toInt() ?: 0,
                            business?.getString("businessName").orEmpty().ifBlank { appointment.getString("businessName").orEmpty() },
                            AppointmentReferences.serviceName(appointment.getString("serviceId").orEmpty(), business?.get("serviceCatalog"), legacy)
                                .ifBlank { appointment.getString("serviceType").orEmpty() },
                            appointment.getString("appointmentTime").orEmpty()
                        )
                    }
                }
                pending.finish()
                }
            }
    }
}
