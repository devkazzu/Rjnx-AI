package com.rjnx.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * V3.0 Phase 2.1
 *
 * Background service shell only.
 * It intentionally does NOT keep SpeechRecognizer running continuously.
 * That prevents the microphone from repeatedly switching on/off.
 *
 * The actual low-power "Hey Mio" wake-word engine will be plugged into this
 * service as the next isolated component.
 */
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
            .setContentText("Mio is ready in the background")
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
        // No continuous microphone loop here.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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
                description = "Keeps Mio ready for background assistant features."
            }
            manager.createNotificationChannel(channel)
        }
    }
}
