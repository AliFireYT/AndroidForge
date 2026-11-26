package com.alifire.firefit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("firefit", MODE_PRIVATE) }
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(prefs.getStringSet("selected", emptySet()) ?: emptySet())
        val blocked = intent.getStringExtra("blocked_package")
        if (blocked != null && System.currentTimeMillis() >= prefs.getLong("until", 0L)) {
            prefs.edit().putString("pending_package", blocked).apply()
            startActivity(Intent(this, PlankActivity::class.java))
        }
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22, 32, 22, 24)
            setBackgroundColor(Color.rgb(12, 15, 25))
        }
        root.addView(TextView(this).apply {
            text = "🔥 FIREFIT"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(65, 220, 255))
        }, LinearLayout.LayoutParams(-1, 72))
        root.addView(TextView(this).apply {
            text = "EARN YOUR SCREEN TIME\n۳۰ ثانیه پلانک = ۱۰ دقیقه دسترسی"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 24)
        })
        addButton(root, "فعال‌سازی قفل برنامه‌ها", Color.rgb(37, 75, 105)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton(root, "انتخاب برنامه‌ها", Color.rgb(37, 75, 105)) { chooseApps() }
        addButton(root, "شروع تمرین پلانک", Color.rgb(0, 130, 155)) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(this, PlankActivity::class.java))
            } else requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 7)
        }
        val until = prefs.getLong("until", 0L)
        val left = (until - System.currentTimeMillis()).coerceAtLeast(0L)
        root.addView(TextView(this).apply {
            text = if (left > 0) "زمان آزاد: ${left / 60000} دقیقه" else "آماده تمرین"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 20, 0, 10)
        })
        root.addView(TextView(this).apply {
            text = "برای فعال شدن قفل، سرویس FireFit را در تنظیمات دسترس‌پذیری روشن کن."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        })
        setContentView(root)
    }

    private fun addButton(root: LinearLayout, label: String, color: Int, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setOnClickListener { action() }
        }
        root.addView(b, LinearLayout.LayoutParams(-1, 58).apply { setMargins(0, 8, 0, 8) })
    }

    private fun chooseApps() {
        val apps = packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName && packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 8, 8) }
        val scroll = ScrollView(this).apply { addView(list) }
        apps.forEach { app ->
            val cb = CheckBox(this).apply {
                text = packageManager.getApplicationLabel(app).toString()
                setTextColor(Color.WHITE)
                isChecked = selected.contains(app.packageName)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
                }
            }
            list.addView(cb)
        }
        AlertDialog.Builder(this).setTitle("برنامه‌های قفل‌شونده").setView(scroll)
            .setPositiveButton("ذخیره") { _, _ -> prefs.edit().putStringSet("selected", selected.toSet()).apply() }
            .setNegativeButton("لغو", null).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, PlankActivity::class.java))
        }
    }
}
