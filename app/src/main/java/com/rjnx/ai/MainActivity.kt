package com.rjnx.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var input: EditText
    private lateinit var chat: TextView
    private lateinit var mic: Button
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var pendingCallNumber: String? = null
    private var pendingCallName: String? = null

    companion object {
        private const val REQUEST_CALL_PHONE = 901
        private const val REQUEST_CONTACTS = 902
        private const val REQUEST_NOTIFICATIONS = 903
    }

    private val apiKey = BuildConfig.OPENROUTER_API_KEY
    private val conversation = mutableListOf<Pair<String, String>>()

    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val number = pendingCallNumber
            val name = pendingCallName ?: "contact"
            pendingCallNumber = null
            pendingCallName = null

            if (granted && number != null) {
                placeCall(number, name)
            } else {
                reply("Phone permission was denied, so I couldn't call $name.")
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // V3.0 Phase 1: start the persistent RJNX background service.
        startRjnxVoiceService()

        setContentView(R.layout.activity_main)
        input = findViewById(R.id.input)
        chat = findViewById(R.id.chat)
        mic = findViewById(R.id.mic)
        tts = TextToSpeech(this, this)
        findViewById<Button>(R.id.send).setOnClickListener {
            val command = input.text.toString().trim()
            if (command.isNotEmpty()) { input.setText(""); handleCommand(command) }
        }
        mic.setOnClickListener { startListening() }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun startRjnxVoiceService() {
        try {
            val serviceIntent = Intent(this, RjnxVoiceService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
            // The foreground Activity remains usable if the service cannot start.
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { reply("Voice recognition is not available on this phone."); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                input.setText(text); handleCommand(text)
            }
            override fun onError(error: Int) { reply("I couldn't understand that.") }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to RJNX")
        }
        recognizer?.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        val text = raw.trim()
        val command = normalizeVoiceCommand(text)
        addUser(text)

        val memoryQuestion =
            command.contains("do you remember me") ||
            command.contains("what do you remember") ||
            command.contains("what do you know about me") ||
            command.contains("kya yaad hai") ||
            command.contains("mujhe yaad hai") ||
            command == "who am i" ||
            command.contains("remember me")

        val memoryAnswer = buildMemoryAnswer()

        val isCallCommand =
            command.matches(Regex(".*\\b(call|phone|contact)\\b.*")) &&
            (command.contains("karo") ||
             command.contains("kar do") ||
             command.contains("lagao") ||
             command.contains("lagao") ||
             command.startsWith("call ") ||
             command.startsWith("phone "))

        val isAlarmCommand =
            command.contains("alarm") ||
            command.contains("wake me") ||
            command.contains("subah") && command.contains("baje") ||
            command.contains("baje") && command.contains("utha")

        when {
            memoryQuestion -> {
                if (memoryAnswer.isBlank()) {
                    reply("I don't have anything saved about you yet.")
                } else {
                    reply(memoryAnswer)
                }
            }

            isCallCommand -> makeCall(text)

            isAlarmCommand -> setAlarmFromCommand(text)

            command.startsWith("remember ") ||
            command.startsWith("save note ") ||
            command.startsWith("make a note ") ||
            command.startsWith("note ") ||
            command.startsWith("yaad rakh") ||
            command.startsWith("yaad rakhna") ||
            command.startsWith("yaad rakh lo") -> saveNote(text)

            command.contains("show my notes") ||
            command.contains("read my notes") ||
            command.contains("what are my notes") ||
            command == "my notes" ||
            command.contains("meri notes") ||
            command.contains("mere notes") -> showNotes()

            command.contains("clear my notes") ||
            command.contains("delete my notes") ||
            command.contains("notes delete") -> clearNotes()

            command.contains("youtube") -> {
                if (!openInstalledAppByName("youtube")) {
                    openUrl("https://www.youtube.com")
                    reply("Opening YouTube.")
                }
            }

            command.contains("instagram") -> {
                if (!openInstalledAppByName("instagram")) {
                    openUrl("https://www.instagram.com")
                    reply("Opening Instagram.")
                }
            }

            command.contains("whatsapp") -> {
                if (!openInstalledAppByName("whatsapp")) {
                    openUrl("https://web.whatsapp.com")
                    reply("Opening WhatsApp.")
                }
            }

            command.contains("chrome") -> {
                if (!openInstalledAppByName("chrome")) {
                    openUrl("https://www.google.com")
                    reply("Opening the browser.")
                }
            }

            command.contains("settings") ||
            command.contains("setting kholo") ||
            command.contains("settings kholo") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                reply("Opening Settings.")
            }

            command == "gallery" ||
            command == "photos" ||
            command.contains("open gallery") ||
            command.contains("open photos") ||
            command.contains("gallery kholo") ||
            command.contains("gallery khol do") ||
            command.contains("photos kholo") -> {
                startActivity(Intent(Intent.ACTION_VIEW).apply { type = "image/*" })
                reply("Opening Gallery.")
            }

            command.contains("google") || command.contains("search") ||
            command.contains("google par") ||
            command.contains("google pe") ||
            command.contains("search karo") -> {
                val query = text
                    .replace(Regex("(?i)google"), "")
                    .replace(Regex("(?i)search"), "")
                    .replace(Regex("(?i)par"), "")
                    .replace(Regex("(?i)pe"), "")
                    .replace(Regex("(?i)karo"), "")
                    .replace(Regex("(?i)kar do"), "")
                    .trim()

                if (query.isBlank()) {
                    reply("What should I search for?")
                } else {
                    openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
                    reply("Searching the web.")
                }
            }

            command.contains("browser") -> {
                openUrl("https://www.google.com")
                reply("Opening the browser.")
            }

            command.contains("hello") ||
            command.contains("hey rjnx") ||
            command.matches(Regex(".*\\bhi\\b.*")) ->
                reply("Hello! I'm RJNX AI. How can I help?")

            else -> askAI(text)
        }
    }

    private fun normalizeVoiceCommand(raw: String): String {
        return raw
            .lowercase(Locale.getDefault())
            .replace("kholo", " kholo ")
            .replace("khol do", " kholo ")
            .replace("open karo", " kholo ")
            .replace("chalao", " kholo ")
            .replace("chala do", " kholo ")
            .replace("chala", " kholo ")
            .replace("lagao", " lagao ")
            .replace("laga do", " lagao ")
            .replace("phone laga do", " phone lagao ")
            .replace("call kar do", " call karo ")
            .replace("call kr do", " call karo ")
            .replace("kr do", " karo ")
            .replace("kar do", " karo ")
            .replace("please", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildMemoryAnswer(): String {
        // Memory is persisted in SharedPreferences by saveNote(), not in
        // the temporary conversation list. Read the same persistent store.
        val saved = getPreferences(MODE_PRIVATE)
            .getStringSet("notes", emptySet())
            ?.toList()
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (saved.isEmpty()) return ""

        return buildString {
            append("Yes. I remember: ")
            saved.forEachIndexed { index, note ->
                if (index > 0) append("; ")
                append(note)
            }
        }
    }

    private fun saveNote(raw: String) {
        val note = raw.replace(
            Regex("(?i)^\\s*(remember|save note|make a note|note)\\s*[:,-]?\\s*"),
            ""
        ).trim()

        if (note.isBlank()) {
            reply("What should I remember?")
            return
        }

        val prefs = getPreferences(MODE_PRIVATE)
        val notes = prefs.getStringSet("notes", emptySet())?.toMutableSet()
            ?: mutableSetOf()

        notes.add(note)
        prefs.edit().putStringSet("notes", notes).apply()

        reply("Saved that to your memory.")
    }

    private fun showNotes() {
        val notes = getPreferences(MODE_PRIVATE)
            .getStringSet("notes", emptySet())
            ?.toList()
            .orEmpty()

        if (notes.isEmpty()) {
            reply("I don't have any saved notes yet.")
            return
        }

        val text = notes.mapIndexed { index, note ->
            "${index + 1}. $note"
        }.joinToString("\n")

        reply("Here is what I remember:\n$text")
    }

    private fun clearNotes() {
        getPreferences(MODE_PRIVATE).edit().remove("notes").apply()
        reply("I've cleared your saved notes.")
    }

    private fun makeCall(raw: String) {
        val command = raw.trim()

        val target = command
            .replace(
                Regex(
                    "(?i)^\\s*(call|phone)\\s+|\\s*(ko\\s+)?(call|phone)(\\s+kar(o|na)|\\s+lagao)?\\s*$|" +
                    "\\s*(ko\\s+)?call\\s+karo\\s*$|" +
                    "\\s*(ko\\s+)?phone\\s+karo\\s*$"
                ),
                ""
            )
            .trim()

        if (target.isBlank()) {
            reply("Tell me who you want to call.")
            return
        }

        val number = findContactNumber(target)
        if (number != null) {
            makeDirectCall(number, target)
        } else {
            val directNumber = target.replace(Regex("[^0-9+]"), "")
            if (directNumber.length >= 7) {
                makeDirectCall(directNumber, target)
            } else {
                reply("I couldn't find $target in your contacts.")
            }
        }
    }

    private fun makeDirectCall(number: String, target: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCallNumber = number
            pendingCallName = target
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            reply("Allow phone permission and I'll call $target.")
            return
        }

        placeCall(number, target)
    }

    private fun placeCall(number: String, target: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCallNumber = number
            pendingCallName = target
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }

        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            reply("Calling $target.")
        } catch (_: Exception) {
            reply("I couldn't place the call.")
        }
    }

    private fun findContactNumber(name: String): String? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 20)
            reply("Please allow contacts permission, then ask me again."); return null
        }
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection, args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private fun setAlarmFromCommand(raw: String) {
        val text = raw.lowercase(Locale.getDefault())

        val timeMatch = Regex("""\b([01]?\d|2[0-3])(?:[:.]([0-5]\d))?\s*(?:baje|am|pm)?\b""")
            .find(text)

        if (timeMatch == null) {
            reply("Time batao, jaise 7 baje ya 7:30 AM.")
            return
        }

        var hour = timeMatch.groupValues[1].toInt()
        val minute = timeMatch.groupValues[2].ifBlank { "0" }.toInt()

        val fullBefore = text.substring(0, timeMatch.range.first)
        val fullAfter = text.substring(timeMatch.range.last + 1)
        val isPm = Regex("""\bpm\b""").containsMatchIn(fullBefore + " " + fullAfter)
        val isAm = Regex("""\bam\b""").containsMatchIn(fullBefore + " " + fullAfter)
        val morning = text.contains("subah")
        val evening = text.contains("shaam") || text.contains("evening")
        val night = text.contains("raat") || text.contains("night")

        if (isPm || evening || night) {
            if (hour in 1..11) hour += 12
        } else if (isAm || morning) {
            if (hour == 12) hour = 0
        }

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        val alarmIntent = Intent(this, RjnxAlarmReceiver::class.java).apply {
            putExtra("message", "RJNX AI Alarm")
        }

        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            hour * 60 + minute,
            alarmIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                reply("Exact alarm permission chahiye. Main settings khol raha hoon.")
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(
                    this,
                    "android.permission.POST_NOTIFICATIONS"
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            }

            reply("Alarm set for ${String.format("%02d:%02d", hour, minute)}.")
        } catch (_: SecurityException) {
            reply("Android blocked exact-alarm access. Please allow it and try again.")
        } catch (_: Exception) {
            reply("I couldn't set the alarm.")
        }
    }

    private fun askAI(question: String) {
        if (apiKey.isBlank()) { reply("AI API key is not configured yet."); return }
        addAssistant("Thinking…")
        Thread {
            try {
                val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; setRequestProperty("Authorization", "Bearer $apiKey"); setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("HTTP-Referer", "https://github.com/devkazzu/Rjnx-AI")
                    setRequestProperty("X-Title", "RJNX AI")
                    connectTimeout = 15000; readTimeout = 30000; doOutput = true
                }
                val messages = JSONArray()
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are RJNX AI, a concise and helpful Android voice assistant. Answer naturally and clearly.")
                })
                synchronized(conversation) {
                    conversation.takeLast(10).forEach { (role, text) ->
                        messages.put(JSONObject().apply {
                            put("role", if (role == "assistant") "assistant" else "user")
                            put("content", text)
                        })
                    }
                }
                val savedNotes = getPreferences(MODE_PRIVATE)
                    .getStringSet("notes", emptySet())
                    ?.toList()
                    .orEmpty()

                if (savedNotes.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "User's saved notes/memory:\n- " +
                                    savedNotes.joinToString("\n- ")
                        )
                    })
                }

                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", question)
                })
                val body = JSONObject().apply {
                    put("model", "openrouter/free")
                    put("messages", messages)
                }
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                if (code !in 200..299) {
                    Log.e("RJNX_AI", "HTTP $code: $response")
                    val detail = try {
                        val error = JSONObject(response).optJSONObject("error")
                        error?.optString("message")?.takeIf { it.isNotBlank() } ?: response.take(300)
                    } catch (_: Exception) {
                        response.take(300)
                    }
                    runOnUiThread { reply("OpenRouter Error $code: $detail") }
                    return@Thread
                }
                val choices = JSONObject(response).optJSONArray("choices")
                var answer = ""
                if (choices != null && choices.length() > 0) {
                    answer = choices.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "") ?: ""
                }
                if (answer.isBlank()) answer = "I couldn't generate a response."
                synchronized(conversation) { conversation.add("user" to question); conversation.add("assistant" to answer); while (conversation.size > 20) conversation.removeAt(0) }
                runOnUiThread { reply(answer) }
            } catch (e: Exception) {
                Log.e("RJNX_AI", "AI request error", e)
                runOnUiThread {
                    reply("Network/Request error: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
                }
            }
        }.start()
    }

    private fun openInstalledAppByName(name: String): Boolean {
        val wanted = name.lowercase(Locale.getDefault()).trim()
        if (wanted.isBlank()) return false

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val appInfo = apps.firstOrNull { info ->
            val label = pm.getApplicationLabel(info).toString()
                .lowercase(Locale.getDefault())
            label == wanted || label.contains(wanted)
        } ?: return false

        val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
            ?: return false

        try {
            startActivity(launchIntent)
            reply("Opening ${pm.getApplicationLabel(appInfo)}.")
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun openUrl(url: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    private fun addUser(text: String) { chat.append("\n\nYou: $text") }
    private fun addAssistant(text: String) { chat.append("\n\nRJNX: $text") }
    private fun reply(text: String) { addAssistant(text); if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rjnx") }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) { tts.language = Locale.getDefault(); tts.setSpeechRate(0.95f) }
    }

    override fun onDestroy() { recognizer?.destroy(); tts.stop(); tts.shutdown(); super.onDestroy() }
}
