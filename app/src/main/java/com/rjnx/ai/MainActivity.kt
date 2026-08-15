package com.rjnx.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.widget.Button
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.ClipData
import android.content.ClipboardManager
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
                tts?.language = getSelectedLocale()
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
            openVisionCamera()
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

    private fun getSelectedLocale(): Locale {
        val tag = getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
            .getString("tts_language", "en-IN") ?: "en-IN"
        return Locale.forLanguageTag(tag)
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

    private fun openVisionCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(intent, REQUEST_VISION_CAMERA)
        } catch (_: Exception) {
            Toast.makeText(this, "Camera app not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_VISION_CAMERA || resultCode != RESULT_OK) return

        val bitmap = data?.extras?.get("data") as? Bitmap
        if (bitmap == null) {
            Toast.makeText(this, "Could not get the captured image", Toast.LENGTH_SHORT).show()
            return
        }

        tapToSpeak.text = "👁  Analyzing image..."

        OpenRouterClient.askVision(
            bitmap,
            "Analyze this image and explain what you see. Be concise but useful."
        ) { answer ->
            runOnUiThread {
                tapToSpeak.text = "👁  $answer"
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
        val input = EditText(this).apply {
            hint = "Enter text to translate"
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setPadding(32, 24, 32, 24)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("🌐 Translate")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Copy", null)
            .setPositiveButton("Translate", null)

        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isBlank()) {
                    input.error = "Enter text first"
                    return@setOnClickListener
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                tapToSpeak.text = "🌐  Translating..."

                val prompt =
                    "Translate the following text to Hindi. Return only the translated text.\n\n$text"

                OpenRouterClient.ask(prompt) { answer ->
                    runOnUiThread {
                        dialog.dismiss()
                        showTranslationResult(answer)
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Mio Translation", text))
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showTranslationResult(result: String) {
        tapToSpeak.text = "🌐  $result"

        AlertDialog.Builder(this)
            .setTitle("🌐 Translation")
            .setMessage(result)
            .setNegativeButton("Close", null)
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Mio Translation", result))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Speak") { _, _ ->
                speak(result)
            }
            .show()
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
        private const val REQUEST_VISION_CAMERA = 602
    }
}
