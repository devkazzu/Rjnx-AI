package com.rjnx.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tapToSpeak: TextView
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_glass_orbit)

        tts = TextToSpeech(this) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (isTtsReady) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.96f)
                tts?.setPitch(1.0f)
            }
        }

        tapToSpeak = findViewById(R.id.btn_tap_to_speak)
        tapToSpeak.setOnClickListener {
            startTapToSpeak()
        }

        findViewById<View>(R.id.nav_chats).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        handleVoiceIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("MIO_WAKE", false) != true) return

        val command = intent.getStringExtra("MIO_COMMAND").orEmpty().trim()

        if (command.isBlank()) {
            tapToSpeak.text = "🎙  Listening..."
            return
        }

        tapToSpeak.text = "🎙  Heard: $command"
        askMio(command)
    }

    private fun askMio(command: String) {
        tapToSpeak.text = "🤖  Thinking..."

        OpenRouterClient.ask(command) { answer ->
            tapToSpeak.text = "🤖  $answer"
            speak(answer)
        }
    }

    private fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        val clean = text
            .replace(Regex("[*_`#]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "mio_voice_reply")
    }

    private fun startTapToSpeak() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }

        tapToSpeak.text = "🎙  Listening..."

        val serviceIntent = Intent(this, RjnxVoiceService::class.java).apply {
            putExtra("MIO_TAP_TO_SPEAK", true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startTapToSpeak()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 401
    }
}
