package com.dustatus.app

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.util.Locale

class SpeedService : Service() {
 companion object { const val ACTION_STOP = "com.dustatus.app.STOP" }
 private val handler = Handler(Looper.getMainLooper())
 private lateinit var wm: WindowManager
 private var overlay: LinearLayout? = null
 private var down: TextView? = null
 private var up: TextView? = null
 private var lastRx = 0L
 private var lastTx = 0L
 private var lastTime = 0L
 private val tick = object : Runnable {
  override fun run() {
   val now = SystemClock.elapsedRealtime()
   val rx = TrafficStats.getTotalRxBytes()
   val tx = TrafficStats.getTotalTxBytes()
   val elapsed = now - lastTime
   if (lastTime > 0 && elapsed in 500..3000 && rx >= lastRx && tx >= lastTx && rx >= 0 && tx >= 0) {
    val seconds = elapsed / 1000.0
    down?.text = coloredSpeed("↓", Color.rgb(80, 235, 125), speed(((rx-lastRx).coerceAtLeast(0))/seconds))
    up?.text = coloredSpeed("↑", Color.rgb(255, 105, 105), speed(((tx-lastTx).coerceAtLeast(0))/seconds))
   }
   else if (lastTime > 0) { down?.text = coloredSpeed("↓", Color.rgb(80, 235, 125), "--"); up?.text = coloredSpeed("↑", Color.rgb(255, 105, 105), "--") }
   lastRx = rx; lastTx = tx; lastTime = now
   handler.postDelayed(this, 1000)
  }
 }
 private fun coloredSpeed(arrow: String, color: Int, value: String): SpannableString =
  SpannableString("$arrow $value").apply {
   setSpan(ForegroundColorSpan(color), 0, arrow.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
  }
 private fun speed(b: Double): String = when {
  b >= 1048576 -> String.format(Locale.US, "%.1f MB/s", b/1048576)
  b >= 1024 -> String.format(Locale.US, "%.0f KB/s", b/1024)
  else -> String.format(Locale.US, "%.0f B/s", b)
 }
 override fun onBind(intent: Intent?) = null
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if (intent?.action == ACTION_STOP) {
   getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("running", false).apply()
   stopSelf()
   return START_NOT_STICKY
  }
  return START_STICKY
 }
 override fun onCreate() {
  super.onCreate()
  val channel = "speed"
  val nm = getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(channel, "Network speed", NotificationManager.IMPORTANCE_LOW))
  val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
  val stopIntent = Intent(this, SpeedService::class.java).apply { action = ACTION_STOP }
  val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val notification = Notification.Builder(this, channel).addAction(android.R.drawable.ic_media_pause, "Stop", stopPending).setContentTitle("DUstatus active").setContentText("Network speed indicator running").setSmallIcon(android.R.drawable.stat_sys_download).setContentIntent(pending).build()
  startForeground(1, notification)
  if (!Settings.canDrawOverlays(this)) { getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("running", false).apply(); stopSelf(); return }
  wm = getSystemService(WINDOW_SERVICE) as WindowManager
  val density = resources.displayMetrics.density
  fun line(color: Int) = TextView(this).apply {
   setTextColor(color); textSize = 9f; typeface = Typeface.DEFAULT_BOLD
   includeFontPadding = false
  }
  down = line(Color.WHITE).apply { text = coloredSpeed("↓", Color.rgb(76, 217, 100), "--") }
  up = line(Color.WHITE).apply { text = coloredSpeed("↑", Color.rgb(255, 82, 82), "--") }
  overlay = LinearLayout(this).apply {
   orientation = LinearLayout.HORIZONTAL
   setPadding((3*density).toInt(), (1*density).toInt(), (3*density).toInt(), (1*density).toInt())
   background = GradientDrawable().apply {
    setColor(Color.argb(217, 20, 25, 35)) // 85% opaque charcoal, readable on light/dark backgrounds
    cornerRadius = 1000f * density // fully rounded pill ends
   }
   addView(down); addView(TextView(this@SpeedService).apply { text = "  ·  "; textSize = 9f; setTextColor(Color.rgb(200, 208, 218)); includeFontPadding = false }); addView(up)
  }
  val params = WindowManager.LayoutParams(
   WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
   PixelFormat.TRANSLUCENT
  ).apply { gravity = Gravity.TOP or Gravity.START; x = (40*density).toInt(); y = (28*density).toInt() }
  try { wm.addView(overlay, params); getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("running", true).apply(); handler.post(tick) } catch (e: Exception) { getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("running", false).apply(); stopSelf() }
 }
 override fun onDestroy() {
  handler.removeCallbacks(tick)
  overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
  overlay = null
  super.onDestroy()
 }
}
