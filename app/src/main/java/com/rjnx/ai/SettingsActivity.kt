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

    private lateinit var voiceSwitch: Switch
    private lateinit var wakeSwitch: Switch
    private lateinit var notificationSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_app_settings)

        voiceSwitch = findViewById(R.id.setting_voice)
        wakeSwitch = findViewById(R.id.setting_wake)
        notificationSwitch = findViewById(R.id.setting_notifications)

        findViewById<TextView>(R.id.settings_back).setOnClickListener { finish() }

        bindListeners()
        refreshSwitches()
    }

    override fun onResume() {
        super.onResume()
        if (::voiceSwitch.isInitialized) refreshSwitches()
    }

    private fun bindListeners() {
        voiceSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("voice", checked).apply()

            if (checked) {
                if (hasMicPermission()) {
                    startVoiceService()
                } else {
                    requestPermissions(
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        REQUEST_MIC
                    )
                }
            } else {
                stopService(Intent(this, RjnxVoiceService::class.java))
            }
        }

        wakeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("wake_word", checked).apply()

            if (checked && prefs.getBoolean("voice", true)) {
                if (hasMicPermission()) startVoiceService()
                else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            }
        }

        notificationSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notifications", checked).apply()
            if (checked) requestNotificationPermissionIfNeeded()
        }

        findViewById<ViewGroup>(R.id.setting_language).setOnClickListener {
            showLanguageDialog()
        }

        findViewById<ViewGroup>(R.id.setting_permissions).setOnClickListener {
            openAppSettings()
        }

        findViewById<ViewGroup>(R.id.setting_about).setOnClickListener {
            showAbout()
        }
    }

    private fun refreshSwitches() {
        voiceSwitch.setOnCheckedChangeListener(null)
        wakeSwitch.setOnCheckedChangeListener(null)
        notificationSwitch.setOnCheckedChangeListener(null)

        voiceSwitch.isChecked = prefs.getBoolean("voice", true)
        wakeSwitch.isChecked = prefs.getBoolean("wake_word", true)
        notificationSwitch.isChecked = prefs.getBoolean("notifications", true)

        bindListeners()
    }

    private fun hasMicPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

    private fun startVoiceService() {
        try {
            val intent = Intent(this, RjnxVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Could not start Mio voice service.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            "English (India)" to "en-IN",
            "English (US)" to "en-US",
            "Hindi (India)" to "hi-IN",
            "Assamese (India)" to "as-IN",
            "Bengali (India)" to "bn-IN"
        )

        val current = prefs.getString("tts_language", "en-IN") ?: "en-IN"
        val selected = languages.indexOfFirst { it.second == current }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Language & Voice")
            .setSingleChoiceItems(languages.map { it.first }.toTypedArray(), selected) { dialog, which ->
                prefs.edit().putString("tts_language", languages[which].second).apply()
                Toast.makeText(this, "${languages[which].first} selected", Toast.LENGTH_SHORT).show()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                if (prefs.getBoolean("voice", true)) startVoiceService()
            } else {
                voiceSwitch.setOnCheckedChangeListener(null)
                voiceSwitch.isChecked = false
                prefs.edit().putBoolean("voice", false).apply()
                bindListeners()
                Toast.makeText(this, "Microphone permission is required for Voice Assistant.", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == REQUEST_NOTIFICATIONS &&
            grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        ) {
            notificationSwitch.setOnCheckedChangeListener(null)
            notificationSwitch.isChecked = false
            prefs.edit().putBoolean("notifications", false).apply()
            bindListeners()
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
                "Mio Personal AI Assistant\\n\\n" +
                    "Version $version\\n" +
                    "RJNX AI\\n\\n" +
                    "Offline voice recognition: Vosk\\n" +
                    "AI: OpenRouter"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        private const val REQUEST_MIC = 801
        private const val REQUEST_NOTIFICATIONS = 802
    }
}
