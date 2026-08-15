package com.rjnx

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.TextView
import java.util.Calendar

class MioGreetingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {
        override fun run() {
            updateGreeting()
            handler.postDelayed(this, 60_000L)
        }
    }

    init {
        updateGreeting()
        handler.postDelayed(updater, 60_000L)
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        text = when (hour) {
            in 5..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            in 17..20 -> "Good Evening,"
            else -> "Good Night,"
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(updater)
        super.onDetachedFromWindow()
    }
}
