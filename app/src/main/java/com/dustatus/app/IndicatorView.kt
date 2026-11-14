package com.dustatus.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Shared rendering for the real overlay and the in-app preview. */
class IndicatorView(context: Context) : LinearLayout(context) {
 private val density = resources.displayMetrics.density
 private fun dp(v: Int) = (v * density + 0.5f).toInt()
 private val down = speedLabel()
 private val up = speedLabel()
 private val separator = TextView(context).apply {
  text = "·"; textSize = 9f; setTextColor(Color.rgb(200, 208, 218))
  includeFontPadding = false; gravity = Gravity.CENTER
 }
 init {
  orientation = HORIZONTAL
  gravity = Gravity.CENTER_VERTICAL
  background = GradientDrawable().apply {
   setColor(Color.argb(217, 20, 25, 35))
   cornerRadius = dp(1000).toFloat()
  }
  setSpeeds("--", "--")
 }
 private fun speedLabel() = TextView(context).apply {
  textSize = 9f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
  includeFontPadding = false; gravity = Gravity.CENTER; maxLines = 1
 }
 private fun colored(arrow: String, color: Int, value: String): SpannableString =
  SpannableString("$arrow $value").apply {
   setSpan(ForegroundColorSpan(color), 0, arrow.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
  }
 fun setSpeeds(rx: String, tx: String) {
  down.text = colored("↓", Color.rgb(80, 235, 125), rx)
  up.text = colored("↑", Color.rgb(255, 105, 105), tx)
 }
 fun applySettings(prefs: android.content.SharedPreferences) {
  val horizontal = prefs.getInt("padding_x", 3).coerceIn(0, 12)
  val vertical = prefs.getInt("padding_y", 1).coerceIn(0, 8)
  val width = prefs.getInt("field_width", 52).coerceIn(44, 90)
  setPadding(dp(horizontal), dp(vertical), dp(horizontal), dp(vertical))
  removeAllViews()
  addView(down, LayoutParams(dp(width), LayoutParams.WRAP_CONTENT))
  addView(separator, LayoutParams(dp(5), LayoutParams.WRAP_CONTENT))
  addView(up, LayoutParams(dp(width), LayoutParams.WRAP_CONTENT))
 }
}
