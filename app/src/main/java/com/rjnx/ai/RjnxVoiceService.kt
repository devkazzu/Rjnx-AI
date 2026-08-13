package com.rjnx.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class RjnxVoiceService : Service() {

    companion object {
        private const val CHANNEL_ID = "rjnx_background"
        private const val NOTIFICATION_ID = 3001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RJNX AI")
            .setContentText("RJNX is running in the background")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // Phase 1: keep the service alive.
        // Microphone capture and wake-word detection are added in the next phase.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RJNX Background Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps RJNX ready for background assistant features."
            }
            manager.createNotificationChannel(channel)
        }
    }
}
