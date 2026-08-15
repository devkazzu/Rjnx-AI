package com.rjnx.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_app_settings)

        findViewById<TextView>(R.id.settings_back).setOnClickListener { finish() }

        val voiceSwitch = findViewById<Switch>(R.id.setting_voice)
        val wakeSwitch = findViewById<Switch>(R.id.setting_wake)
        val notificationSwitch = findViewById<Switch>(R.id.setting_notifications)

        voiceSwitch.isChecked = prefs.getBoolean("voice", true)
        wakeSwitch.isChecked = prefs.getBoolean("wake_word", true)
        notificationSwitch.isChecked = prefs.getBoolean("notifications", true)

        voiceSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("voice", checked).apply()
        }

        wakeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("wake_word", checked).apply()
        }

        notificationSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notifications", checked).apply()
        }

        findViewById<ViewGroup>(R.id.setting_language).setOnClickListener {
            Toast.makeText(this, "English (India) • Vosk offline", Toast.LENGTH_SHORT).show()
        }

        findViewById<ViewGroup>(R.id.setting_permissions).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            })
        }

        findViewById<ViewGroup>(R.id.setting_about).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Mio")
                .setMessage("Mio Personal AI Assistant\n\nOffline voice recognition powered by Vosk.\nRJNX AI")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
