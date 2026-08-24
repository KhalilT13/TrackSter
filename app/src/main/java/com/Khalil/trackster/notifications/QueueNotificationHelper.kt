package com.Khalil.trackster.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.Khalil.trackster.R

class QueueNotificationHelper(private val context: Context) {
    // Purpose: Creates the required Android notification channel when it does not already exist.
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_queue), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notification_channel_queue_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // Purpose: Shows a notification when only a small number of customers remain ahead in the queue.
    fun almostTurn(appointmentId: String, peopleAhead: Int, businessName: String) {
        notify(appointmentId.hashCode(), context.getString(R.string.notification_almost_turn_title),
            context.getString(R.string.notification_almost_turn_body, peopleAhead, businessName))
    }

    // Purpose: Shows a notification when the business starts serving the customer.
    fun yourTurn(appointmentId: String, businessName: String) {
        notify(appointmentId.hashCode(), context.getString(R.string.notification_your_turn_title),
            context.getString(R.string.notification_your_turn_body, businessName))
    }

    // Purpose: Builds and posts an Android notification that opens Trackster when tapped.
    private fun notify(id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_queue)
            .setContentTitle(title).setContentText(body).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object { private const val CHANNEL_ID = "queue_reminders" }
}
