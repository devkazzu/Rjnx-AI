package com.rjnx.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(
    val role: String,
    val text: String
)

data class MioChat(
    val id: String,
    var title: String,
    val messages: MutableList<ChatMessage>
)

object ChatStore {

    private const val PREFS = "mio_chat_persistence"
    private const val KEY_CHATS = "chats"
    private const val KEY_ACTIVE = "active_chat"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadChats(context: Context): MutableList<MioChat> {
        val raw = prefs(context).getString(KEY_CHATS, null)
        if (raw.isNullOrBlank()) {
            return mutableListOf(createChat("New Chat"))
        }

        return try {
            val array = JSONArray(raw)
            val chats = mutableListOf<MioChat>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val messages = mutableListOf<ChatMessage>()
                val messageArray = obj.optJSONArray("messages") ?: JSONArray()

                for (j in 0 until messageArray.length()) {
                    val m = messageArray.getJSONObject(j)
                    messages.add(
                        ChatMessage(
                            m.optString("role"),
                            m.optString("text")
                        )
                    )
                }

                chats.add(
                    MioChat(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        title = obj.optString("title").ifBlank { "New Chat" },
                        messages = messages
                    )
                )
            }

            if (chats.isEmpty()) mutableListOf(createChat("New Chat")) else chats
        } catch (_: Exception) {
            mutableListOf(createChat("New Chat"))
        }
    }

    fun saveChats(context: Context, chats: List<MioChat>) {
        val array = JSONArray()

        chats.forEach { chat ->
            val obj = JSONObject()
                .put("id", chat.id)
                .put("title", chat.title)

            val messages = JSONArray()
            chat.messages.forEach { message ->
                messages.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("text", message.text)
                )
            }

            obj.put("messages", messages)
            array.put(obj)
        }

        prefs(context).edit()
            .putString(KEY_CHATS, array.toString())
            .apply()
    }

    fun getActiveId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE, null)

    fun setActiveId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ACTIVE, id).apply()
    }

    fun createChat(title: String = "New Chat"): MioChat =
        MioChat(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "New Chat" },
            messages = mutableListOf()
        )
}
