package com.alifire.firefit

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
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
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class PlankActivity : Activity() {
    private lateinit var preview: PreviewView
    private lateinit var label: TextView
    private lateinit var timer: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val detector by lazy {
        PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    }
    @Volatile private var startedAt = 0L
    @Volatile private var lastGood = 0L
    @Volatile private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(10, 14, 24)) }
        label = TextView(this).apply {
            text = "از横 پهلو در کادر دوربین قرار بگیر؛ کل بدن باید دیده شود."
            setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER; setPadding(12, 14, 12, 10)
        }
        timer = TextView(this).apply {
            text = "۰۰:۰۰ / ۰۰:۳۰"; setTextColor(Color.CYAN); textSize = 32f; gravity = Gravity.CENTER; setPadding(8, 12, 8, 12)
        }
        preview = PreviewView(this)
        root.addView(label, LinearLayout.LayoutParams(-1, 94))
        root.addView(timer, LinearLayout.LayoutParams(-1, 72))
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else startCamera()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val previewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || completed) { proxy.close(); return@setAnalyzer }
                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                detector.process(input)
                    .addOnSuccessListener { pose -> evaluate(pose) }
                    .addOnCompleteListener { proxy.close() }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, previewUseCase, analysis)
            } catch (_: Exception) { runOnUiThread { label.text = "دوربین جلو در دسترس نیست." } }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun evaluate(pose: Pose) {
        val shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) ?: pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val now = System.currentTimeMillis()
        val valid = if (shoulder != null && hip != null && ankle != null &&
            shoulder.inFrameLikelihood > .55f && hip.inFrameLikelihood > .55f && ankle.inFrameLikelihood > .55f) {
            val dx = (ankle.position.x - shoulder.position.x).toDouble()
            val dy = (ankle.position.y - shoulder.position.y).toDouble()
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
            val slope = Math.toDegrees(atan2(abs(dy), abs(dx)))
            val hipDeviation = abs(dx * (hip.position.y - shoulder.position.y) - dy * (hip.position.x - shoulder.position.x)) / length
            slope < 25.0 && hipDeviation / length < .20
        } else false

        if (valid) {
            if (startedAt == 0L || now - lastGood > 1000L) startedAt = now
            lastGood = now
            val seconds = ((now - startedAt) / 1000L).toInt()
            runOnUiThread {
                label.text = "فرم مناسب است؛ ثابت بمان."
                timer.text = String.format("%02d:%02d / 00:30", seconds / 60, seconds % 60)
            }
            if (seconds >= 30 && !completed) {
                completed = true
                val prefs = getSharedPreferences("firefit", MODE_PRIVATE)
                prefs.edit().putLong("until", now + 10 * 60 * 1000L).apply()
                val pending = prefs.getString("pending_package", null)
                runOnUiThread {
                    label.text = "تمرین کامل شد؛ ۱۰ دقیقه دسترسی گرفتی."
                    timer.text = "✓ ۰۰:۳۰"
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
                if (!completed) { label.text = "بدن را از横 پهلو صاف نگه دار و کل بدن را در کادر بگذار."; timer.text = "۰۰:۰۰ / ۰۰:۳۰" }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        detector.close()
    }
}
