package com.dustatus.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 56, 40, 24) }
  val title = TextView(this).apply { text = "DUstatus · v0.1\nDownload / Upload overlay\n\nAndroid does not allow normal overlays inside the protected status bar. The indicator appears just below it."; textSize = 18f }
  val start = Button(this).apply { text = "Grant overlay permission / Start"; setOnClickListener {
   if (!Settings.canDrawOverlays(this@MainActivity)) {
    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
   } else {
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
     requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 12)
    }
    startForegroundService(Intent(this@MainActivity, SpeedService::class.java))
   }
  } }
  val stop = Button(this).apply { text = "Stop"; setOnClickListener { stopService(Intent(this@MainActivity, SpeedService::class.java)) } }
  root.addView(title); root.addView(start); root.addView(stop); setContentView(root)
 }
}
