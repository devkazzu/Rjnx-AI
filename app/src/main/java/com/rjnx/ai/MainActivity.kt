package com.rjnx.ai

import android.Manifest
import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
    private var pendingVisionPrompt: String? = null

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

        findViewById<View>(R.id.nav_tools).setOnClickListener {
            showTools()
        }

        findViewById<View>(R.id.btn_help_center).setOnClickListener {
            showHelp()
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

        val settings = getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
        if (!settings.getBoolean("voice", true)) {
            Toast.makeText(this, "Voice Assistant is OFF in Settings", Toast.LENGTH_SHORT).show()
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
            summarizeCurrentScreen()
        }

        setQuickActionClick(root, "Solve Anything") {
            openVisionCameraWithPrompt(
                "Solve the problem shown in this image. Explain the solution step by step."
            )
        }

        setQuickActionClick(root, "Help me Code") {
            showTextPrompt(
                "Help me Code",
                "Describe what you want to build or fix."
            ) { text ->
                runQuickPrompt(
                    "Act as a coding assistant. Help the user with this request. " +
                        "Give practical code and explain the important parts.\n\n$text"
                )
            }
        }

        setQuickActionClick(root, "Tell me a Story") {
            runQuickPrompt("Tell me a short engaging story.")
        }
    }

    private fun getSelectedLocale(): Locale {
        val tag = getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
            .getString("tts_language", "en-IN") ?: "en-IN"
        return Locale.forLanguageTag(tag)
    }

    private fun showTools() {
        val tools = arrayOf(
            "📷 Vision / Solve Anything",
            "🌐 Translate",
            "💬 AI Chat",
            "🎙 Voice Assistant",
            "🔎 Web Search"
        )

        AlertDialog.Builder(this)
            .setTitle("Mio Tools")
            .setItems(tools) { _, which ->
                when (which) {
                    0 -> openVisionCamera()
                    1 -> openTranslate()
                    2 -> startActivity(Intent(this, ChatActivity::class.java))
                    3 -> startTapToSpeak()
                    4 -> showTextPrompt("Web Search", "What do you want to search?") { q ->
                        if (!MioCommandRouter.execute(this, "search $q")) {
                            openWebsite("https://www.google.com/search?q=${Uri.encode(q)}")
                        }
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Mio Help")
            .setMessage(
                "Try commands like:\\n\\n" +
                    "• YouTube kholo\\n" +
                    "• Chrome kholo\\n" +
                    "• Mummy ko call karo\\n" +
                    "• 7:30 PM ka alarm lagao\\n" +
                    "• Hey Mio\\n\\n" +
                    "You can also use Vision, Translate, Chat and Quick Actions."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showTextPrompt(title: String, hint: String, onSubmit: (String) -> Unit) {
        val edit = EditText(this).apply {
            this.hint = hint
            minLines = 3
            setPadding(30, 20, 30, 20)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(edit)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val value = edit.text.toString().trim()
                if (value.isNotEmpty()) onSubmit(value)
            }
            .show()
    }

    private fun summarizeCurrentScreen() {
        val root = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(
            root.width.coerceAtLeast(1),
            root.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).let { root.draw(it) }

        tapToSpeak.text = "👁  Summarizing screen..."
        OpenRouterClient.askVision(
            bitmap,
            "Summarize the visible app screen. Identify the main information and UI sections. " +
                "Keep the summary concise and useful."
        ) { answer ->
            runOnUiThread {
                tapToSpeak.text = "👁  $answer"
                speak(answer)
            }
        }
    }

    private fun openVisionCameraWithPrompt(prompt: String) {
        pendingVisionPrompt = prompt
        openVisionCamera()
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

        val prompt = pendingVisionPrompt
            ?: "Analyze this image and explain what you see. Be concise but useful."
        pendingVisionPrompt = null

        OpenRouterClient.askVision(bitmap, prompt) { answer ->
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

        val languages = arrayOf("English", "Hindi", "Assamese", "Bengali")
        var target = "Hindi"

        AlertDialog.Builder(this)
            .setTitle("🌐 Translate")
            .setView(input)
            .setSingleChoiceItems(languages, 1) { _, which ->
                target = languages[which]
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Translate", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val text = input.text.toString().trim()
                        if (text.isBlank()) {
                            input.error = "Enter text first"
                            return@setOnClickListener
                        }

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        tapToSpeak.text = "🌐  Translating..."

                        val prompt =
                            "Translate the following text to $target. Return only the translated text.\\n\\n$text"

                        OpenRouterClient.ask(prompt) { answer ->
                            runOnUiThread {
                                dialog.dismiss()
                                showTranslationResult(answer)
                            }
                        }
                    }
                }
                dialog.show()
            }
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
