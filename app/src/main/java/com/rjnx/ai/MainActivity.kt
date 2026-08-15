package com.rjnx.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tapToSpeak: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mio_glass_orbit)

        bindAllButtons()

        if (intent.getBooleanExtra("MIO_WAKE", false)) {
            val command = intent.getStringExtra("MIO_COMMAND").orEmpty()
            tapToSpeak.text = if (command.isBlank()) {
                "🎙  Listening..."
            } else {
                "🎙  Heard: $command"
            }
        }
    }

    private fun bindAllButtons() {
        tapToSpeak = findViewById(R.id.btn_tap_to_speak)
        tapToSpeak.setOnClickListener { startTapToSpeak() }

        findViewById<android.view.View>(R.id.btn_settings).setOnClickListener {
            openSystemSettings()
        }

        findViewById<android.view.View>(R.id.btn_help_center).setOnClickListener {
            openWeb("https://support.google.com/")
        }

        findViewById<android.view.View>(R.id.action_voice).setOnClickListener {
            startTapToSpeak()
        }

        findViewById<android.view.View>(R.id.action_vision).setOnClickListener {
            openCamera()
        }

        findViewById<android.view.View>(R.id.action_translate).setOnClickListener {
            openWeb("https://translate.google.com/")
        }

        findViewById<android.view.View>(R.id.action_chat).setOnClickListener {
            startTapToSpeak()
        }

        findViewById<android.view.View>(R.id.action_youtube).setOnClickListener {
            openWeb("https://www.youtube.com/")
        }

        findViewById<android.view.View>(R.id.action_camera).setOnClickListener {
            openCamera()
        }

        findViewById<android.view.View>(R.id.nav_home).setOnClickListener {
            Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.nav_chats).setOnClickListener {
            startTapToSpeak()
        }

        findViewById<android.view.View>(R.id.nav_tools).setOnClickListener {
            Toast.makeText(this, "Mio Tools", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.nav_settings).setOnClickListener {
            openSystemSettings()
        }

        // Quick Actions currently have no separate screens in the project.
        // They are connected so every visible row responds instead of doing nothing.
        val quickRows = listOf(
            "Summarize Screen",
            "Solve Anything",
            "Help me Code",
            "Tell me a Story"
        )

        val quickContainers = findQuickActionRows()
        quickContainers.forEachIndexed { index, view ->
            view.setOnClickListener {
                Toast.makeText(this, quickRows[index], Toast.LENGTH_SHORT).show()
                startTapToSpeak()
            }
        }
    }

    private fun findQuickActionRows(): List<android.view.View> {
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val result = mutableListOf<android.view.View>()
        collectQuickRows(root, result)
        return result.take(4)
    }

    private fun collectQuickRows(parent: android.view.ViewGroup, result: MutableList<android.view.View>) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)

            if (child is android.view.ViewGroup) {
                val text = child.findFirstText()
                if (text in setOf(
                        "Summarize Screen",
                        "Solve Anything",
                        "Help me Code",
                        "Tell me a Story"
                    )
                ) {
                    result.add(child)
                    continue
                }
                collectQuickRows(child, result)
            }
        }
    }

    private fun android.view.ViewGroup.findFirstText(): String? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is TextView) return child.text?.toString()
            if (child is android.view.ViewGroup) {
                val nested = child.findFirstText()
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun startTapToSpeak() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO
            )
            return
        }

        tapToSpeak.text = "🎙  Listening..."

        val serviceIntent = Intent(this, RjnxVoiceService::class.java).apply {
            putExtra("MIO_TAP_TO_SPEAK", true)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Mio voice service could not start",
                Toast.LENGTH_SHORT
            ).show()
            tapToSpeak.text = "🎙  Tap to speak"
        }
    }

    private fun openCamera() {
        try {
            startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
        } catch (_: Exception) {
            Toast.makeText(this, "Camera app not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSystemSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Settings unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWeb(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_AUDIO &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startTapToSpeak()
        } else if (requestCode == REQUEST_AUDIO) {
            tapToSpeak.text = "🎙  Tap to speak"
            Toast.makeText(
                this,
                "Microphone permission is required",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val REQUEST_AUDIO = 401
    }
}
