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
        val command = raw.lowercase(Locale.getDefault())
        addUser(raw)
        when {
            command.contains("youtube") -> { openUrl("https://www.youtube.com"); reply("Opening YouTube.") }
            command.contains("google") || command.contains("search") -> {
                val query = raw.replace(Regex("(?i)search|google"), "").trim()
                openUrl("https://www.google.com/search?q=${Uri.encode(query)}"); reply("Searching the web.")
            }
            command.contains("settings") -> { startActivity(Intent(Settings.ACTION_SETTINGS)); reply("Opening Settings.") }
            command.contains("gallery") || command.contains("photos") -> {
                startActivity(Intent(Intent.ACTION_VIEW).apply { type = "image/*" }); reply("Opening Gallery.")
            }
            command.startsWith("call ") || command == "call" -> makeCall(raw)
            command.contains("alarm") || command.contains("wake me") -> setAlarmFromCommand(raw)
            command.contains("browser") || command.contains("chrome") -> { openUrl("https://www.google.com"); reply("Opening the browser.") }
            command.contains("note") || command.contains("remember") -> {
                val note = raw.replace(Regex("(?i)^.*?(note|remember)"), "").trim()
                getPreferences(MODE_PRIVATE).edit().putString("last_note", note).apply(); reply("Saved your note.")
            }
            command.contains("hello") || command.matches(Regex(".*\\bhi\\b.*")) -> reply("Hello! I'm RJNX AI. How can I help?")
            else -> askAI(raw)
        }
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
                val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; setRequestProperty("Authorization", "Bearer $apiKey"); setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000; readTimeout = 30000; doOutput = true
                }
                val messages = JSONArray()
                messages.put(JSONObject().apply { put("role", "developer"); put("content", "You are RJNX AI, a concise and helpful Android voice assistant. Answer naturally and clearly.") })
                synchronized(conversation) { conversation.takeLast(10).forEach { (role, text) -> messages.put(JSONObject().apply { put("role", role); put("content", text) }) } }
                messages.put(JSONObject().apply { put("role", "user"); put("content", question) })
                val body = JSONObject().apply { put("model", "gpt-5-mini"); put("input", messages) }
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                if (code !in 200..299) {
                    Log.e("RJNX_AI", "HTTP $code: $response")

                    val detail = try {
                        val errorJson = JSONObject(response)
                        val error = errorJson.optJSONObject("error")
                        error?.optString("message")
                            ?.takeIf { it.isNotBlank() }
                            ?: response.take(300)
                    } catch (_: Exception) {
                        response.take(300)
                    }

                    runOnUiThread {
                        reply("API Error $code: $detail")
                    }
                    return@Thread
                }
                val output = JSONObject(response).optJSONArray("output")
                var answer = ""
                if (output != null) for (i in 0 until output.length()) {
                    val item = output.optJSONObject(i) ?: continue; val parts = item.optJSONArray("content") ?: continue
                    for (j in 0 until parts.length()) { val part = parts.optJSONObject(j) ?: continue; if (part.optString("type") == "output_text") answer += part.optString("text") }
                }
                if (answer.isBlank()) answer = "I couldn't generate a response."
                synchronized(conversation) { conversation.add("user" to question); conversation.add("assistant" to answer); while (conversation.size > 20) conversation.removeAt(0) }
                runOnUiThread { reply(answer) }
            } catch (e: Exception) { Log.e("RJNX_AI", "AI request error", e); runOnUiThread { reply("Couldn't reach the AI service. Check your internet connection.") } }
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
