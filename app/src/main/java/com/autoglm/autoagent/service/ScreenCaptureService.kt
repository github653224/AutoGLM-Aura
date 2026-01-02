package com.autoglm.autoagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Data class to hold screenshot with dimensions
data class ScreenshotData(
    val base64: String,
    val width: Int,
    val height: Int
)

class ScreenCaptureService : Service() {

    companion object {
        var instance: ScreenCaptureService? = null
        var resultCode: Int = 0
        var resultData: Intent? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 400

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(1, createNotification())
        
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        // Initialize readers/projection immediately if data available
        if (resultData != null) {
            setupMediaProjection()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (resultData != null && mediaProjection == null) {
            setupMediaProjection()
        }
        return START_STICKY
    }

    private fun setupMediaProjection() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData!!)
        
        // Use a persistent ImageReader, capturing full resolution to ensure text clarity before downscaling
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 1)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
    }

    /**
     * Captures a single frame on demand.
     * Prioritizes AccessibilityService.takeScreenshot if available (API 30+).
     * @return ScreenshotData containing base64 image and dimensions, or null on failure
     */
    suspend fun captureSnapshot(): ScreenshotData? {
        Log.d("ScreenCapture", "📸 captureSnapshot() called")
        
        // 尝试使用无障碍服务截图 (无需额外权限)
        val accessibilityService = AutoAgentService.instance
        Log.d("ScreenCapture", "AutoAgentService.instance = $accessibilityService, API = ${Build.VERSION.SDK_INT}")
        
        if (accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d("ScreenCapture", "Trying AccessibilityService screenshot...")
            val bitmap = accessibilityService.takeScreenshotAsync()
            if (bitmap != null) {
                Log.d("ScreenCapture", "✅ Got bitmap from AccessibilityService: ${bitmap.width}x${bitmap.height}")
                val base64 = bitmapToBase64(bitmap)
                val result = ScreenshotData(base64, bitmap.width, bitmap.height)
                bitmap.recycle()
                return result
            } else {
                Log.w("ScreenCapture", "⚠️ AccessibilityService.takeScreenshotAsync() returned null")
            }
        } else {
            Log.w("ScreenCapture", "⚠️ Cannot use AccessibilityService (instance=$accessibilityService, API=${Build.VERSION.SDK_INT})")
        }

        // 回退到 MediaProjection (需要录屏权限)
        Log.d("ScreenCapture", "Falling back to MediaProjection...")
        return captureViaMediaProjection()
    }

    private suspend fun captureViaMediaProjection(): ScreenshotData? = suspendCancellableCoroutine { cont ->
        if (imageReader == null) {
            if (resultData != null) setupMediaProjection()
            else {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }

        try {
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                 Handler(Looper.getMainLooper()).postDelayed({
                     try {
                         val retryImg = imageReader?.acquireLatestImage()
                         processImage(retryImg, cont)
                     } catch (e: Exception) {
                         Log.e("ScreenCapture", "Error acquiring retry image", e)
                         cont.resume(null)
                     }
                 }, 100)
            } else {
                processImage(image, cont)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cont.resume(null)
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        // 注释掉压缩以测试原始质量
        // bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    
    // Helper to process image to Base64 in background
    private fun processImage(image: android.media.Image?, cont: kotlin.coroutines.Continuation<ScreenshotData?>) {
        if (image == null) {
            cont.resume(null)
            return
        }
        
        CoroutineScope(Dispatchers.Default).launch {
            var finalBitmap: Bitmap? = null
            var rawBitmap: Bitmap? = null
            var imageWidth = 0
            var imageHeight = 0
            
            try {
                // Extract all needed data from Image first
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width
                imageWidth = image.width
                imageHeight = image.height

                // Create bitmap from buffer (this is the raw copy)
                rawBitmap = Bitmap.createBitmap(
                    imageWidth + rowPadding / pixelStride,
                    imageHeight, Bitmap.Config.ARGB_8888
                )
                rawBitmap.copyPixelsFromBuffer(buffer)
                
                // CRITICAL: Close image immediately after copying buffer
                // This releases the HardwareBuffer and prevents GPU memory leak
                image.close()

                // Remove padding (stride mismatch)
                finalBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, imageWidth, imageHeight)
                
                // Raw bitmap is no longer needed after cropping
                if (rawBitmap != finalBitmap) {
                    rawBitmap.recycle()
                    rawBitmap = null  // Clear reference
                }

                val stream = ByteArrayOutputStream()
                // 注释掉压缩以测试原始质量
                // Quality 75: High quality JPEG, reduced size
                // finalBitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                // Return screenshot with actual dimensions
                cont.resume(ScreenshotData(base64, imageWidth, imageHeight))
            } catch(e: Exception) {
                Log.e("ScreenCapture", "Error processing image", e)
                // Make sure image is closed even on exception
                try { image.close() } catch (ignored: Exception) {}
                cont.resume(null)
            } finally {
                // Clean up bitmaps
                finalBitmap?.recycle()
                rawBitmap?.recycle()
            }
        }
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ScreenCap", "Screen Capture", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "ScreenCap")
            .setContentTitle("AutoGLM Vision Active")
            .setContentText("Ready to capture screen for Agent tasks.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        virtualDisplay?.release()
        imageReader?.close()
        instance = null
    }
}
