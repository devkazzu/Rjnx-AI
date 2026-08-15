package com.rjnx.ai

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_app_settings)
        bindSettings()
    }

    override fun onResume() {
        super.onResume()
        if (settingsBound) refreshSwitches()
    }

    private var settingsBound = false

    private fun bindSettings() {
        findViewById<TextView>(R.id.settings_back).setOnClickListener { finish() }

        val voiceSwitch = findViewById<Switch>(R.id.setting_voice)
        val wakeSwitch = findViewById<Switch>(R.id.setting_wake)
        val notificationSwitch = findViewById<Switch>(R.id.setting_notifications)

        settingsBound = true
        refreshSwitches(voiceSwitch, wakeSwitch, notificationSwitch)

        voiceSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("voice", checked).apply()

            if (checked) {
                startMioService()
            } else {
                stopService(Intent(this, RjnxVoiceService::class.java))
            }
        }

        wakeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("wake_word", checked).apply()
            if (checked && prefs.getBoolean("voice", true)) {
                startMioService()
            }
        }

        notificationSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notifications", checked).apply()
            if (checked) {
                requestNotificationPermissionIfNeeded()
            } else {
                Toast.makeText(
                    this,
                    "Assistant notification updates are off.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        findViewById<ViewGroup>(R.id.setting_language).setOnClickListener {
            showLanguageVoiceDialog()
        }

        findViewById<ViewGroup>(R.id.setting_permissions).setOnClickListener {
            openAppSettings()
        }

        findViewById<ViewGroup>(R.id.setting_about).setOnClickListener {
            showAbout()
        }
    }

    private fun refreshSwitches(
        voice: Switch = findViewById(R.id.setting_voice),
        wake: Switch = findViewById(R.id.setting_wake),
        notifications: Switch = findViewById(R.id.setting_notifications)
    ) {
        voice.setOnCheckedChangeListener(null)
        wake.setOnCheckedChangeListener(null)
        notifications.setOnCheckedChangeListener(null)

        voice.isChecked = prefs.getBoolean("voice", true)
        wake.isChecked = prefs.getBoolean("wake_word", true)
        notifications.isChecked = prefs.getBoolean("notifications", true)
    }

    private fun startMioService() {
        val intent = Intent(this, RjnxVoiceService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start Mio voice service.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLanguageVoiceDialog() {
        val languages = arrayOf(
            "English (India)",
            "English (US)",
            "Hindi (India)"
        )

        val saved = prefs.getString("tts_language", "en-IN") ?: "en-IN"
        val selected = when (saved) {
            "en-US" -> 1
            "hi-IN" -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Language & Voice")
            .setSingleChoiceItems(languages, selected) { dialog, which ->
                val tag = when (which) {
                    1 -> "en-US"
                    2 -> "hi-IN"
                    else -> "en-IN"
                }

                prefs.edit().putString("tts_language", tag).apply()

                val locale = Locale.forLanguageTag(tag)
                var tempTts: android.speech.tts.TextToSpeech? = null
                tempTts = android.speech.tts.TextToSpeech(this) { status ->
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        tempTts?.language = locale
                        tempTts?.stop()
                        tempTts?.shutdown()
                    }
                }

                Toast.makeText(this, "${languages[which]} selected", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "Unknown"
        }

        AlertDialog.Builder(this)
            .setTitle("Mio")
            .setMessage(
                "Mio Personal AI Assistant\n\n" +
                    "Version $version\n" +
                    "RJNX AI\n\n" +
                    "Offline voice recognition powered by Vosk.\n" +
                    "AI responses powered by OpenRouter."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 801
    }
}
