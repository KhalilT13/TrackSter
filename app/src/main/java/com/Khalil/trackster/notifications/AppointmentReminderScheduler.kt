package com.Khalil.trackster.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AppointmentReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    // Purpose: Schedules all local reminders required for a future appointment.
    fun schedule(
        appointmentId: String,
        dateKey: String,
        startMinutes: Int,
        businessName: String,
        serviceName: String,
        timeLabel: String
    ) {
        val start = appointmentMillis(dateKey, startMinutes) ?: return
        scheduleOne(appointmentId, TYPE_DAY, start - DAY_MILLIS, businessName, serviceName, timeLabel)
        scheduleOne(appointmentId, TYPE_HOUR, start - HOUR_MILLIS, businessName, serviceName, timeLabel)
    }

    // Purpose: Cancels every local reminder associated with an appointment.
    fun cancel(appointmentId: String) {
        listOf(TYPE_DAY, TYPE_HOUR).forEach { type -> alarms.cancel(pendingIntent(appointmentId, type, null)) }
    }

    // Purpose: Schedules one appointment alarm for the requested trigger time and reminder type.
    private fun scheduleOne(
        appointmentId: String,
        type: String,
        triggerAt: Long,
        businessName: String,
        serviceName: String,
        timeLabel: String
    ) {
        val pending = pendingIntent(appointmentId, type, Intent(context, AppointmentReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
            putExtra(EXTRA_BUSINESS_NAME, businessName)
            putExtra(EXTRA_SERVICE_NAME, serviceName)
            putExtra(EXTRA_TIME_LABEL, timeLabel)
        })
        if (triggerAt > System.currentTimeMillis()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        else alarms.cancel(pending)
    }

    // Purpose: Creates a stable PendingIntent that uniquely identifies an appointment reminder.
    private fun pendingIntent(appointmentId: String, type: String, source: Intent?): PendingIntent {
        val intent = source ?: Intent(context, AppointmentReminderReceiver::class.java)
        intent.action = "com.Khalil.trackster.APPOINTMENT_REMINDER.$appointmentId.$type"
        return PendingIntent.getBroadcast(context, (appointmentId + type).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // Purpose: Converts an appointment date and start time into epoch milliseconds.
    private fun appointmentMillis(dateKey: String, startMinutes: Int): Long? = runCatching {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey) ?: return@runCatching null
        Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, startMinutes / 60)
            set(Calendar.MINUTE, startMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }.getOrNull()

    companion object {
        const val EXTRA_TYPE = "reminder_type"
        const val EXTRA_APPOINTMENT_ID = "appointment_id"
        const val EXTRA_BUSINESS_NAME = "business_name"
        const val EXTRA_SERVICE_NAME = "service_name"
        const val EXTRA_TIME_LABEL = "time_label"
        const val TYPE_DAY = "day"
        const val TYPE_HOUR = "hour"
        private const val HOUR_MILLIS = 60 * 60 * 1000L
        private const val DAY_MILLIS = 24 * HOUR_MILLIS
    }
}
