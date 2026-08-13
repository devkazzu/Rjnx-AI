package com.rjnx.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class RjnxVoiceService : Service() {

    companion object {
        private const val CHANNEL_ID = "rjnx_background"
        private const val NOTIFICATION_ID = 3001
    }

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RJNX AI")
            .setContentText("Listening for “Hey Mio”")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        startWakeWordListening()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!listening) startWakeWordListening()
        return START_STICKY
    }

    private fun startWakeWordListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    listening = false
                }

                override fun onError(error: Int) {
                    listening = false
                    restartWakeWordListening()
                }

                override fun onResults(results: Bundle?) {
                    listening = false

                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    ).orEmpty()

                    val phrase = matches.firstOrNull().orEmpty()
                    val normalized = phrase.lowercase(Locale.getDefault()).trim()

                    if (normalized.contains("hey mio") ||
                        normalized == "mio" ||
                        normalized.startsWith("mio ")
                    ) {
                        val command = normalized
                            .replaceFirst(Regex("^.*?hey\\s+mio\\s*"), "")
                            .replaceFirst(Regex("^mio\\s*"), "")
                            .trim()

                        val activityIntent = Intent(this@RjnxVoiceService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("MIO_WAKE", true)
                            putExtra("MIO_COMMAND", command)
                        }

                        startActivity(activityIntent)
                    }

                    restartWakeWordListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            recognizer?.startListening(intent)
            listening = true
        } catch (_: Exception) {
            listening = false
            restartWakeWordListening()
        }
    }

    private fun restartWakeWordListening() {
        android.os.Handler(mainLooper).postDelayed(
            { startWakeWordListening() },
            800L
        )
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        listening = false
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
            )
            manager.createNotificationChannel(channel)
        }
    }
}
