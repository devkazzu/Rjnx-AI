package com.rjnx.ai

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_chat_screen)

        findViewById<android.view.View>(R.id.chat_back).setOnClickListener {
            finish()
        }
    }
}
