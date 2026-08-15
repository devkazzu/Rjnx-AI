package com.rjnx.ai

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.ByteArrayOutputStream
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
                requestText(userMessage)
            } catch (e: Exception) {
                "Sorry, I couldn't connect right now.\n${e.message ?: "Network error"}"
            }
            main.post { callback(result) }
        }
    }

    fun askVision(bitmap: Bitmap, prompt: String, callback: (String) -> Unit) {
        executor.execute {
            val result = try {
                requestVision(bitmap, prompt)
            } catch (e: Exception) {
                "Sorry, I couldn't analyze the image.\n${e.message ?: "Network error"}"
            }
            main.post { callback(result) }
        }
    }

    private fun requestText(userMessage: String): String {
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
            .put(JSONObject().put("role", "user").put("content", userMessage))

        return request(messages)
    }

    private fun requestVision(bitmap: Bitmap, prompt: String): String {
        val compressed = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, compressed)
        val base64 = Base64.encodeToString(compressed.toByteArray(), Base64.NO_WRAP)

        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$base64")
                    )
            )

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put(
                "content",
                "You are Mio Vision. Analyze the provided image carefully. " +
                    "Describe what is visible and answer the user's question. " +
                    "Do not claim certainty about details that cannot be seen."
            ))
            .put(JSONObject().put("role", "user").put("content", content))

        return request(messages)
    }

    private fun request(messages: JSONArray): String {
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
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream

            val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

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

            val choices = JSONObject(response).optJSONArray("choices")
                ?: return "Mio received an empty response."

            if (choices.length() == 0) return "Mio received an empty response."

            return choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Mio received an empty response."
        } finally {
            connection.disconnect()
        }
    }
}
