package com.rjnx.ai

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var input: EditText
    private lateinit var chat: TextView
    private lateinit var mic: Button
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private val apiKey = BuildConfig.OPENAI_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        chat = findViewById(R.id.chat)
        mic = findViewById(R.id.mic)
        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.send).setOnClickListener {
            val command = input.text.toString().trim()
            if (command.isNotEmpty()) {
                input.setText("")
                handleCommand(command)
            }
        }

        mic.setOnClickListener { startListening() }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            reply("Voice recognition is not available on this phone.")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                input.setText(text)
                handleCommand(text)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to RJNX")
        }
        recognizer?.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale.getDefault())
        addUser(raw)

        when {
            command.contains("youtube") -> {
                openUrl("https://www.youtube.com")
                reply("Opening YouTube.")
            }
            command.contains("google") || command.contains("search") -> {
                val q = raw.replace(Regex("(?i)search|google"), "").trim()
                openUrl("https://www.google.com/search?q=${Uri.encode(q)}")
                reply("Searching the web.")
            }
            command.contains("settings") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                reply("Opening Settings.")
            }
            command.contains("gallery") || command.contains("photos") -> {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                reply("Opening Gallery.")
            }
            command.contains("note") -> {
                val note = raw.substringAfter("note", raw, "").trim()
                getPreferences(MODE_PRIVATE).edit()
                    .putString("last_note", note).apply()
                reply("Saved your note.")
            }
            command.contains("hello") || command.contains("hi") -> {
                reply("Hello! I'm RJNX AI. How can I help?")
            }
            else -> {
                askAI(raw)
            }
        }
    }


    private fun askAI(question: String) {
        if (apiKey.isBlank()) {
            reply("AI API key is not configured yet.")
            return
        }

        reply("Thinking…")

        Thread {
            try {
                val url = URL("https://api.openai.com/v1/responses")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("model", "gpt-5-mini")
                    put("input", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", question)
                        }
                    ))
                }

                connection.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

                if (code !in 200..299) {
                    Log.e("RJNX_AI", "HTTP $code: $response")
                    runOnUiThread { reply("AI request failed ($code). Check your API key or connection.") }
                    return@Thread
                }

                val json = JSONObject(response)
                val output = json.optJSONArray("output")
                var answer = ""

                if (output != null) {
                    for (i in 0 until output.length()) {
                        val item = output.optJSONObject(i) ?: continue
                        val content = item.optJSONArray("content") ?: continue
                        for (j in 0 until content.length()) {
                            val part = content.optJSONObject(j) ?: continue
                            if (part.optString("type") == "output_text") {
                                answer += part.optString("text")
                            }
                        }
                    }
                }

                if (answer.isBlank()) answer = "I couldn't generate a response."

                runOnUiThread {
                    // Remove the temporary "Thinking…" message by simply adding the final answer.
                    reply(answer)
                }
            } catch (e: Exception) {
                Log.e("RJNX_AI", "AI request error", e)
                runOnUiThread { reply("Couldn't reach the AI service. Check your internet connection.") }
            }
        }.start()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun addUser(text: String) {
        chat.append("\n\nYou: $text")
    }

    private fun reply(text: String) {
        chat.append("\n\nRJNX: $text")
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rjnx")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            tts.setSpeechRate(0.95f)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
