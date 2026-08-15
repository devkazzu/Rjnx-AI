package com.rjnx.ai

import android.Manifest
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import android.graphics.Canvas
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Button
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.ClipData
import android.content.ClipboardManager
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tapToSpeak: TextView
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingVisionPrompt: String? = null


    private var pendingVisionUri: Uri? = null
    private var pendingPermissionCommand: String? = null

    private val tapVoiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(RjnxVoiceService.EXTRA_VOICE_COMMAND).orEmpty().trim()
            if (command.isNotBlank()) {
                handleVoiceCommand(command)
            }
        }
    }

    private val visionCameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingVisionUri
            pendingVisionUri = null

            if (!success || uri == null) {
                tapToSpeak.text = "👁  Vision cancelled"
                return@registerForActivityResult
            }

            val bitmap = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) {
                null
            }

            if (bitmap == null) {
                Toast.makeText(this, "Could not read the captured image", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            tapToSpeak.text = "👁  Analyzing image..."
            val prompt = pendingVisionPrompt
                ?: "Analyze this image and explain what you see. Be concise but useful."
            pendingVisionPrompt = null

            OpenRouterClient.askVision(bitmap, prompt) { answer ->
                bitmap.recycle()
                runOnUiThread {
                    tapToSpeak.text = "👁  $answer"
                    speak(answer)
                    try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_glass_orbit)
        playMioStartupAnimation()

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

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            tapVoiceReceiver,
            IntentFilter(RjnxVoiceService.ACTION_TAP_VOICE_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(tapVoiceReceiver)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: Intent) {
        val localReply = intent.getStringExtra("MIO_LOCAL_REPLY")
        if (!localReply.isNullOrBlank()) {
            showLocalReply(localReply)
            intent.removeExtra("MIO_LOCAL_REPLY")
            return
        }

        if (!intent.getBooleanExtra("MIO_WAKE", false)) return

        val command = intent.getStringExtra("MIO_COMMAND").orEmpty().trim()
        if (command.isBlank()) {
            tapToSpeak.text = "🎙  Listening..."
            return
        }

        handleVoiceCommand(command)
    }

    fun showLocalReply(text: String) {
        tapToSpeak.text = "🤖  $text"
        speak(text)
    }

    private fun handleVoiceCommand(command: String) {
        tapToSpeak.text = "🎙  Heard: $command"
        pendingPermissionCommand = command

        if (MioCommandRouter.execute(this, command)) {
            pendingPermissionCommand = null
            tapToSpeak.text = "✅  Done"
            return
        }

        pendingPermissionCommand = null
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
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
            return
        }

        val imageFile = try {
            File.createTempFile("mio_vision_", ".jpg", cacheDir)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not prepare camera", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = try {
            FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", imageFile)
        } catch (_: Exception) {
            Toast.makeText(this, "Camera storage setup failed", Toast.LENGTH_SHORT).show()
            return
        }

        pendingVisionUri = uri
        visionCameraLauncher.launch(uri)
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

        if ((requestCode == REQUEST_CONTACTS || requestCode == REQUEST_CALL) &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            pendingPermissionCommand?.let {
                val command = it
                pendingPermissionCommand = null
                handleVoiceCommand(command)
            }
        }

        if (requestCode == REQUEST_AUDIO &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startTapToSpeak()
        }
    }


    /**
     * Premium Mio startup animation.
     * Kept fully programmatic so no extra XML/drawable files are required.
     */
    private fun playMioStartupAnimation() {
        val root = findViewById<View>(android.R.id.content) as? android.view.ViewGroup ?: return

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(5, 7, 16))
            alpha = 1f
            isClickable = true
            isFocusable = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_mio)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Mio"
        }

        val logoSize = (132 * resources.displayMetrics.density).toInt()
        content.addView(
            logo,
            LinearLayout.LayoutParams(logoSize, logoSize)
        )

        val title = TextView(this).apply {
            text = "MIO"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 30f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            setPadding(0, 18, 0, 0)
        }
        content.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = "Your AI. Your assistant."
            setTextColor(android.graphics.Color.rgb(166, 174, 205))
            textSize = 13f
            gravity = Gravity.CENTER
            alpha = 0f
            setPadding(0, 8, 0, 0)
        }
        content.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        overlay.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        content.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(650L)
            .setInterpolator(OvershootInterpolator(1.15f))
            .withEndAction {
                subtitle.animate()
                    .alpha(1f)
                    .setDuration(280L)
                    .start()

                content.animate()
                    .scaleX(1.035f)
                    .scaleY(1.035f)
                    .setDuration(180L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        content.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180L)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .withEndAction {
                                overlay.animate()
                                    .alpha(0f)
                                    .setStartDelay(320L)
                                    .setDuration(420L)
                                    .setInterpolator(AccelerateDecelerateInterpolator())
                                    .withEndAction {
                                        root.removeView(overlay)
                                    }
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 401
        private const val REQUEST_CONTACTS = 803
        private const val REQUEST_CALL = 804
        private const val REQUEST_VISION_CAMERA = 602
    }
}
