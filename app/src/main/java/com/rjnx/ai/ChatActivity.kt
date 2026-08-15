package com.rjnx.ai

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts

class ChatActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var send: TextView

    private var chats = mutableListOf<MioChat>()
    private lateinit var activeChat: MioChat

    private var pendingAttachmentUri: Uri? = null
    private var pendingAttachmentName: String? = null
    private var pendingAttachmentMime: String? = null

    private val attachmentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers don't support persistable permissions.
            }

            pendingAttachmentUri = uri
            pendingAttachmentName = getFileName(uri)
            pendingAttachmentMime = contentResolver.getType(uri)
            input.hint = "Add a message (attachment ready)"
            Toast.makeText(
                this,
                "Attached: ${pendingAttachmentName ?: "file"}",
                Toast.LENGTH_SHORT
            ).show()
        }

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

        // Top action: create a new chat immediately.
        findViewById<View>(R.id.chat_new).setOnClickListener {
            createNewChat()
        }

        // Three-dot overflow menu.
        findViewById<View>(R.id.chat_menu).setOnClickListener {
            showOverflowMenu(it)
        }

        // Bottom + button: attachment picker (image or file).
        findViewById<View>(R.id.chat_plus).setOnClickListener {
            showAttachmentMenu(it)
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
        val attachment = pendingAttachmentUri

        if (message.isEmpty() && attachment == null) return
        if (!send.isEnabled) return

        val attachmentName = pendingAttachmentName
        val attachmentMime = pendingAttachmentMime.orEmpty()

        val visibleMessage = buildString {
            if (attachmentName != null) {
                append("📎 ")
                append(attachmentName)
                if (message.isNotEmpty()) append("\n")
            }
            append(message)
        }.trim()

        addUserMessage(visibleMessage)
        input.text.clear()
        input.hint = "Type a message..."
        pendingAttachmentUri = null
        pendingAttachmentName = null
        pendingAttachmentMime = null
        setSending(true)

        if (attachment != null && attachmentMime.startsWith("image/")) {
            val bitmap = contentResolver.openInputStream(attachment)?.use {
                BitmapFactory.decodeStream(it)
            }

            if (bitmap == null) {
                addBotMessage("I couldn't open that image.")
                setSending(false)
                return
            }

            val prompt = if (message.isNotEmpty()) {
                message
            } else {
                "Analyze this attached image and tell me what you see."
            }

            OpenRouterClient.askVision(bitmap, prompt) { answer ->
                bitmap.recycle()
                addBotMessage(answer)
                setSending(false)
                saveChats()
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
            return
        }

        if (attachment != null && (attachmentMime.startsWith("text/") ||
                    attachmentName?.endsWith(".txt", true) == true ||
                    attachmentName?.endsWith(".json", true) == true ||
                    attachmentName?.endsWith(".csv", true) == true ||
                    attachmentName?.endsWith(".md", true) == true)) {

            val fileText = try {
                contentResolver.openInputStream(attachment)?.bufferedReader()?.use {
                    it.readText().take(12000)
                }.orEmpty()
            } catch (_: Exception) {
                ""
            }

            if (fileText.isBlank()) {
                addBotMessage("I couldn't read that text file.")
                setSending(false)
                return
            }

            val prompt = buildString {
                append("The user attached a text file named \"$attachmentName\".\n")
                append("File content:\n")
                append(fileText)
                if (message.isNotEmpty()) {
                    append("\n\nUser request:\n")
                    append(message)
                } else {
                    append("\n\nSummarize or explain this file briefly.")
                }
            }

            OpenRouterClient.ask(prompt) { answer ->
                addBotMessage(answer)
                setSending(false)
                saveChats()
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
            return
        }

        if (attachment != null) {
            addBotMessage(
                "📎 $attachmentName attached. I can currently analyze images and text files here. " +
                    "This file type isn't readable by Mio yet."
            )
            setSending(false)
            saveChats()
            return
        }

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

        val avatar = ImageView(this).apply {
            setImageResource(R.drawable.ic_mio)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = getDrawable(R.drawable.mio_chat_avatar_circle)
            clipToOutline = true
            isClickable = false
            contentDescription = "Mio"
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

    private fun showAttachmentMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 2001, 0, "🖼️ Image / Photo")
        popup.menu.add(0, 2002, 1, "📎 File")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                2001 -> {
                    attachmentPicker.launch(arrayOf("image/*"))
                    true
                }
                2002 -> {
                    attachmentPicker.launch(arrayOf("*/*"))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun getFileName(uri: Uri): String {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "attachment"
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_NEW, 0, "New Chat")
        popup.menu.add(0, MENU_HISTORY, 1, "Chat History")
        popup.menu.add(0, MENU_RENAME, 2, "Rename Chat")
        popup.menu.add(0, MENU_CLEAR, 3, "Clear Conversation")
        popup.menu.add(0, MENU_DELETE, 4, "Delete Chat")
        popup.menu.add(0, MENU_EXPORT, 5, "Copy Chat")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_NEW -> {
                    createNewChat()
                    true
                }
                MENU_HISTORY -> {
                    showChatManager()
                    true
                }
                MENU_RENAME -> {
                    renameChat(activeChat)
                    true
                }
                MENU_CLEAR -> {
                    clearCurrentConversation()
                    true
                }
                MENU_DELETE -> {
                    deleteChat(activeChat)
                    true
                }
                MENU_EXPORT -> {
                    copyCurrentChat()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun clearCurrentConversation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Conversation?")
            .setMessage("Remove all messages from \"${activeChat.title}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                activeChat.messages.clear()
                saveChats()
                renderActiveChat()
                Toast.makeText(this, "Conversation cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun copyCurrentChat() {
        val text = buildString {
            append(activeChat.title)
            append("\\n\\n")
            activeChat.messages.forEach { message ->
                append(if (message.role == "user") "You: " else "Mio: ")
                append(message.text)
                append("\\n\\n")
            }
        }.trim()

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Mio Chat", text))
        Toast.makeText(this, "Chat copied", Toast.LENGTH_SHORT).show()
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
    companion object {
        private const val MENU_NEW = 1001
        private const val MENU_HISTORY = 1002
        private const val MENU_RENAME = 1003
        private const val MENU_CLEAR = 1004
        private const val MENU_DELETE = 1005
        private const val MENU_EXPORT = 1006
    }

}
