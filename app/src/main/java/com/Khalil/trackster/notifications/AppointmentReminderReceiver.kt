package com.Khalil.trackster.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.Khalil.trackster.MainActivity
import com.Khalil.trackster.R

class AppointmentReminderReceiver : BroadcastReceiver() {
    // Purpose: Handles the received Android system event and performs its reminder-related action.
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val type = intent.getStringExtra(AppointmentReminderScheduler.EXTRA_TYPE)
        val business = intent.getStringExtra(AppointmentReminderScheduler.EXTRA_BUSINESS_NAME).orEmpty()
        val service = intent.getStringExtra(AppointmentReminderScheduler.EXTRA_SERVICE_NAME).orEmpty()
        val time = intent.getStringExtra(AppointmentReminderScheduler.EXTRA_TIME_LABEL).orEmpty()
        val title = context.getString(if (type == AppointmentReminderScheduler.TYPE_DAY) R.string.notification_day_before_title else R.string.notification_hour_before_title)
        val body = context.getString(if (type == AppointmentReminderScheduler.TYPE_DAY) R.string.notification_day_before_body else R.string.notification_hour_before_body, service, time, business)
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val id = (intent.getStringExtra(AppointmentReminderScheduler.EXTRA_APPOINTMENT_ID).orEmpty() + type).hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_appointments)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    // Purpose: Creates the required Android notification channel when it does not already exist.
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_appointments), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notification_channel_appointments_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object { private const val CHANNEL_ID = "appointment_reminders" }
}
