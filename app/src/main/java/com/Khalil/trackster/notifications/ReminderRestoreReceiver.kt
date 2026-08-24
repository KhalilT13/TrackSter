package com.Khalil.trackster.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReminderRestoreReceiver : BroadcastReceiver() {
    // Purpose: Handles the received Android system event and performs its reminder-related action.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val pending = goAsync()
        val scheduler = AppointmentReminderScheduler(context.applicationContext)
        FirebaseFirestore.getInstance().collection("appointments").whereEqualTo("customerId", uid).get()
            .addOnCompleteListener { task ->
                task.result?.documents.orEmpty().forEach { appointment ->
                    if (appointment.getString("status") == "waiting") {
                        scheduler.schedule(
                            appointment.id,
                            appointment.getString("appointmentDateKey").orEmpty(),
                            appointment.getLong("appointmentStartMinutes")?.toInt() ?: 0,
                            appointment.getString("businessName").orEmpty(),
                            appointment.getString("serviceType").orEmpty(),
                            appointment.getString("appointmentTime").orEmpty()
                        )
                    }
                }
                pending.finish()
            }
    }
}
