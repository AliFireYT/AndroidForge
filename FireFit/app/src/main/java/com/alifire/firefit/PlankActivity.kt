package com.alifire.firefit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class PlankActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var label: TextView
    private lateinit var timer: TextView
    private lateinit var cameraStatus: TextView
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detector by lazy {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
        )
    }

    @Volatile private var startedAt = 0L
    @Volatile private var lastGood = 0L
    @Volatile private var completed = false
    @Volatile private var cameraReady = false
    @Volatile private var detectorErrorShown = false
    private var provider: ProcessCameraProvider? = null
    private var cameraStarted = false

    private val bg = Color.rgb(15, 10, 7)
    private val surface = Color.rgb(28, 18, 11)
    private val text = Color.rgb(245, 235, 224)
    private val muted = Color.rgb(183, 162, 144)
    private val ember = Color.rgb(255, 90, 31)
    private val gold = Color.rgb(255, 178, 56)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            setBackgroundColor(bg)
        }
        root.addView(makeText("FIREFIT  /  CHALLENGE", 12f, gold, true).apply {
            letterSpacing = .12f
        }, LinearLayout.LayoutParams(-1, dp(28)))
        root.addView(makeText("پلانک را نگه دار", 24f, text, true), LinearLayout.LayoutParams(-1, dp(42)))
        label = makeText("از پهلو روبه‌روی دوربین قرار بگیر؛ سر، لگن و مچ پا باید دیده شوند.", 14f, muted, false).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(8))
        }
        root.addView(label, LinearLayout.LayoutParams(-1, dp(58)))
        timer = makeText("۰۰:۰۰", 42f, gold, true).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        root.addView(timer, LinearLayout.LayoutParams(-1, dp(74)))
        cameraStatus = makeText("در حال آماده‌سازی دوربین…", 12f, muted, false).apply {
            gravity = Gravity.CENTER
        }
        root.addView(cameraStatus, LinearLayout.LayoutParams(-1, dp(30)))

        val frame = FrameLayout(this).apply {
            background = rounded(surface, 18, Color.rgb(89, 48, 25), 1)
            clipToOutline = true
        }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(8)
            bottomMargin = dp(12)
        })

        val hint = makeText("بدن باید تقریباً صاف باشد. اگر فرم به‌هم بخورد، شمارش از صفر شروع می‌شود.", 13f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(surface, 12, Color.rgb(65, 40, 24), 1)
        }
        root.addView(hint, LinearLayout.LayoutParams(-1, dp(58)))
        val cancel = makeText("انصراف", 15f, text, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(36, 23, 8), 12, Color.rgb(111, 66, 34), 1)
            minHeight = dp(56)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        root.addView(cancel, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(12) })
        setContentView(root)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else if (requestCode == 1) {
            label.text = "دسترسی دوربین رد شده؛ از تنظیمات برنامه آن را فعال کن."
            cameraStatus.text = "دوربین اجازهٔ دسترسی ندارد"
            cameraStatus.setTextColor(ember)
        }
    }

    private fun startCamera() {
        if (cameraStarted || isFinishing) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraStatus.text = "مجوز دوربین فعال نیست"
            return
        }
        cameraStarted = true
        cameraStatus.text = "در حال اتصال به دوربین…"
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> {
                        showCameraError("هیچ دوربین سازگاری پیدا نشد.")
                        return@addListener
                    }
                }

                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || completed) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    try {
                        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        detector.process(input)
                            .addOnSuccessListener { pose -> evaluate(pose) }
                            .addOnFailureListener {
                                if (!detectorErrorShown) {
                                    detectorErrorShown = true
                                    runOnUiThread {
                                        label.text = "تعذر تحليل الصورة؛ دوربین را دوباره باز کن."
                                    }
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } catch (_: Exception) {
                        proxy.close()
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, previewUseCase, analysis)
                cameraReady = true
                runOnUiThread {
                    cameraStatus.text = if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) "دوربین جلو متصل شد" else "دوربین پشت متصل شد"
                    cameraStatus.setTextColor(gold)
                    label.text = "از پهلو در کادر قرار بگیر؛ کل بدن باید دیده شود."
                }
            } catch (error: Exception) {
                cameraStarted = false
                val detail = error.localizedMessage?.take(90)
                showCameraError(if (detail.isNullOrBlank()) "اتصال دوربین ناموفق بود؛ برنامه‌های دیگرِ استفاده‌کننده از دوربین را ببند." else "اتصال دوربین ناموفق بود: $detail")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showCameraError(message: String) {
        runOnUiThread {
            cameraReady = false
            cameraStatus.text = "دوربین در دسترس نیست"
            cameraStatus.setTextColor(ember)
            label.text = message
        }
    }

    private fun evaluate(pose: Pose) {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val side = if (leftShoulder != null && leftHip != null && leftAnkle != null) {
            Triple(leftShoulder, leftHip, leftAnkle)
        } else if (rightShoulder != null && rightHip != null && rightAnkle != null) {
            Triple(rightShoulder, rightHip, rightAnkle)
        } else null

        val now = System.currentTimeMillis()
        val valid = if (side != null &&
            side.first.inFrameLikelihood > .55f &&
            side.second.inFrameLikelihood > .55f &&
            side.third.inFrameLikelihood > .55f) {
            val shoulder = side.first.position
            val hip = side.second.position
            val ankle = side.third.position
            val dx = (ankle.x - shoulder.x).toDouble()
            val dy = (ankle.y - shoulder.y).toDouble()
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
            val bodyAngle = Math.toDegrees(atan2(abs(dy), abs(dx)))
            val hipOffset = abs(dx * (hip.y - shoulder.y) - dy * (hip.x - shoulder.x)) / length
            bodyAngle < 25.0 && hipOffset / length < .23
        } else false

        if (valid) {
            if (startedAt == 0L || now - lastGood > 1200L) startedAt = now
            lastGood = now
            val seconds = ((now - startedAt) / 1000L).toInt().coerceAtMost(30)
            runOnUiThread {
                label.text = "فرم مناسبه؛ ثابت بمون."
                timer.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
                timer.setTextColor(gold)
            }
            if (seconds >= 30 && !completed) {
                completed = true
                val prefs = getSharedPreferences("firefit", MODE_PRIVATE)
                prefs.edit().putLong("until", now + 10 * 60 * 1000L).apply()
                val pending = prefs.getString("pending_package", null)
                runOnUiThread {
                    label.text = "تمرین کامل شد؛ ۱۰ دقیقه دسترسی گرفتی."
                    cameraStatus.text = "چالش با موفقیت انجام شد"
                    timer.text = "✓ ۰۰:۳۰"
                    timer.setTextColor(ember)
                    preview.postDelayed({
                        if (!pending.isNullOrBlank()) {
                            packageManager.getLaunchIntentForPackage(pending)?.let { startActivity(it) }
                            prefs.edit().remove("pending_package").apply()
                        }
                        finish()
                    }, 1400)
                }
            }
        } else {
            startedAt = 0L
            runOnUiThread {
                if (!completed) {
                    label.text = if (side == null) "بدن کامل دیده نمی‌شود؛ کمی از دوربین فاصله بگیر." else "بدن را صاف نگه دار و کل بدن را داخل کادر بگذار."
                    timer.text = "۰۰:۰۰"
                    timer.setTextColor(muted)
                }
            }
        }
    }

    override fun onDestroy() {
        provider?.unbindAll()
        executor.shutdown()
        if (::preview.isInitialized) detector.close()
        super.onDestroy()
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
