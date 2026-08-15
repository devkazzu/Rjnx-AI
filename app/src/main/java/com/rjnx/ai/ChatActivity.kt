package com.rjnx.ai

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_chat_screen)

        chatContainer = findViewById(R.id.chat_container)
        input = findViewById(R.id.chat_input)
        scroll = findViewById(R.id.chat_scroll)

        findViewById<View>(R.id.chat_back).setOnClickListener { finish() }

        findViewById<View>(R.id.chat_new).setOnClickListener {
            chatContainer.removeAllViews()
            addBotMessage("Hello Raju! 👋\nHow can I help you today?")
        }

        findViewById<View>(R.id.chat_send).setOnClickListener {
            sendMessage()
        }

        findViewById<View>(R.id.chat_mic).setOnClickListener {
            // Existing Vosk voice flow can be connected here.
            input.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        addBotMessage("Hello Raju! 👋\nHow can I help you today?")
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return

        addUserMessage(message)
        input.text.clear()

        // UI is ready for the existing OpenRouter integration.
        // The API call should be connected here without changing this screen.
        addBotMessage("I'm ready, Raju. 🤖\nYour OpenRouter response can appear here.")

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
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

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.END
        params.setMargins(50, 8, 14, 8)
        chatContainer.addView(bubble, params)
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
