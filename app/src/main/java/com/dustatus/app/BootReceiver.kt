package com.dustatus.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent?) {
  if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
  val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  if (!prefs.getBoolean("auto_boot", false) || !prefs.getBoolean("running", false) || !Settings.canDrawOverlays(context)) return
  try {
   val service = Intent(context, SpeedService::class.java)
   if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service)
   else context.startService(service)
  } catch (_: Exception) {
   // Some OEMs restrict foreground-service launches after boot.
  }
 }
}
