package com.rjnx.ai

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.net.Uri
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MioCommandRouter {

    fun execute(context: Context, rawCommand: String): Boolean {
        val c = rawCommand.lowercase().trim()
        if (c.isEmpty()) return false

        return when {
            isTime(c) -> speakLocal(context, "Current time is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}")
            isDate(c) -> speakLocal(context, "Today is ${SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())}")
            isBattery(c) -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speakLocal(context, "Battery is $level percent")
            }
            isDevice(c) -> speakLocal(context, "You are using ${Build.MANUFACTURER} ${Build.MODEL}")
            isAlarm(c) -> scheduleAlarm(context, rawCommand)
            isWhatsApp(c) -> handleWhatsApp(context, rawCommand)
            isSms(c) -> handleSms(context, rawCommand)
            isCall(c) -> handleCall(context, rawCommand)
            isSearch(c) -> extractSearch(rawCommand).takeIf { it.isNotBlank() }?.let { search(context, it) } ?: false
            isWebsite(c) -> extractWebsite(rawCommand).takeIf { it.isNotBlank() }?.let { openWebsite(context, it) } ?: false
            containsAny(c, "volume up", "increase volume", "volume badhao", "awaaz badhao", "sound badhao") -> volume(context, AudioManager.ADJUST_RAISE)
            containsAny(c, "volume down", "decrease volume", "volume kam", "awaaz kam", "sound kam") -> volume(context, AudioManager.ADJUST_LOWER)
            containsAny(c, "mute", "silent") -> volume(context, AudioManager.ADJUST_MUTE)
            containsAny(c, "flashlight", "torch") && containsAny(c, "on", "chala", "chalu", "start") -> flashlight(context, true)
            containsAny(c, "flashlight", "torch") && containsAny(c, "off", "band", "stop") -> flashlight(context, false)
            containsAny(c, "wifi") && containsAny(c, "settings", "setting", "open", "khol", "kholo") -> system(context, Settings.ACTION_WIFI_SETTINGS)
            containsAny(c, "bluetooth") && containsAny(c, "settings", "setting", "open", "khol", "kholo") -> system(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            containsAny(c, "notification") && containsAny(c, "settings", "setting", "open", "khol", "kholo") -> system(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            containsAny(c, "youtube", "you tube") && containsAny(c, "open", "khol", "kholo", "launch", "start") -> openPackageOrWeb(context, "com.google.android.youtube", "https://www.youtube.com")
            containsAny(c, "chrome", "browser") && containsAny(c, "open", "khol", "kholo", "launch", "start") -> openPackageOrWeb(context, "com.android.chrome", "https://www.google.com")
            containsAny(c, "gallery", "photos") && containsAny(c, "open", "khol", "kholo", "launch", "start") -> gallery(context)
            containsAny(c, "camera") && containsAny(c, "open", "khol", "kholo", "launch", "start") -> camera(context)
            containsAny(c, "calculator", "calc") && containsAny(c, "open", "khol", "kholo", "launch", "start") -> calculator(context)
            containsAny(c, "settings", "setting") && containsAny(c, "open", "khol", "kholo", "launch", "start") -> system(context, Settings.ACTION_SETTINGS)
            else -> false
        }
    }

    private fun speakLocal(context: Context, text: String): Boolean {
        return try {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("MIO_LOCAL_REPLY", text)
            })
            true
        } catch (_: Exception) { false }
    }

    private fun isCall(s: String) = containsAny(s, "call", "phone", "dial")
    private fun isSms(s: String) = containsAny(s, "sms", "text", "message", "msg") && !containsAny(s, "whatsapp")
    private fun isWhatsApp(s: String) = containsAny(s, "whatsapp", "whats app")

    private fun handleCall(context: Context, raw: String): Boolean {
        val number = extractPhoneNumber(raw)
        if (number.isNotBlank()) return confirmCall(context, number)

        val name = extractContactName(raw)
        return resolveContact(context, name) { confirmCall(context, it) }
    }

    private fun handleSms(context: Context, raw: String): Boolean {
        val number = extractPhoneNumber(raw)
        val message = extractMessage(raw)
        if (number.isNotBlank()) return confirmSms(context, number, message)

        val name = extractContactName(raw)
        return resolveContact(context, name) { confirmSms(context, it, message) }
    }

    private fun handleWhatsApp(context: Context, raw: String): Boolean {
        val number = extractPhoneNumber(raw)
        val message = extractMessage(raw)
        if (number.isNotBlank()) return confirmWhatsApp(context, number, message)

        val name = extractContactName(raw)
        return resolveContact(context, name) { confirmWhatsApp(context, it, message) }
    }

    private fun extractPhoneNumber(raw: String): String =
        Regex("""(?:\+91\s*)?[6-9]\d{9}\b""").find(raw.replace(Regex("[^0-9+]"), " "))?.value?.replace(" ", "") ?: ""

    private fun extractMessage(raw: String): String {
        val lower = raw.lowercase()
        for (marker in listOf("message ", "msg ", "text ", "sms ")) {
            val i = lower.indexOf(marker)
            if (i >= 0) return raw.substring(i + marker.length).trim()
        }
        return ""
    }

    private fun extractContactName(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("(?i)\\b(please|mujhe|ko|par|pe|se)\\b"), " ")
        s = s.replace(Regex("(?i)\\b(call|phone|dial|karo|kar do|karna|sms|text|message|msg|whatsapp|whats app|bhejo|bhej do)\\b"), " ")
        s = s.replace(Regex("""(?:\+91\s*)?[6-9]\d{9}\b"""), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun resolveContact(context: Context, name: String, onFound: (String) -> Boolean): Boolean {
        if (name.isBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (context is android.app.Activity) {
                context.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 803)
            }
            return true
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val number = cursor.getString(0).orEmpty()
                return onFound(number)
            }
        }

        ToastHelper.show(context, "Contact not found: $name")
        return true
    }

    private fun confirmCall(context: Context, number: String): Boolean {
        val activity = context as? android.app.Activity ?: return false
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("Call confirmation")
                .setMessage("Do you want to call $number?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Call") { _, _ ->
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        activity.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
                    } else {
                        activity.requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 804)
                    }
                }.show()
        }
        return true
    }

    private fun confirmSms(context: Context, number: String, message: String): Boolean {
        val activity = context as? android.app.Activity ?: return false
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("SMS confirmation")
                .setMessage("Open SMS for $number${if (message.isNotBlank()) "\\n\\n$message" else ""}")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open SMS") { _, _ ->
                    activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                        if (message.isNotBlank()) putExtra("sms_body", message)
                    })
                }.show()
        }
        return true
    }

    private fun confirmWhatsApp(context: Context, number: String, message: String): Boolean {
        val activity = context as? android.app.Activity ?: return false
        activity.runOnUiThread {
            val clean = number.replace("+", "").replace(" ", "")
            val uri = Uri.parse("https://wa.me/$clean" + if (message.isNotBlank()) "?text=${Uri.encode(message)}" else "")
            AlertDialog.Builder(activity)
                .setTitle("WhatsApp confirmation")
                .setMessage("Open WhatsApp chat with $number?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open WhatsApp") { _, _ ->
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") })
                    } catch (_: Exception) {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                }.show()
        }
        return true
    }

    private fun isAlarm(s: String): Boolean =
        containsAny(s, "alarm", "wake me", "remind me") && Regex("""\b\d{1,2}(?::\d{2})?\s*(am|pm)?\b""").containsMatchIn(s)

    private fun scheduleAlarm(context: Context, raw: String): Boolean {
        val match = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE).find(raw) ?: return false
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val ampm = match.groupValues[3].lowercase()

        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        if (hour > 23 || minute > 59) return false

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            system(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            ToastHelper.show(context, "Allow exact alarms, then try again.")
            return true
        }

        val pending = PendingIntent.getBroadcast(
            context, calendar.timeInMillis.hashCode(),
            Intent(context, RjnxAlarmReceiver::class.java).putExtra("message", "Mio alarm"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        else
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)

        ToastHelper.show(context, "Alarm set for ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)}")
        return true
    }

    private fun isTime(s: String) = containsAny(s, "what time", "time now", "current time", "abhi kitna time", "kitne baje")
    private fun isDate(s: String) = containsAny(s, "today date", "date today", "what date", "aaj ka date", "aaj ki date")
    private fun isBattery(s: String) = containsAny(s, "battery", "charge") && containsAny(s, "how much", "kitna", "percent", "percentage", "level")
    private fun isDevice(s: String) = containsAny(s, "phone model", "device model", "which phone", "kaunsa phone", "mera phone")
    private fun containsAny(s: String, vararg words: String) = words.any { s.contains(it) }
    private fun isSearch(s: String) = s.startsWith("search ") || s.startsWith("google ") || s.startsWith("find ") || s.contains("search for ")
    private fun extractSearch(raw: String): String {
        val lower = raw.lowercase()
        for (prefix in listOf("search for ", "search ", "google ", "find ")) if (lower.startsWith(prefix)) return raw.substring(prefix.length).trim()
        return ""
    }
    private fun isWebsite(s: String) = s.startsWith("open http://") || s.startsWith("open https://") || s.startsWith("open www.") || s.startsWith("website ") || s.startsWith("open website ")
    private fun extractWebsite(raw: String): String {
        var text = raw.trim(); val lower = text.lowercase()
        for (prefix in listOf("open website ", "website ", "open ")) if (lower.startsWith(prefix)) { text = text.substring(prefix.length).trim(); break }
        return if (text.contains("://")) text else "https://$text"
    }
    private fun search(context: Context, query: String): Boolean =
        safe(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    private fun openWebsite(context: Context, url: String): Boolean =
        if (URLUtil.isNetworkUrl(url)) safe(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) else false
    private fun volume(context: Context, direction: Int): Boolean {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return true
    }
    private fun flashlight(context: Context, on: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = manager.cameraIdList.firstOrNull() ?: return false
            manager.setTorchMode(cameraId, on)
            true
        } catch (_: Exception) {
            false
        }
    }
    private fun openPackageOrWeb(context: Context, packageName: String, fallback: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch); return true }
        return safe(context, Intent(Intent.ACTION_VIEW, Uri.parse(fallback)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    }
    private fun gallery(context: Context) = safe(context, Intent(Intent.ACTION_VIEW).apply { type = "image/*"; flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    private fun camera(context: Context) = safe(context, Intent("android.media.action.IMAGE_CAPTURE").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    private fun calculator(context: Context): Boolean {
        for (p in listOf("com.google.android.calculator","com.sec.android.app.popupcalculator","com.android.calculator2")) {
            val i=context.packageManager.getLaunchIntentForPackage(p); if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return true}
        }
        return false
    }
    private fun system(context: Context, action: String) = safe(context, Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    private fun safe(context: Context, intent: Intent) = try { context.startActivity(intent); true } catch (_: Exception) { false }

    private object ToastHelper {
        fun show(context: Context, text: String) =
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }
}
