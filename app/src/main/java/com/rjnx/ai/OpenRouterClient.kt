package com.rjnx.ai

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object OpenRouterClient {

    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private const val MODEL = "openai/gpt-4o-mini"

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun ask(userMessage: String, callback: (String) -> Unit) {
        executor.execute {
            val result = try {
                request(userMessage)
            } catch (e: Exception) {
                "Sorry, I couldn't connect right now.\n${e.message ?: "Network error"}"
            }

            main.post { callback(result) }
        }
    }

    private fun request(userMessage: String): String {
        val apiKey = BuildConfig.OPENROUTER_API_KEY.trim()
        if (apiKey.isEmpty()) {
            return "OpenRouter API key is not configured. Add OPENROUTER_API_KEY to your Gradle properties."
        }

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://github.com/devkazzu/Rjnx-AI")
            setRequestProperty("X-Title", "RJNX AI")
        }

        try {
            val messages = JSONArray()
                .put(
                    JSONObject()
                        .put("role", "system")
                        .put(
                            "content",
                            "You are Mio, a helpful AI assistant inside the RJNX AI Android app. " +
                                "Answer clearly and naturally. Keep responses useful and concise unless the user asks for detail."
                        )
                )
                .put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", userMessage)
                )

            val body = JSONObject()
                .put("model", MODEL)
                .put("messages", messages)
                .put("temperature", 0.7)
                .put("max_tokens", 1200)

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(body.toString())
                it.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }

            if (code !in 200..299) {
                return try {
                    val error = JSONObject(response)
                        .optJSONObject("error")
                        ?.optString("message")
                    "OpenRouter error ($code): ${error ?: response}"
                } catch (_: Exception) {
                    "OpenRouter error ($code): $response"
                }
            }

            val json = JSONObject(response)
            val choices = json.optJSONArray("choices")
                ?: return "Mio received an empty response."

            if (choices.length() == 0) return "Mio received an empty response."

            val content = choices
                .getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content")
                ?.trim()

            return if (!content.isNullOrEmpty()) {
                content
            } else {
                "Mio received an empty response."
            }
        } finally {
            connection.disconnect()
        }
    }
}
