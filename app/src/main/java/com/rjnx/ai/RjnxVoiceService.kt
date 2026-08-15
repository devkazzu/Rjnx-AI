package com.rjnx.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class RjnxVoiceService : Service() {

    companion object {
        const val ACTION_CHAT_VOICE_RESULT = "com.rjnx.ai.CHAT_VOICE_RESULT"
        const val EXTRA_CHAT_LISTEN = "MIO_CHAT_LISTEN"

        private const val CHANNEL_ID = "rjnx_background"
        private const val NOTIFICATION_ID = 3001
        private const val MODEL_DIR = "vosk-model-small-en-in-0.4"
        private const val SAMPLE_RATE = 16000f
    }

    private var model: Model? = null
    private var audioRecord: AudioRecord? = null
    private var wakeRecognizer: Recognizer? = null
    private var commandRecognizer: Recognizer? = null
    private val running = AtomicBoolean(false)
    private var commandMode = false
    private var chatListenRequested = false
    private var tapRequested = false

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

        Thread {
            try {
                prepareModel()

                if (chatListenRequested || tapRequested) {
                    enterCommandMode()
                    if (tapRequested && !chatListenRequested) announceWake()
                }

                startAudioLoop()
            } catch (_: Exception) {
                stopAudioLoop()
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_CHAT_LISTEN, false) == true) {
            chatListenRequested = true
            tapRequested = false
            if (model != null) enterCommandMode()
        }

        if (intent?.getBooleanExtra("MIO_TAP_TO_SPEAK", false) == true) {
            tapRequested = true
            chatListenRequested = false
            if (model != null) {
                enterCommandMode()
                announceWake()
            }
        }

        return START_STICKY
    }

    private fun prepareModel() {
        val modelDir = File(filesDir, MODEL_DIR)
        if (!modelDir.exists()) copyAssetFolder(MODEL_DIR, modelDir)

        model = Model(modelDir.absolutePath)
        wakeRecognizer = Recognizer(
            model,
            SAMPLE_RATE,
            """["hey mio", "mio", "[unk]"]"""
        )
    }

    private fun startAudioLoop() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, 4096)
        )

        running.set(true)
        audioRecord?.startRecording()

        val buffer = ShortArray(2048)

        while (running.get()) {
            val count = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (count <= 0) continue

            if (commandMode) {
                val accepted = commandRecognizer?.acceptWaveForm(buffer, count) == true
                if (accepted) {
                    val text = parseText(commandRecognizer?.getResult()?.toString() ?: "")
                    if (text.isNotBlank()) {
                        if (chatListenRequested) {
                            deliverChatVoice(text)
                        } else {
                            deliverCommand(text)
                        }
                        enterWakeMode()
                    }
                }
            } else {
                val accepted = wakeRecognizer?.acceptWaveForm(buffer, count) == true
                if (accepted) {
                    val text = parseText(wakeRecognizer?.getResult()?.toString() ?: "")
                    if (isMioWake(text)) {
                        enterCommandMode()
                        announceWake()
                    }
                } else {
                    val partial = parseText(wakeRecognizer?.partialResult?.toString() ?: "")
                    if (isMioWake(partial)) {
                        enterCommandMode()
                        announceWake()
                    }
                }
            }
        }
    }

    private fun deliverChatVoice(text: String) {
        updateNotification("Mio heard: $text")
        sendBroadcast(Intent(ACTION_CHAT_VOICE_RESULT).apply {
            setPackage(packageName)
            putExtra("MIO_VOICE_TEXT", text)
        })
        chatListenRequested = false
        tapRequested = false
    }

    private fun announceWake() {
        updateNotification("Mio heard you — listening…")

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("MIO_WAKE", true)
            putExtra("MIO_COMMAND", "")
        }

        try { startActivity(intent) } catch (_: Exception) {}
    }

    private fun enterCommandMode() {
        commandMode = true
        commandRecognizer?.close()
        commandRecognizer = model?.let { Recognizer(it, SAMPLE_RATE) }
        updateNotification(
            if (chatListenRequested) "Mio is listening for Chat…"
            else "Mio is listening…"
        )
    }

    private fun enterWakeMode() {
        commandMode = false
        commandRecognizer?.close()
        commandRecognizer = null
        wakeRecognizer?.reset()
        chatListenRequested = false
        tapRequested = false
        updateNotification("Mio is ready in the background")
    }

    private fun deliverCommand(command: String) {
        updateNotification("Mio heard you")

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("MIO_WAKE", true)
            putExtra("MIO_COMMAND", command)
        }

        try { startActivity(intent) } catch (_: Exception) {}
    }

    private fun isMioWake(text: String): Boolean {
        val normalized = text.lowercase().trim()
        return normalized == "mio" ||
            normalized == "hey mio" ||
            normalized.contains("hey mio") ||
            normalized.endsWith(" mio") ||
            normalized.startsWith("mio ")
    }

    private fun parseText(json: String): String = try {
        JSONObject(json).optString("text").trim()
    } catch (_: Exception) {
        ""
    }

    private fun copyAssetFolder(assetFolder: String, destination: File) {
        destination.mkdirs()
        val children = assets.list(assetFolder).orEmpty()

        if (children.isEmpty()) {
            assets.open(assetFolder).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            return
        }

        for (child in children) {
            val childAsset = "$assetFolder/$child"
            val childDest = File(destination, child)
            val nested = assets.list(childAsset).orEmpty()

            if (nested.isNotEmpty()) {
                copyAssetFolder(childAsset, childDest)
            } else {
                assets.open(childAsset).use { input ->
                    FileOutputStream(childDest).use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun stopAudioLoop() {
        running.set(false)
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
        commandRecognizer?.close()
        wakeRecognizer?.close()
        commandRecognizer = null
        wakeRecognizer = null
        model?.close()
        model = null
    }

    override fun onDestroy() {
        stopAudioLoop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "RJNX Background Assistant",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps Mio ready for background assistant features."
                }
            )
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RJNX AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
