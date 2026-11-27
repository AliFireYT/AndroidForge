package com.alifire.firefit

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("firefit", MODE_PRIVATE) }
    private val selected = mutableSetOf<String>()

    private val bg = Color.rgb(15, 10, 7)
    private val surface = Color.rgb(28, 18, 11)
    private val surface2 = Color.rgb(36, 23, 8)
    private val text = Color.rgb(245, 235, 224)
    private val muted = Color.rgb(183, 162, 144)
    private val ember = Color.rgb(255, 90, 31)
    private val gold = Color.rgb(255, 178, 56)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        selected.addAll(prefs.getStringSet("selected", emptySet()) ?: emptySet())

        val blocked = intent.getStringExtra("blocked_package")
        if (blocked != null && System.currentTimeMillis() >= prefs.getLong("until", 0L)) {
            prefs.edit().putString("pending_package", blocked).apply()
            startActivity(Intent(this, PlankActivity::class.java))
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (selected.isNotEmpty()) render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(textView("AliFire  /  FITNESS", 12f, gold, true).apply {
            letterSpacing = .12f
            gravity = Gravity.CENTER_VERTICAL
        }, matchWrap())
        root.addView(textView("FIREFIT", 36f, text, true).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            letterSpacing = .035f
            setPadding(0, dp(5), 0, 0)
        }, matchWrap())
        root.addView(textView("Earn your screen time.", 16f, muted, false).apply {
            setPadding(0, 0, 0, dp(18))
        }, matchWrap())

        val hero = card()
        hero.addView(textView("۳۰ ثانیه پلانک", 25f, gold, true))
        hero.addView(textView("۱۰ دقیقه دسترسی آزاد به برنامه‌های انتخاب‌شده", 14f, muted, false).apply {
            setPadding(0, dp(7), 0, dp(16))
        })
        hero.addView(actionButton("شروع تمرین پلانک", true) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(this, PlankActivity::class.java))
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 7)
            }
        })
        root.addView(hero, matchWrap(dp(16)))

        root.addView(sectionTitle("کنترل برنامه‌ها"), matchWrap(dp(10)))
        val controls = card()
        val accessibilityEnabled = isAccessibilityEnabled()
        controls.addView(statusLine(
            if (accessibilityEnabled) "قفل برنامه‌ها فعاله" else "قفل برنامه‌ها هنوز فعال نشده",
            accessibilityEnabled
        ))
        controls.addView(textView(
            "برای جلوگیری از باز شدن برنامه‌های انتخابی، سرویس دسترس‌پذیری FireFit را روشن کن.",
            13f, muted, false
        ).apply { setPadding(0, dp(8), 0, dp(14)) })
        controls.addView(actionButton("رفتن به تنظیمات دسترس‌پذیری", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        controls.addView(actionButton("انتخاب برنامه‌ها  ·  ${selected.size} انتخاب", false) {
            chooseApps()
        }.apply { setPadding(dp(14), dp(17), dp(14), dp(17)) })
        root.addView(controls, matchWrap())

        val until = prefs.getLong("until", 0L)
        val left = (until - System.currentTimeMillis()).coerceAtLeast(0L)
        val timeText = if (left > 0) "زمان باقی‌مانده: ${(left / 60000L).coerceAtLeast(1)} دقیقه" else "آماده برای تمرین بعدی"
        val footer = textView(timeText, 13f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }
        root.addView(footer, matchWrap())
    }

    private fun chooseApps() {
        val apps = packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName && packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
            background = rounded(surface, 20, Color.rgb(83, 49, 27), 1)
        }
        panel.addView(textView("برنامه‌های قفل‌شونده", 20f, text, true))
        panel.addView(textView("برنامه‌هایی را که می‌خواهی با پلانک باز شوند انتخاب کن.", 13f, muted, false).apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        apps.forEach { app ->
            val cb = CheckBox(this).apply {
                text = packageManager.getApplicationLabel(app).toString()
                textSize = 15f
                setTextColor(this@MainActivity.text)
                buttonTintList = android.content.res.ColorStateList.valueOf(ember)
                isChecked = selected.contains(app.packageName)
                minHeight = dp(54)
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
                }
            }
            list.addView(cb, matchWrap())
        }
        val listScroll = ScrollView(this).apply {
            addView(list)
            isFillViewport = false
        }
        panel.addView(listScroll, LinearLayout.LayoutParams(-1, dp(360)))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        buttons.addView(actionButton("لغو", false) { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            marginEnd = dp(6)
        })
        buttons.addView(actionButton("ذخیره", true) {
            prefs.edit().putStringSet("selected", selected.toSet()).apply()
            dialog.dismiss()
            render()
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(6) })
        panel.addView(buttons)
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels - dp(28)), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels - dp(28)), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals("$packageName/.AppGuardService", true) || it.contains("$packageName/com.alifire.firefit.AppGuardService", true) }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(surface, 16, Color.rgb(89, 48, 25), 1)
    }

    private fun sectionTitle(value: String) = textView(value, 18f, text, true).apply {
        setPadding(dp(2), dp(6), 0, dp(4))
    }

    private fun statusLine(value: String, enabled: Boolean) = textView(
        (if (enabled) "●  " else "○  ") + value,
        15f, if (enabled) gold else ember, true
    )

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit): TextView {
        val fill = if (primary) ember else surface2
        val stroke = if (primary) ember else Color.rgb(111, 66, 34)
        return TextView(this).apply {
            text = label
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) Color.rgb(25, 12, 5) else this@MainActivity.text)
            gravity = Gravity.CENTER
            minHeight = dp(60)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(fill, 12, stroke, 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun textView(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = true
    }

    private fun rounded(color: Int, radius: Int, stroke: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(strokeWidth), stroke)
        }

    private fun matchWrap(bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (bottom > 0) bottomMargin = dp(bottom)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
