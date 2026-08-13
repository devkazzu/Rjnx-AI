package com.rjnx.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class RjnxAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "rjnx_alarm"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "RJNX AI Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            7001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, channelId)
        } else {
            android.app.Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("RJNX AI Alarm")
            .setContentText(intent?.getStringExtra("message") ?: "Alarm")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .build()

        manager.notify(7001, notification)
    }
}
