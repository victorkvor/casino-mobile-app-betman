package com.example.betman

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.app.PendingIntent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Get the notification phrases from intent
        val notificationPhrases = intent.getStringArrayExtra("PHRASES")

        // Choose random phrase from list
        val randomPhrase = notificationPhrases?.random() ?: "🎰 Your luck awaits at Gotham Casino!"

        // Create intent to launch the SplashActivity when notification is clicked
        val openAppIntent = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Create PendingIntent to wrap openAppIntent
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification with random phrase and add the PendingIntent
        val notification = NotificationCompat.Builder(context, "casino_channel")
            .setSmallIcon(R.drawable.logo) // Make sure this icon exists
            .setContentTitle("🎰 Time to play!") // Notification title
            .setContentText(randomPhrase) // Notification content text
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent) // Set the intent to open the app
            .setAutoCancel(true) // Dismiss the notification when clicked
            .build()

        // Get the notification manager and display the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification) // Show the notification with ID 1
    }
}