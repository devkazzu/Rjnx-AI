package com.rjnx.ai

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.webkit.URLUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URLEncoder

object MioCommandRouter {

    fun execute(context: Context, rawCommand: String): Boolean {
        val c = rawCommand.lowercase().trim()
        if (c.isEmpty()) return false

        return when {
            isTime(c) -> {
                speakLocal(context, "Current time is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}")
            }

            isDate(c) -> {
                speakLocal(context, "Today is ${SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())}")
            }

            isBattery(c) -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speakLocal(context, "Battery is $level percent")
                true
            }

            isDevice(c) -> {
                speakLocal(context, "You are using ${Build.MANUFACTURER} ${Build.MODEL}")
            }

            isSearch(c) -> {
                val query = extractSearch(rawCommand)
                if (query.isNotBlank()) search(context, query) else false
            }

            isWebsite(c) -> {
                val target = extractWebsite(rawCommand)
                if (target.isNotBlank()) openWebsite(context, target) else false
            }

            containsAny(c, "volume up", "increase volume", "volume badhao", "awaaz badhao", "sound badhao") ->
                volume(context, AudioManager.ADJUST_RAISE)

            containsAny(c, "volume down", "decrease volume", "volume kam", "awaaz kam", "sound kam") ->
                volume(context, AudioManager.ADJUST_LOWER)

            containsAny(c, "mute", "silent") ->
                volume(context, AudioManager.ADJUST_MUTE)

            containsAny(c, "flashlight", "torch") &&
                containsAny(c, "on", "chala", "chalu", "start") ->
                flashlight(context, true)

            containsAny(c, "flashlight", "torch") &&
                containsAny(c, "off", "band", "stop") ->
                flashlight(context, false)

            containsAny(c, "wifi") &&
                containsAny(c, "settings", "setting", "open", "khol", "kholo") ->
                system(context, Settings.ACTION_WIFI_SETTINGS)

            containsAny(c, "bluetooth") &&
                containsAny(c, "settings", "setting", "open", "khol", "kholo") ->
                system(context, Settings.ACTION_BLUETOOTH_SETTINGS)

            containsAny(c, "notification") &&
                containsAny(c, "settings", "setting", "open", "khol", "kholo") ->
                system(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)

            containsAny(c, "youtube", "you tube") &&
                containsAny(c, "open", "khol", "kholo", "launch", "start") ->
                openPackage(context, "com.google.android.youtube")

            containsAny(c, "chrome", "browser") &&
                containsAny(c, "open", "khol", "kholo", "launch", "start") ->
                openPackage(context, "com.android.chrome")

            containsAny(c, "gallery", "photos") &&
                containsAny(c, "open", "khol", "kholo", "launch", "start") ->
                gallery(context)

            containsAny(c, "camera") &&
                containsAny(c, "open", "khol", "kholo", "launch", "start") ->
                camera(context)

            containsAny(c, "calculator", "calc") &&
                containsAny(c, "open", "khol", "kholo", "launch", "start") ->
                calculator(context)

            containsAny(c, "settings", "setting") &&
                containsAny(c, "open", "khol", "kholo", "launch", "start") ->
                system(context, Settings.ACTION_SETTINGS)

            else -> false
        }
    }

    private fun speakLocal(context: Context, text: String): Boolean {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("MIO_LOCAL_REPLY", text)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isTime(s: String): Boolean =
        containsAny(s, "what time", "time now", "current time", "abhi kitna time", "kitne baje")

    private fun isDate(s: String): Boolean =
        containsAny(s, "today date", "date today", "what date", "aaj ka date", "aaj ki date")

    private fun isBattery(s: String): Boolean =
        containsAny(s, "battery", "charge") &&
            containsAny(s, "how much", "kitna", "percent", "percentage", "level")

    private fun isDevice(s: String): Boolean =
        containsAny(s, "phone model", "device model", "which phone", "kaunsa phone", "mera phone")

    private fun containsAny(s: String, vararg words: String): Boolean =
        words.any { s.contains(it) }

    private fun isSearch(s: String): Boolean =
        s.startsWith("search ") ||
            s.startsWith("google ") ||
            s.startsWith("find ") ||
            s.contains("search for ")

    private fun extractSearch(raw: String): String {
        val text = raw.trim()
        val lower = text.lowercase()

        for (prefix in listOf("search for ", "search ", "google ", "find ")) {
            if (lower.startsWith(prefix)) {
                return text.substring(prefix.length).trim()
            }
        }

        return ""
    }

    private fun isWebsite(s: String): Boolean =
        s.startsWith("open http://") ||
            s.startsWith("open https://") ||
            s.startsWith("open www.") ||
            s.startsWith("website ") ||
            s.startsWith("open website ")

    private fun extractWebsite(raw: String): String {
        var text = raw.trim()
        val lower = text.lowercase()

        for (prefix in listOf("open website ", "website ", "open ")) {
            if (lower.startsWith(prefix)) {
                text = text.substring(prefix.length).trim()
                break
            }
        }

        return if (text.contains("://")) text else "https://$text"
    }

    private fun search(context: Context, query: String): Boolean {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=$encoded")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return safe(context, intent)
    }

    private fun openWebsite(context: Context, url: String): Boolean {
        if (!URLUtil.isNetworkUrl(url)) return false

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return safe(context, intent)
    }

    private fun volume(context: Context, direction: Int): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        return true
    }

    private fun flashlight(context: Context, on: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        return try {
            val manager =
                context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = manager.cameraIdList.firstOrNull() ?: return false
            manager.setTorchMode(cameraId, on)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openPackage(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun gallery(context: Context): Boolean =
        safe(context, Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })

    private fun camera(context: Context): Boolean =
        safe(context, Intent("android.media.action.IMAGE_CAPTURE").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })

    private fun calculator(context: Context): Boolean {
        for (packageName in listOf(
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.android.calculator2"
        )) {
            if (openPackage(context, packageName)) return true
        }
        return false
    }

    private fun system(context: Context, action: String): Boolean =
        safe(context, Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })

    private fun safe(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
}
