package com.dustatus.app

import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
 private var overlay: IndicatorView? = null
 private lateinit var overlayParams: WindowManager.LayoutParams
 private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
 private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
  if (key in setOf("position_x", "position_y", "padding_x", "padding_y", "field_width", "drag_unlocked")) {
   handler.post { overlay?.applySettings(prefs); updateOverlayPosition(); updateDragMode() }
  }
 }
 private fun updateOverlayPosition() {
  if (!::overlayParams.isInitialized) return
  val d = resources.displayMetrics.density
  val bounds = displayBounds()
  val maxX = (bounds.first - (overlay?.width ?: 0)).coerceAtLeast(0)
  val maxY = (bounds.second - (overlay?.height ?: 0)).coerceAtLeast(0)
  overlayParams.x = (prefs.getInt("position_x", 7) * d + 0.5f).toInt().coerceIn(0, maxX)
  overlayParams.y = (prefs.getInt("position_y", 28) * d + 0.5f).toInt().coerceIn(0, maxY)
  overlay?.let { try { wm.updateViewLayout(it, overlayParams) } catch (_: Exception) {} }
 }
 private fun displayBounds(): Pair<Int, Int> {
  val metrics = android.util.DisplayMetrics()
  @Suppress("DEPRECATION")
  wm.defaultDisplay.getRealMetrics(metrics)
  return metrics.widthPixels to metrics.heightPixels
 }
 private fun updateDragMode() {
  if (!::overlayParams.isInitialized) return
  val unlocked = prefs.getBoolean("drag_unlocked", false)
  val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
  overlayParams.flags = base or (if (unlocked) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
  overlay?.let { try { wm.updateViewLayout(it, overlayParams) } catch (_: Exception) {} }
 }
 private var dragStartX = 0f
 private var dragStartY = 0f
 private var dragWindowX = 0
 private var dragWindowY = 0
 private fun installDragHandler(view: IndicatorView) {
  view.setOnTouchListener { _, event ->
   if (!prefs.getBoolean("drag_unlocked", false)) return@setOnTouchListener false
   when (event.actionMasked) {
    MotionEvent.ACTION_DOWN -> {
     dragStartX = event.rawX
     dragStartY = event.rawY
     dragWindowX = overlayParams.x
     dragWindowY = overlayParams.y
     true
    }
    MotionEvent.ACTION_MOVE -> {
     val (screenW, screenH) = displayBounds()
     overlayParams.x = (dragWindowX + (event.rawX - dragStartX).toInt()).coerceIn(0, (screenW - view.width).coerceAtLeast(0))
     overlayParams.y = (dragWindowY + (event.rawY - dragStartY).toInt()).coerceIn(0, (screenH - view.height).coerceAtLeast(0))
     try { wm.updateViewLayout(view, overlayParams) } catch (_: Exception) {}
     true
    }
    MotionEvent.ACTION_UP -> {
     val d = resources.displayMetrics.density
     prefs.edit().putInt("position_x", (overlayParams.x / d + 0.5f).toInt())
      .putInt("position_y", (overlayParams.y / d + 0.5f).toInt()).apply()
     true
    }
    MotionEvent.ACTION_CANCEL -> true
    else -> false
   }
  }
 }
 private var lastRx = 0L
 private var lastTx = 0L
 private var lastTime = 0L
 private var polling = false
 private var receiverRegistered = false
 private var lastDownText = "--"
 private var lastUpText = "--"
 private val screenReceiver = object : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
   when (intent?.action) {
    Intent.ACTION_SCREEN_OFF -> pauseSampling()
    Intent.ACTION_SCREEN_ON -> resumeSampling()
   }
  }
 }
 private fun pauseSampling() {
  polling = false
  handler.removeCallbacks(tick)
  lastTime = 0L
 }
 private fun resumeSampling() {
  if (polling || overlay == null) return
  polling = true
  lastTime = 0L
  handler.removeCallbacks(tick)
  handler.post(tick)
 }
 private fun updateValue(value: String, download: Boolean) {
  if (download) {
   if (lastDownText == value) return
   lastDownText = value
  } else {
   if (lastUpText == value) return
   lastUpText = value
  }
  overlay?.setSpeeds(lastDownText, lastUpText)
 }
 private val tick = object : Runnable {
  override fun run() {
   val now = SystemClock.elapsedRealtime()
   val rx = TrafficStats.getTotalRxBytes()
   val tx = TrafficStats.getTotalTxBytes()
   val elapsed = now - lastTime
   if (lastTime > 0 && elapsed in 500L..3000L && rx >= lastRx && tx >= lastTx && rx >= 0 && tx >= 0) {
    val seconds = elapsed / 1000.0
    updateValue(speed(((rx-lastRx).coerceAtLeast(0))/seconds), true)
    updateValue(speed(((tx-lastTx).coerceAtLeast(0))/seconds), false)
   }
   else if (lastTime > 0) { updateValue("--", true); updateValue("--", false) }
   lastRx = rx; lastTx = tx; lastTime = now
   if (polling) handler.postDelayed(this, 1000)
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
  overlay = IndicatorView(this).apply { applySettings(prefs); installDragHandler(this) }
  overlayParams = WindowManager.LayoutParams(
   WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
   PixelFormat.TRANSLUCENT
  ).apply { gravity = Gravity.TOP or Gravity.START; x = (prefs.getInt("position_x", 7) * resources.displayMetrics.density + 0.5f).toInt(); y = (prefs.getInt("position_y", 28) * resources.displayMetrics.density + 0.5f).toInt() }
  try {
   wm.addView(overlay, overlayParams)
   updateDragMode()
   prefs.registerOnSharedPreferenceChangeListener(settingsListener)
   registerReceiver(screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
   receiverRegistered = true
   getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("running", true).apply()
   val power = getSystemService(POWER_SERVICE) as PowerManager
   if (power.isInteractive) resumeSampling()
  } catch (e: Exception) {
   getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("running", false).apply()
   stopSelf()
  }
 }
 override fun onDestroy() {
  pauseSampling()
  prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
  if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
  overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
  overlay = null
  super.onDestroy()
 }
}
