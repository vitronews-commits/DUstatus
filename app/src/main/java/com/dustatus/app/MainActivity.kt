package com.dustatus.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 private lateinit var status: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("settings", MODE_PRIVATE)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 56, 40, 24) }
  val title = TextView(this).apply {
   text = "DUstatus · v0.2\nDownload / Upload indicator\n\nCompact indicator below the clock."
   textSize = 18f
  }
  status = TextView(this).apply { textSize = 15f }
  val start = Button(this).apply {
   text = "Grant overlay permission / Start"
   setOnClickListener {
    if (!Settings.canDrawOverlays(this@MainActivity)) {
     startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    } else {
     if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 12)
     try {
      startForegroundService(Intent(this@MainActivity, SpeedService::class.java))
     } catch (e: Exception) {
      status.text = "Could not start service: ${e.javaClass.simpleName}"
     }
    }
   }
  }
  val stop = Button(this).apply {
   text = "Stop"
   setOnClickListener {
    prefs.edit().putBoolean("running", false).apply()
    stopService(Intent(this@MainActivity, SpeedService::class.java))
    updateStatus()
   }
  }
  val boot = CheckBox(this).apply {
   text = "Start automatically after reboot"
   isChecked = prefs.getBoolean("auto_boot", false)
   setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("auto_boot", checked).apply() }
  }
  root.addView(title); root.addView(status); root.addView(start); root.addView(stop); root.addView(boot)
  setContentView(root)
 }
 override fun onResume() { super.onResume(); updateStatus() }
 private fun updateStatus() {
  if (::status.isInitialized) status.text =
   "Overlay permission: " + (if (Settings.canDrawOverlays(this)) "Granted" else "Required") +
   "\nLast requested state: " + (if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("running", false)) "Started" else "Stopped")
 }
}
