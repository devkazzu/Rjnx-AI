package com.rjnx.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
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

        // STEP 4A — Home feature buttons
        findViewById<View>(R.id.action_voice).setOnClickListener {
            startTapToSpeak()
        }

        findViewById<View>(R.id.action_vision).setOnClickListener {
            openCamera()
        }

        findViewById<View>(R.id.action_translate).setOnClickListener {
            openTranslate()
        }

        findViewById<View>(R.id.action_chat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<View>(R.id.action_youtube).setOnClickListener {
            if (!MioCommandRouter.execute(this, "open youtube")) {
                openWebsite("https://www.youtube.com")
            }
        }

        findViewById<View>(R.id.action_camera).setOnClickListener {
            openCamera()
        }

        // STEP 5 — Quick Actions
        setupQuickActionsByText()

        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.nav_chats).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<View>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        handleVoiceIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: Intent) {
        val localReply = intent.getStringExtra("MIO_LOCAL_REPLY")
        if (!localReply.isNullOrBlank()) {
            tapToSpeak.text = "🤖  $localReply"
            speak(localReply)
            intent.removeExtra("MIO_LOCAL_REPLY")
            return
        }

        if (!intent.getBooleanExtra("MIO_WAKE", false)) return

        val command = intent.getStringExtra("MIO_COMMAND").orEmpty().trim()

        if (command.isBlank()) {
            tapToSpeak.text = "🎙  Listening..."
            return
        }

        tapToSpeak.text = "🎙  Heard: $command"

        if (MioCommandRouter.execute(this, command)) {
            speak("Done.")
            tapToSpeak.text = "✅  Done"
            return
        }

        askMio(command)
    }

    private fun askMio(command: String) {
        tapToSpeak.text = "🤖  Thinking..."

        OpenRouterClient.ask(command) { answer ->
            runOnUiThread {
                tapToSpeak.text = "🤖  $answer"
                speak(answer)
            }
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
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
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

    private fun setupQuickActionsByText() {
        val root = findViewById<View>(android.R.id.content)
        setQuickActionClick(root, "Summarize Screen") {
            runQuickPrompt("Summarize the current screen. If screen content is unavailable, ask me to paste or upload it.")
        }
        setQuickActionClick(root, "Solve Anything") {
            runQuickPrompt("Solve this problem step by step. If no problem is provided, ask me to enter or upload it.")
        }
        setQuickActionClick(root, "Help me Code") {
            runQuickPrompt("Help me code. Ask what I want to build or fix, then provide a clear solution.")
        }
        setQuickActionClick(root, "Tell me a Story") {
            runQuickPrompt("Tell me a short engaging story.")
        }
    }

    private fun setQuickActionClick(view: View, label: String, action: () -> Unit) {
        if (view is TextView && view.text.toString() == label) {
            (view.parent as? View)?.setOnClickListener { action() }
            return
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                setQuickActionClick(view.getChildAt(i), label, action)
            }
        }
    }

    private fun runQuickPrompt(prompt: String) {
        tapToSpeak.text = "🤖  Thinking..."
        OpenRouterClient.ask(prompt) { answer ->
            runOnUiThread {
                tapToSpeak.text = "🤖  $answer"
                speak(answer)
            }
        }
    }

    private fun openCamera() {
        val intent = Intent("android.media.action.IMAGE_CAPTURE")
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Camera app not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTranslate() {
        openWebsite("https://translate.google.com")
    }

    private fun openWebsite(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
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
