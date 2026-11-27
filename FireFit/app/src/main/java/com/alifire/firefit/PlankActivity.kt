package com.alifire.firefit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import kotlin.math.min
import kotlin.math.sqrt

class PlankActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var overlay: PoseOverlayView
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

    @Volatile private var lastGood = 0L
    @Volatile private var plankMillis = 0L
    @Volatile private var completed = false
    @Volatile private var detectorErrorShown = false
    @Volatile private var cameraStarted = false
    @Volatile private var useFrontCamera = true
    private var provider: ProcessCameraProvider? = null
    private var detectorCreated = false

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
        getSharedPreferences("firefit", MODE_PRIVATE).edit()
            .putBoolean("challenge_active", true)
            .apply()
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
        label = makeText("از پهلو در کادر قرار بگیر؛ سر تا مچ پا دیده شود.", 14f, muted, false).apply {
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
            // FIT_CENTER keeps the pose overlay in the same coordinate space as the camera frame.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        overlay = PoseOverlayView(this)
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(8)
            bottomMargin = dp(12)
        })

        val hint = makeText("اسکلت بدن با نقطه و خط نمایش داده می‌شود. برای پلانک، از پهلو و تمام‌قد دیده شو.", 13f, muted, false).apply {
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
            setOnClickListener {
                getSharedPreferences("firefit", MODE_PRIVATE).edit()
                    .remove("pending_package")
                    .putBoolean("challenge_active", false)
                    .apply()
                finish()
            }
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
                useFrontCamera = selector == CameraSelector.DEFAULT_FRONT_CAMERA

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
                    val rotation = proxy.imageInfo.rotationDegrees
                    val frameWidth = if (rotation == 90 || rotation == 270) proxy.height else proxy.width
                    val frameHeight = if (rotation == 90 || rotation == 270) proxy.width else proxy.height
                    try {
                        val input = InputImage.fromMediaImage(media, rotation)
                        detectorCreated = true
                        detector.process(input)
                            .addOnSuccessListener { pose ->
                                overlay.updatePose(pose, frameWidth, frameHeight, useFrontCamera)
                                evaluate(pose)
                            }
                            .addOnFailureListener {
                                if (!detectorErrorShown) {
                                    detectorErrorShown = true
                                    runOnUiThread { label.text = "تحلیل تصویر ناموفق بود؛ نور و فاصله را بررسی کن." }
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } catch (_: Exception) {
                        proxy.close()
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, previewUseCase, analysis)
                runOnUiThread {
                    cameraStatus.text = if (useFrontCamera) "دوربین جلو متصل شد · رهگیری فعال" else "دوربین پشت متصل شد · رهگیری فعال"
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
            cameraStatus.text = "دوربین در دسترس نیست"
            cameraStatus.setTextColor(ember)
            label.text = message
        }
    }

    private fun evaluate(pose: Pose) {
        // A side-view plank only needs the main chain; knee confidence can vary with clothing/occlusion.
        val sides = listOf(
            listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_ANKLE),
            listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_ANKLE)
        )
        val candidate = sides.mapNotNull { ids ->
            val points = ids.map { pose.getPoseLandmark(it) }
            if (points.all { it != null && it.inFrameLikelihood >= .30f }) points.filterNotNull() else null
        }.maxByOrNull { points -> points.map { it.inFrameLikelihood }.average() }

        val now = System.currentTimeMillis()
        val valid = if (candidate != null) {
            val shoulder = candidate[0].position
            val elbow = candidate[1].position
            val wrist = candidate[2].position
            val hip = candidate[3].position
            val ankle = candidate[4].position

            val bodyDx = (ankle.x - shoulder.x).toDouble()
            val bodyDy = (ankle.y - shoulder.y).toDouble()
            val bodyLength = sqrt(bodyDx * bodyDx + bodyDy * bodyDy).coerceAtLeast(1.0)
            val bodyAngle = Math.toDegrees(atan2(abs(bodyDy), abs(bodyDx)))
            val hipDeviation = abs(bodyDx * (hip.y - shoulder.y) - bodyDy * (hip.x - shoulder.x)) / bodyLength
            val elbowAngle = jointAngle(shoulder, elbow, wrist)

            // Wider tolerances account for camera perspective and natural body proportions.
            bodyAngle <= 40.0 &&
                hipDeviation / bodyLength <= .30 &&
                elbowAngle in 45.0..160.0
        } else false

        if (valid) {
            // Accumulate only time supported by valid pose frames. Brief detection dropouts pause,
            // rather than erase, the user's effort. Cap each frame contribution to avoid time jumps.
            if (lastGood > 0L) {
                plankMillis += (now - lastGood).coerceIn(0L, 250L)
            }
            lastGood = now
            val seconds = (plankMillis / 1000L).toInt().coerceAtMost(30)
            runOnUiThread {
                label.text = "فرم پلانک تأیید شد؛ ثابت بمان."
                timer.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
                timer.setTextColor(gold)
            }
            if (seconds >= 30 && !completed) {
                completed = true
                val prefs = getSharedPreferences("firefit", MODE_PRIVATE)
                prefs.edit().putLong("until", now + 10 * 60 * 1000L).apply()
                val pending = prefs.getString("pending_package", intent.getStringExtra("blocked_package"))
                runOnUiThread {
                    label.text = "تمرین کامل شد؛ ۱۰ دقیقه دسترسی گرفتی."
                    cameraStatus.text = "چالش با موفقیت انجام شد"
                    timer.text = "✓ ۰۰:۳۰"
                    timer.setTextColor(ember)
                    preview.postDelayed({
                        if (!pending.isNullOrBlank()) {
                            packageManager.getLaunchIntentForPackage(pending)?.let { startActivity(it) }
                        }
                        prefs.edit()
                            .remove("pending_package")
                            .putBoolean("challenge_active", false)
                            .apply()
                        finish()
                    }, 1400)
                }
            }
        } else {
            // Reset the frame-to-frame clock only; preserve already-earned plank time.
            lastGood = 0L
            runOnUiThread {
                if (!completed) {
                    label.text = if (candidate == null) {
                        "بدن کامل پیدا نشد؛ از پهلو و با نور کافی در کادر قرار بگیر."
                    } else {
                        "فرم پلانک را صاف کن؛ شانه، لگن و مچ پا تقریباً در یک خط باشند."
                    }
                    timer.setTextColor(if (plankMillis > 0L) gold else muted)
                }
            }
        }
    }

    private fun jointAngle(a: android.graphics.PointF, b: android.graphics.PointF, c: android.graphics.PointF): Double {
        val abx = (a.x - b.x).toDouble()
        val aby = (a.y - b.y).toDouble()
        val cbx = (c.x - b.x).toDouble()
        val cby = (c.y - b.y).toDouble()
        val dot = abx * cbx + aby * cby
        val lengths = (sqrt(abx * abx + aby * aby) * sqrt(cbx * cbx + cby * cby)).coerceAtLeast(1.0)
        return Math.toDegrees(kotlin.math.acos((dot / lengths).coerceIn(-1.0, 1.0)))
    }

    override fun onDestroy() {
        provider?.unbindAll()
        executor.shutdown()
        if (detectorCreated) detector.close()
        getSharedPreferences("firefit", MODE_PRIVATE).edit()
            .putBoolean("challenge_active", false)
            .apply()
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

private class PoseOverlayView(context: android.content.Context) : View(context) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 225, 210)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 165, 35)
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    @Volatile private var pose: Pose? = null
    @Volatile private var imageWidth = 1
    @Volatile private var imageHeight = 1
    @Volatile private var mirrored = false

    fun updatePose(value: Pose, width: Int, height: Int, mirror: Boolean) {
        pose = value
        imageWidth = width.coerceAtLeast(1)
        imageHeight = height.coerceAtLeast(1)
        mirrored = mirror
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = pose ?: return
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        fun point(type: Int): android.graphics.PointF? {
            val landmark = current.getPoseLandmark(type) ?: return null
            if (landmark.inFrameLikelihood < .30f) return null
            val x = if (mirrored) imageWidth - landmark.position.x else landmark.position.x
            return android.graphics.PointF(offsetX + x * scale, offsetY + landmark.position.y * scale)
        }

        val bones = arrayOf(
            intArrayOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
            intArrayOf(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
            intArrayOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
            intArrayOf(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            intArrayOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
            intArrayOf(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
            intArrayOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
            intArrayOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
            intArrayOf(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            intArrayOf(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            intArrayOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
            intArrayOf(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        )
        bones.forEach { pair ->
            val a = point(pair[0])
            val b = point(pair[1])
            if (a != null && b != null) canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
        }
        current.allPoseLandmarks.forEach { landmark ->
            if (landmark.inFrameLikelihood >= .30f) {
                val p = point(landmark.landmarkType) ?: return@forEach
                canvas.drawCircle(p.x, p.y, 7f, outlinePaint)
                canvas.drawCircle(p.x, p.y, 4.5f, jointPaint)
            }
        }
    }
}
