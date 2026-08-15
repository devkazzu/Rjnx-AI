package com.rjnx.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.URLUtil
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MioCommandRouter {
    fun execute(context: Context, rawCommand: String): Boolean {
        val c=rawCommand.lowercase().trim()
        if(c.isEmpty()) return false
        return when {
            isSearch(c) -> extractSearch(rawCommand).takeIf{it.isNotBlank()}?.let{search(context,it)} ?: false
            isWebsite(c) -> extractWebsite(rawCommand).takeIf{it.isNotBlank()}?.let{openWebsite(context,it)} ?: false
            containsAny(c,"youtube","you tube") && containsAny(c,"open","khol","kholo","launch","start") -> openPackage(context,"com.google.android.youtube")
            containsAny(c,"chrome","browser") && containsAny(c,"open","khol","kholo","launch","start") -> openPackage(context,"com.android.chrome")
            containsAny(c,"gallery","photos") && containsAny(c,"open","khol","kholo","launch","start") -> gallery(context)
            containsAny(c,"camera") && containsAny(c,"open","khol","kholo","launch","start") -> camera(context)
            containsAny(c,"calculator","calc") && containsAny(c,"open","khol","kholo","launch","start") -> calculator(context)
            containsAny(c,"settings","setting") && containsAny(c,"open","khol","kholo","launch","start") -> settings(context)
            containsAny(c,"wifi") && containsAny(c,"settings","setting","open","khol","kholo") -> system(context,Settings.ACTION_WIFI_SETTINGS)
            containsAny(c,"bluetooth") && containsAny(c,"settings","setting","open","khol","kholo") -> system(context,Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> false
        }
    }
    private fun containsAny(s:String,vararg w:String)=w.any{s.contains(it)}
    private fun isSearch(s:String)=s.startsWith("search ")||s.startsWith("google ")||s.startsWith("find ")||s.contains("search for ")
    private fun extractSearch(raw:String):String {
        val t=raw.trim(); val l=t.lowercase()
        listOf("search for ","search ","google ","find ").forEach{if(l.startsWith(it)) return t.substring(it.length).trim()}
        val i=l.indexOf("search for "); return if(i>=0)t.substring(i+13).trim() else ""
    }
    private fun isWebsite(s:String)=s.startsWith("open http://")||s.startsWith("open https://")||s.startsWith("open www.")||s.startsWith("website ")||s.startsWith("open website ")
    private fun extractWebsite(raw:String):String {
        var t=raw.trim(); val l=t.lowercase()
        listOf("open website ","website ","open ").forEach{if(l.startsWith(it)){t=t.substring(it.length).trim();return@forEach}}
        return if(t.contains("://"))t else "https://$t"
    }
    private fun search(c:Context,q:String)=safe(c,Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+URLEncoder.encode(q,StandardCharsets.UTF_8.toString()))).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK})
    private fun openWebsite(c:Context,u:String)=if(URLUtil.isNetworkUrl(u))safe(c,Intent(Intent.ACTION_VIEW,Uri.parse(u)).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK}) else false
    private fun openPackage(c:Context,p:String):Boolean{val i=c.packageManager.getLaunchIntentForPackage(p)?:return false;i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);return true}
    private fun gallery(c:Context)=safe(c,Intent(Intent.ACTION_VIEW).apply{type="image/*";flags=Intent.FLAG_ACTIVITY_NEW_TASK})
    private fun camera(c:Context)=safe(c,Intent("android.media.action.IMAGE_CAPTURE").apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK})
    private fun calculator(c:Context):Boolean{for(p in listOf("com.google.android.calculator","com.sec.android.app.popupcalculator","com.android.calculator2"))if(openPackage(c,p))return true;return false}
    private fun settings(c:Context)=system(c,Settings.ACTION_SETTINGS)
    private fun system(c:Context,a:String)=safe(c,Intent(a).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK})
    private fun safe(c:Context,i:Intent)=try{c.startActivity(i);true}catch(_:Exception){false}
}