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
        val c=rawCommand.lowercase().trim()
        if(c.isEmpty()) return false

        return when {
            isTime(c) -> speakLocal(context, "Current time is ${SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date())}")
            isDate(c) -> speakLocal(context, "Today is ${SimpleDateFormat("EEEE, d MMMM yyyy",Locale.getDefault()).format(Date())}")
            isBattery(c) -> {
                val bm=context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speakLocal(context,"Battery is $level percent"); true
            }
            isDevice(c) -> {
                speakLocal(context,"You are using ${Build.MANUFACTURER} ${Build.MODEL}"); true
            }

            isSearch(c) -> extractSearch(rawCommand).takeIf{it.isNotBlank()}?.let{search(context,it)} ?: false
            isWebsite(c) -> extractWebsite(rawCommand).takeIf{it.isNotBlank()}?.let{openWebsite(context,it)} ?: false

            containsAny(c,"volume up","increase volume","volume badhao","awaaz badhao","sound badhao") -> volume(context,AudioManager.ADJUST_RAISE)
            containsAny(c,"volume down","decrease volume","volume kam","awaaz kam","sound kam") -> volume(context,AudioManager.ADJUST_LOWER)
            containsAny(c,"mute","silent") -> volume(context,AudioManager.ADJUST_MUTE)

            containsAny(c,"flashlight","torch") && containsAny(c,"on","chala","chalu","start") -> flashlight(context,true)
            containsAny(c,"flashlight","torch") && containsAny(c,"off","band","stop") -> flashlight(context,false)

            containsAny(c,"wifi") && containsAny(c,"settings","setting","open","khol","kholo") -> system(context,Settings.ACTION_WIFI_SETTINGS)
            containsAny(c,"bluetooth") && containsAny(c,"settings","setting","open","khol","kholo") -> system(context,Settings.ACTION_BLUETOOTH_SETTINGS)
            containsAny(c,"notification") && containsAny(c,"settings","setting","open","khol","kholo") -> system(context,Settings.ACTION_APP_NOTIFICATION_SETTINGS)

            containsAny(c,"youtube","you tube") && containsAny(c,"open","khol","kholo","launch","start") -> openPackage(context,"com.google.android.youtube")
            containsAny(c,"chrome","browser") && containsAny(c,"open","khol","kholo","launch","start") -> openPackage(context,"com.android.chrome")
            containsAny(c,"gallery","photos") && containsAny(c,"open","khol","kholo","launch","start") -> gallery(context)
            containsAny(c,"camera") && containsAny(c,"open","khol","kholo","launch","start") -> camera(context)
            containsAny(c,"calculator","calc") && containsAny(c,"open","khol","kholo","launch","start") -> calculator(context)
            containsAny(c,"settings","setting") && containsAny(c,"open","khol","kholo","launch","start") -> system(context,Settings.ACTION_SETTINGS)
            else -> false
        }
    }

    private fun speakLocal(c:Context,text:String):Boolean {
        val i=Intent(c,MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("MIO_LOCAL_REPLY",text)
        }
        return try{c.startActivity(i);true}catch(_:Exception){false}
    }
    private fun isTime(s:String)=containsAny(s,"what time","time now","current time","abhi kitna time","kitne baje")
    private fun isDate(s:String)=containsAny(s,"today date","date today","what date","aaj ka date","aaj ki date")
    private fun isBattery(s:String)=containsAny(s,"battery","charge") && containsAny(s,"how much","kitna","percent","percentage","level")
    private fun isDevice(s:String)=containsAny(s,"phone model","device model","which phone","kaunsa phone","mera phone")
    private fun containsAny(s:String,vararg w:String)=w.any{s.contains(it)}
    private fun isSearch(s:String)=s.startsWith("search ")||s.startsWith("google ")||s.startsWith("find ")||s.contains("search for ")
    private fun extractSearch(raw:String):String{val t=raw.trim();val l=t.lowercase();for(x in listOf("search for ","search ","google ","find "))if(l.startsWith(x))return t.substring(x.length).trim();return ""}
    private fun isWebsite(s:String)=s.startsWith("open http://")||s.startsWith("open https://")||s.startsWith("open www.")||s.startsWith("website ")||s.startsWith("open website ")
    private fun extractWebsite(raw:String):String{var t=raw.trim();val l=t.lowercase();for(x in listOf("open website ","website ","open "))if(l.startsWith(x)){t=t.substring(x.length).trim();break};return if(t.contains("://"))t else "https://$t"}
    private fun search(c:Context,q:String)=safe(c,Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+URLEncoder.encode(q,"UTF-8"))).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK})
    private fun openWebsite(c:Context,u:String)=if(URLUtil.isNetworkUrl(u))safe(c,Intent(Intent.ACTION_VIEW,Uri.parse(u)).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK})else false
    private fun volume(c:Context,d:Int):Boolean{(c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC,d,AudioManager.FLAG_SHOW_UI);return true}
    private fun flashlight(c:Context,on:Boolean)=try{if(Build.VERSION.SDK_INT<23)return false;val m=c.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager;val id=m.cameraIdList.firstOrNull()?:return false;m.setTorchMode(id,on);true}catch(_:Exception){false}
    private fun openPackage(c:Context,p:String):Boolean{val i=c.packageManager.getLaunchIntentForPackage(p)?:return false;i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);return true}
    private fun gallery(c:Context)=safe(c,Intent(Intent.ACTION_VIEW).apply{type="image/*";flags=Intent.FLAG_ACTIVITY_NEW_TASK})
    private fun camera(c:Context)=safe(c,Intent("android.media.action.IMAGE_CAPTURE").apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK})
    private fun calculator(c:Context):Boolean{for(p in listOf("com.google.android.calculator","com.sec.android.app.popupcalculator","com.android.calculator2"))if(openPackage(c,p))return true;return false}
    private fun system(c:Context,a:String)=safe(c,Intent(a).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK})
    private fun safe(c:Context,i:Intent)=try{c.startActivity(i);true}catch(_:Exception){false}
}