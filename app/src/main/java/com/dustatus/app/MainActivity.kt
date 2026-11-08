package com.dustatus.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
 private val ink = Color.rgb(232, 238, 248)
 private val muted = Color.rgb(157, 170, 192)
 private val panel = Color.rgb(30, 41, 61)
 private val green = Color.rgb(80, 235, 125)
 private lateinit var status: TextView
 private lateinit var permission: TextView
 private lateinit var start: Button
 private lateinit var stop: Button

 private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
 private fun shape(color: Int, radius: Int = 18) = GradientDrawable().apply {
  setColor(color); cornerRadius = dp(radius).toFloat()
 }
 private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
  TextView(this).apply {
   this.text = value; textSize = size; setTextColor(color)
   if (bold) typeface = Typeface.DEFAULT_BOLD
   includeFontPadding = false
  }
 private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
 private fun addSpace(parent: LinearLayout, height: Int) {
  parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
 }
 private fun card(root: LinearLayout, content: LinearLayout) {
  content.background = shape(panel)
  content.setPadding(dp(18), dp(18), dp(18), dp(18))
  root.addView(content, LinearLayout.LayoutParams(-1, -2))
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = Color.rgb(13, 21, 36)
  window.navigationBarColor = Color.rgb(13, 21, 36)
  window.decorView.systemUiVisibility = 0
  if (Build.VERSION.SDK_INT >= 35) {
   window.decorView.setOnApplyWindowInsetsListener { view, insets ->
    val bars = insets.getInsets(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
    view.setPadding(0, bars.top, 0, bars.bottom)
    insets
   }
  }
  val prefs = getSharedPreferences("settings", MODE_PRIVATE)
  val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(13, 21, 36)); fillViewport = true }
  val root = column().apply { setPadding(dp(20), dp(26), dp(20), dp(30)) }
  scroll.addView(root)
  root.addView(text("DUstatus", 30f, ink, true))
  addSpace(root, 5)
  root.addView(text("Network speed indicator  •  v0.3", 13f, muted))
  addSpace(root, 26)

  val preview = column()
  preview.addView(text("LIVE INDICATOR", 11f, muted, true))
  addSpace(preview, 14)
  val capsule = text("↓  72 KB/s    ·    ↑  2 KB/s", 13f, Color.WHITE, true).apply {
   background = shape(Color.rgb(217, 20, 25, 35), 100)
   setPadding(dp(12), dp(7), dp(12), dp(7))
  }
  preview.addView(capsule, LinearLayout.LayoutParams(-2, -2))
  addSpace(preview, 10)
  preview.addView(text("Preview only · Actual indicator stays below the clock", 12f, muted))
  card(root, preview)
  addSpace(root, 16)

  val stateCard = column()
  stateCard.addView(text("SERVICE STATUS", 11f, muted, true))
  addSpace(stateCard, 12)
  status = text("Checking…", 18f, green, true)
  stateCard.addView(status)
  addSpace(stateCard, 8)
  permission = text("", 13f, muted)
  stateCard.addView(permission)
  card(root, stateCard)
  addSpace(root, 18)

  start = Button(this).apply {
   text = "START INDICATOR"
   isAllCaps = false
   setTextColor(Color.rgb(9, 26, 18))
   background = shape(green, 14)
   setOnClickListener {
    if (!Settings.canDrawOverlays(this@MainActivity)) {
     startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    } else {
     if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
      requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 12)
     try {
      startForegroundService(Intent(this@MainActivity, SpeedService::class.java))
      status.postDelayed({ updateStatus() }, 350)
     } catch (e: Exception) { status.text = "Start failed: ${e.javaClass.simpleName}" }
    }
   }
  }
  root.addView(start, LinearLayout.LayoutParams(-1, dp(52)))
  addSpace(root, 10)
  stop = Button(this).apply {
   text = "STOP INDICATOR"; isAllCaps = false; setTextColor(ink)
   background = shape(panel, 14)
   setOnClickListener {
    prefs.edit().putBoolean("running", false).apply()
    stopService(Intent(this@MainActivity, SpeedService::class.java))
    updateStatus()
   }
  }
  root.addView(stop, LinearLayout.LayoutParams(-1, dp(52)))
  addSpace(root, 22)

  val options = column()
  options.addView(text("STARTUP", 11f, muted, true))
  addSpace(options, 10)
  val boot = CheckBox(this).apply {
   text = "Start automatically after reboot"
   setTextColor(ink); textSize = 14f
   buttonTintList = android.content.res.ColorStateList.valueOf(green)
   isChecked = prefs.getBoolean("auto_boot", false)
   setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("auto_boot", checked).apply() }
  }
  options.addView(boot)
  addSpace(options, 6)
  options.addView(text("Keeps the indicator available after restarting your phone.", 12f, muted))
  card(root, options)
  addSpace(root, 18)
  root.addView(text("Battery optimized · Polling pauses when the screen is off", 12f, muted))
  setContentView(scroll)
  updateStatus()
 }
 override fun onResume() { super.onResume(); if (::status.isInitialized) updateStatus() }
 private fun updateStatus() {
  val allowed = Settings.canDrawOverlays(this)
  val requested = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("running", false)
  status.text = if (requested && allowed) "●  Indicator enabled" else "●  Indicator stopped"
  status.setTextColor(if (requested && allowed) green else muted)
  permission.text = if (allowed) "Overlay permission granted" else "Overlay permission required"
  start.text = if (allowed) "START INDICATOR" else "GRANT OVERLAY PERMISSION"
 }
}
