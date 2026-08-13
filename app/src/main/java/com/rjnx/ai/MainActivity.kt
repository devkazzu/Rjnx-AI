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
    private val apiKey = BuildConfig.OPENROUTER_API_KEY
    private val conversation = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val command = text.lowercase(Locale.getDefault())
        addUser(text)

        val memoryQuestion =
            command.contains("do you remember me") ||
            command.contains("what do you remember") ||
            command.contains("what do you know about me") ||
            command == "who am i" ||
            command.contains("remember me")

        when {
            memoryQuestion -> askAI(text)

            command.startsWith("remember ") ||
            command.startsWith("save note ") ||
            command.startsWith("make a note ") ||
            command.startsWith("note ") -> saveNote(text)

            command.contains("show my notes") ||
            command.contains("read my notes") ||
            command.contains("what are my notes") ||
            command == "my notes" -> showNotes()

            command.contains("clear my notes") ||
            command.contains("delete my notes") -> clearNotes()

            command.contains("youtube") -> {
                openUrl("https://www.youtube.com")
                reply("Opening YouTube.")
            }

            command.contains("google") || command.contains("search") -> {
                val query = text.replace(Regex("(?i)search|google"), "").trim()
                if (query.isBlank()) {
                    reply("What should I search for?")
                } else {
                    openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
                    reply("Searching the web.")
                }
            }

            command.contains("settings") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                reply("Opening Settings.")
            }

            command == "gallery" ||
            command == "photos" ||
            command.contains("open gallery") ||
            command.contains("open photos") -> {
                startActivity(Intent(Intent.ACTION_VIEW).apply { type = "image/*" })
                reply("Opening Gallery.")
            }

            command.startsWith("call ") || command == "call" -> makeCall(text)

            command.contains("alarm") || command.contains("wake me") ->
                setAlarmFromCommand(text)

            command.contains("browser") || command.contains("chrome") -> {
                openUrl("https://www.google.com")
                reply("Opening the browser.")
            }

            command.contains("hello") || command.matches(Regex(".*\\bhi\\b.*")) ->
                reply("Hello! I'm RJNX AI. How can I help?")

            else -> askAI(text)
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
        val target = raw.replace(Regex("(?i)^.*?call\\s+"), "").trim()
        if (target.isBlank()) { reply("Tell me who you want to call."); return }
        val number = findContactNumber(target)
        if (number != null) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))); reply("Opening the dialer for $target.")
        } else {
            val directNumber = target.replace(Regex("[^0-9+]"), "")
            if (directNumber.length >= 7) { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$directNumber"))); reply("Opening the dialer.") }
            else reply("I couldn't find $target in your contacts.")
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
        val match = Regex("(?i)\\b([01]?\\d|2[0-3])[:.]([0-5]\\d)\\b").find(raw)
        if (match == null) { reply("Say the alarm time, for example: set alarm 7:30."); return }
        val hour = match.groupValues[1].toInt(); val minute = match.groupValues[2].toInt()
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour); putExtra(AlarmClock.EXTRA_MINUTES, minute); putExtra(AlarmClock.EXTRA_MESSAGE, "RJNX AI Alarm")
        }
        try { startActivity(intent); reply("Opening alarm setup for ${String.format("%02d:%02d", hour, minute)}.") }
        catch (e: Exception) { Toast.makeText(this, "No alarm app available", Toast.LENGTH_SHORT).show(); reply("I couldn't open the alarm app.") }
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

    private fun openUrl(url: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    private fun addUser(text: String) { chat.append("\n\nYou: $text") }
    private fun addAssistant(text: String) { chat.append("\n\nRJNX: $text") }
    private fun reply(text: String) { addAssistant(text); if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rjnx") }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) { tts.language = Locale.getDefault(); tts.setSpeechRate(0.95f) }
    }

    override fun onDestroy() { recognizer?.destroy(); tts.stop(); tts.shutdown(); super.onDestroy() }
}
