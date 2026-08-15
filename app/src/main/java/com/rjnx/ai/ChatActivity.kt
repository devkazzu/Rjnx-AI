package com.rjnx.ai

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ChatActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var send: TextView

    private var chats = mutableListOf<MioChat>()
    private lateinit var activeChat: MioChat

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("MIO_VOICE_TEXT").orEmpty().trim()
            if (text.isNotEmpty()) {
                input.setText(text)
                input.setSelection(input.text.length)
                sendMessage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_chat_screen)

        chatContainer = findViewById(R.id.chat_container)
        input = findViewById(R.id.chat_input)
        scroll = findViewById(R.id.chat_scroll)
        send = findViewById(R.id.chat_send)

        chats = ChatStore.loadChats(this)
        activeChat = findActiveChat()
        ChatStore.setActiveId(this, activeChat.id)
        ChatStore.saveChats(this, chats)

        findViewById<View>(R.id.chat_back).setOnClickListener { finish() }

        // New Chat button now opens the saved chat manager.
        findViewById<View>(R.id.chat_new).setOnClickListener {
            showChatManager()
        }

        send.setOnClickListener { sendMessage() }

        findViewById<View>(R.id.chat_mic).setOnClickListener {
            startChatVoiceRecognition()
        }

        input.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        renderActiveChat()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            voiceReceiver,
            IntentFilter(RjnxVoiceService.ACTION_CHAT_VOICE_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(voiceReceiver)
        super.onStop()
    }

    private fun findActiveChat(): MioChat {
        val id = ChatStore.getActiveId(this)
        return chats.firstOrNull { it.id == id } ?: chats.first()
    }

    private fun renderActiveChat() {
        chatContainer.removeAllViews()

        if (activeChat.messages.isEmpty()) {
            addBotMessage("Hello Raju! 👋\nHow can I help you today?")
            return
        }

        activeChat.messages.forEach { message ->
            if (message.role == "user") {
                addUserMessage(message.text, save = false)
            } else {
                addBotMessage(message.text, save = false)
            }
        }

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startChatVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 402)
            return
        }

        input.hint = "Listening…"
        val serviceIntent = Intent(this, RjnxVoiceService::class.java).apply {
            putExtra(RjnxVoiceService.EXTRA_CHAT_LISTEN, true)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty() || !send.isEnabled) return

        addUserMessage(message)
        input.text.clear()
        input.hint = "Type a message..."
        setSending(true)

        OpenRouterClient.ask(message) { answer ->
            addBotMessage(answer)
            setSending(false)
            saveChats()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun setSending(sending: Boolean) {
        send.isEnabled = !sending
        send.alpha = if (sending) 0.45f else 1f
    }

    private fun addUserMessage(message: String, save: Boolean = true) {
        val bubble = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 13, 20, 13)
            background = getDrawable(R.drawable.mio_chat_user_bubble)
        }

        chatContainer.addView(
            bubble,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                setMargins(50, 8, 14, 8)
            }
        )

        if (save) {
            activeChat.messages.add(ChatMessage("user", message))
            if (activeChat.title == "New Chat") {
                activeChat.title = message.take(35).replace("\n", " ")
            }
            saveChats()
        }

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBotMessage(message: String, save: Boolean = true) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        val avatar = ImageButton(this).apply {
            setImageResource(R.drawable.ic_mio)
            background = getDrawable(R.drawable.mio_chat_avatar_bg)
            setPadding(10, 10, 10, 10)
            isClickable = false
        }

        val bubble = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 14, 20, 14)
            background = getDrawable(R.drawable.mio_chat_bot_bubble)
        }

        row.addView(avatar, LinearLayout.LayoutParams(52, 52).apply {
            setMargins(8, 0, 8, 4)
        })

        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 50, 0)
            }
        )

        chatContainer.addView(row)

        if (save) {
            activeChat.messages.add(ChatMessage("assistant", message))
            saveChats()
        }
    }

    private fun saveChats() {
        ChatStore.saveChats(this, chats)
        ChatStore.setActiveId(this, activeChat.id)
    }

    private fun showChatManager() {
        val labels = chats.map { chat ->
            val count = chat.messages.size
            "${chat.title}  •  $count messages"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Your Chats")
            .setItems(labels) { _, which ->
                activeChat = chats[which]
                ChatStore.setActiveId(this, activeChat.id)
                renderActiveChat()
            }
            .setPositiveButton("＋ New Chat") { _, _ ->
                createNewChat()
            }
            .setNeutralButton("Manage") { _, _ ->
                showManageChats()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun createNewChat() {
        val chat = ChatStore.createChat()
        chats.add(0, chat)
        activeChat = chat
        ChatStore.setActiveId(this, chat.id)
        ChatStore.saveChats(this, chats)
        renderActiveChat()
    }

    private fun showManageChats() {
        val labels = chats.map { it.title }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Manage Chats")
            .setItems(labels) { _, which ->
                showChatOptions(chats[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showChatOptions(chat: MioChat) {
        val options = arrayOf("Open", "Rename", "Delete")

        AlertDialog.Builder(this)
            .setTitle(chat.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        activeChat = chat
                        ChatStore.setActiveId(this, chat.id)
                        renderActiveChat()
                    }
                    1 -> renameChat(chat)
                    2 -> deleteChat(chat)
                }
            }
            .show()
    }

    private fun renameChat(chat: MioChat) {
        val edit = EditText(this).apply {
            setText(chat.title)
            selectAll()
            hint = "Chat name"
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Chat")
            .setView(edit)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotEmpty()) {
                    chat.title = name
                    saveChats()
                    if (chat.id == activeChat.id) {
                        Toast.makeText(this, "Chat renamed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun deleteChat(chat: MioChat) {
        AlertDialog.Builder(this)
            .setTitle("Delete Chat?")
            .setMessage("Delete \"${chat.title}\" permanently?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                chats.removeAll { it.id == chat.id }

                if (chats.isEmpty()) {
                    chats.add(ChatStore.createChat())
                }

                if (activeChat.id == chat.id) {
                    activeChat = chats.first()
                }

                saveChats()
                renderActiveChat()
            }
            .show()
    }
}
