package com.rjnx.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ChatActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var send: TextView

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

        findViewById<View>(R.id.chat_back).setOnClickListener { finish() }

        findViewById<View>(R.id.chat_new).setOnClickListener {
            chatContainer.removeAllViews()
            addBotMessage("Hello Raju! 👋\nHow can I help you today?")
        }

        send.setOnClickListener { sendMessage() }

        findViewById<View>(R.id.chat_mic).setOnClickListener {
            startChatVoiceRecognition()
        }

        input.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        addBotMessage("Hello Raju! 👋\nHow can I help you today?")
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

    private fun startChatVoiceRecognition() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
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
        if (message.isEmpty()) return

        addUserMessage(message)
        input.text.clear()
        input.hint = "Type a message..."
        setSending(true)

        OpenRouterClient.ask(message) { answer ->
            addBotMessage(answer)
            setSending(false)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun setSending(sending: Boolean) {
        send.isEnabled = !sending
        send.alpha = if (sending) 0.45f else 1f
    }

    private fun addUserMessage(message: String) {
        val bubble = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 13, 20, 13)
            background = getDrawable(R.drawable.mio_chat_user_bubble)
        }
        chatContainer.addView(bubble, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END
            setMargins(50, 8, 14, 8)
        })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBotMessage(message: String) {
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
        row.addView(bubble, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 50, 0)
        })
        chatContainer.addView(row)
    }
}
