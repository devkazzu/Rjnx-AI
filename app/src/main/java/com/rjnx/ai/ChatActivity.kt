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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_chat_screen)

        messages = findViewById(R.id.chat_messages)
        input = findViewById(R.id.chat_input)

        findViewById<View>(R.id.chat_back).setOnClickListener { finish() }

        findViewById<View>(R.id.chat_new).setOnClickListener {
            messages.removeAllViews()
            showEmptyState()
        }

        findViewById<View>(R.id.chat_send).setOnClickListener {
            sendCurrentMessage()
        }

        input.setOnEditorActionListener { _, _, _ ->
            sendCurrentMessage()
            true
        }

        showEmptyState()
    }

    private fun showEmptyState() {
        val empty = TextView(this).apply {
            text = "💬\n\nNo chats yet\n\nYour conversations with Mio will appear here."
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(32, 0, 32, 0)
            alpha = 0.95f
        }

        messages.addView(
            empty,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun sendCurrentMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        if (messages.childCount == 1) {
            messages.removeAllViews()
        }

        addMessage("You", text, true)
        input.text.clear()

        // OpenRouter response handling can be connected here without changing the UI.
        addMessage("Mio", "I'm ready. Ask me anything.", false)

        input.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun addMessage(sender: String, text: String, mine: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 10, 16, 10)
        }

        val name = TextView(this).apply {
            this.text = sender
            setTextColor(if (mine) Color.WHITE else Color.rgb(190, 198, 225))
            textSize = 13f
        }

        val body = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 5, 0, 4)
        }

        row.addView(name)
        row.addView(body)
        messages.addView(row)
    }
}
