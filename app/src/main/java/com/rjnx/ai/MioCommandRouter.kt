package com.rjnx.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object MioCommandRouter {

    fun execute(context: Context, rawCommand: String): Boolean {
        val command = rawCommand.lowercase().trim()
        if (command.isEmpty()) return false

        return when {
            containsAny(command, "youtube", "you tube") && containsAny(command, "open", "khol", "kholo", "launch", "start") ->
                openPackage(context, "com.google.android.youtube")

            containsAny(command, "chrome", "browser") && containsAny(command, "open", "khol", "kholo", "launch", "start") ->
                openPackage(context, "com.android.chrome")

            containsAny(command, "gallery", "photos") && containsAny(command, "open", "khol", "kholo", "launch", "start") ->
                openGallery(context)

            containsAny(command, "camera") && containsAny(command, "open", "khol", "kholo", "launch", "start") ->
                openCamera(context)

            containsAny(command, "calculator", "calc") && containsAny(command, "open", "khol", "kholo", "launch", "start") ->
                openCalculator(context)

            containsAny(command, "settings", "setting") && containsAny(command, "open", "khol", "kholo", "launch", "start") ->
                openSettings(context)

            containsAny(command, "wifi") && containsAny(command, "settings", "setting", "open", "khol", "kholo") ->
                openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS)

            containsAny(command, "bluetooth") && containsAny(command, "settings", "setting", "open", "khol", "kholo") ->
                openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)

            containsAny(command, "notification") && containsAny(command, "settings", "setting", "open", "khol", "kholo") ->
                openSystemSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)

            else -> false
        }
    }

    private fun containsAny(text: String, vararg words: String): Boolean =
        words.any { text.contains(it) }

    private fun openPackage(context: Context, packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    private fun openGallery(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return startSafely(context, intent)
    }

    private fun openCamera(context: Context): Boolean {
        val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return startSafely(context, intent)
    }

    private fun openCalculator(context: Context): Boolean {
        val candidates = listOf(
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.android.calculator2"
        )
        for (pkg in candidates) {
            if (openPackage(context, pkg)) return true
        }
        return false
    }

    private fun openSettings(context: Context): Boolean =
        openSystemSettings(context, Settings.ACTION_SETTINGS)

    private fun openSystemSettings(context: Context, action: String): Boolean {
        return startSafely(context, Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
