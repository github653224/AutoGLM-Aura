package com.autoglm.autoagent.shell

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.Keep
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.Method

/**
 * AutoDroid User Service
 * Implementation of IAutoDroidShell running in a privileged process via Shizuku.
 * 
 * Modified to fix:
 * 1. Screenshot permissions (using Shizuku.newProcess)
 * 2. Background App Launch (using PendingIntent)
 * 3. Context management (Use App Context to avoid SecurityException)
 */
@Keep
class AutoDroidUserService(private val context: Context) : IAutoDroidShell.Stub() {
    private val TAG = "AutoDroidUserService"
    
    // Cached reflection
    private var inputManager: Any? = null
    private var injectInputEventMethod: Method? = null
    
    // Virtual Display Management
    private val displayMap = mutableMapOf<Int, android.hardware.display.VirtualDisplay>()
    private val imageReaders = mutableMapOf<Int, android.media.ImageReader>()
    
    init {
        Log.d(TAG, "🚀 AutoDroidUserService initialized with App Context")
        initInputManager()
    }

    private fun initInputManager(): Boolean {
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "input") as IBinder
            val wrapper = ShizukuBinderWrapper(binder)
            val iInputManagerStubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = iInputManagerStubClass.getMethod("asInterface", IBinder::class.java)
            inputManager = asInterfaceMethod.invoke(null, wrapper)
            
            val inputManagerClass = inputManager!!.javaClass
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    injectInputEventMethod = inputManagerClass.getMethod(
                        "injectInputEventToTarget",
                        android.view.InputEvent::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                } catch (e: NoSuchMethodException) {}
            }
            if (injectInputEventMethod == null) {
                injectInputEventMethod = inputManagerClass.getMethod(
                    "injectInputEvent",
                    android.view.InputEvent::class.java,
                    Int::class.javaPrimitiveType
                )
            }
            injectInputEventMethod?.isAccessible = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init InputManager", e)
            return false
        }
    }

    override fun ping(): Boolean = true

    override fun injectTouch(displayId: Int, action: Int, x: Int, y: Int): Boolean {
        if (inputManager == null) if (!initInputManager()) return false
        return try {
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, action, x.toFloat(), y.toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                // Ensure displayId is set for multi-display support
                val setDisplayIdMethod = MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
                setDisplayIdMethod.invoke(event, displayId)
            } catch (e: Exception) {}
            val success = performInjection(event)
            event.recycle()
            success
        } catch (e: Exception) {
            Log.e(TAG, "injectTouch failed", e)
            false
        }
    }

    override fun injectKey(keyCode: Int): Boolean {
        if (inputManager == null) if (!initInputManager()) return false
        return try {
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
            val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
            performInjection(down) && performInjection(up)
        } catch (e: Exception) {
            Log.e(TAG, "injectKey failed", e)
            false
        }
    }

    private fun performInjection(event: android.view.InputEvent): Boolean {
        val method = injectInputEventMethod ?: return false
        return try {
            if (method.parameterCount == 3) {
                // injectInputEventToTarget(event, mode, uid)
                // uid = -1 (Process.INVALID_UID) to skip permission checks if caller has INJECT_EVENTS
                method.invoke(inputManager, event, 2, -1) as Boolean
            } else {
                // injectInputEvent(event, mode)
                method.invoke(inputManager, event, 2) as Boolean
            }
        } catch (e: Exception) {
            Log.e(TAG, "performInjection failed", e)
            false
        }
    }

    override fun captureScreen(displayId: Int): ByteArray? {
        // 1. 尝试从 ImageReader 高速获取 (Fast Path for Virtual Displays)
        val reader = imageReaders[displayId]
        if (reader != null) {
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * reader.width
                    
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        reader.width + rowPadding / pixelStride,
                        reader.height,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()
                    
                    // 裁剪并压缩到内存
                    val finalBitmap = if (rowPadding == 0) bitmap else 
                        android.graphics.Bitmap.createBitmap(bitmap, 0, 0, reader.width, reader.height)
                    
                    val stream = java.io.ByteArrayOutputStream()
                    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                    
                    if (rowPadding != 0) bitmap.recycle()
                    finalBitmap.recycle()
                    
                    val data = stream.toByteArray()
                    Log.d(TAG, "Captured via ImageReader: ${data.size} bytes")
                    return data
                }
            } catch (e: Exception) {
                Log.e(TAG, "ImageReader capture failed, falling back", e)
            }
        }

        // 2. 降级到 Shell screencap (必须使用 Shizuku 提权执行!)
        return try {
            val displayArg = if (displayId > 0) "-d $displayId" else ""
            val cmd = "screencap $displayArg -p"
            
            // 使用 Shizuku 执行命令
            val process = runShizukuCommand(cmd)
            
            if (process != null) {
                val inputStream = process.inputStream
                val buffer = java.io.ByteArrayOutputStream()
                val data = ByteArray(16384)
                var nRead: Int
                
                while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
                    buffer.write(data, 0, nRead)
                }
                buffer.flush()
                
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    val bytes = buffer.toByteArray()
                    if (bytes.isNotEmpty()) {
                        Log.d(TAG, "Captured via Shizuku Shell: ${bytes.size} bytes")
                        return bytes
                    }
                } else {
                    val errorMsg = process.errorStream.bufferedReader().use { it.readText() }
                    Log.e(TAG, "Shizuku screencap failed (exit $exitCode): $errorMsg")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Shell capture failed", e)
            null
        }
    }
    
    private fun runShizukuCommand(command: String): Process? {
        return try {
             val newProcessMethod = try {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
            } catch (e: NoSuchMethodException) {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java
                )
            }
            newProcessMethod.isAccessible = true
            
            if (newProcessMethod.parameterCount == 3) {
                newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null)
            } else {
                newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null)
            } as Process
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku newProcess failed", e)
            null
        }
    }

    override fun startActivity(displayId: Int, packageName: String): Boolean {
        // 针对后台启动 (displayId > 0)，为了确保 App 真的在后台屏启动而不被拉回主屏，
        // 我们必须使用 "am start -S" (强制停止后冷启动)。
        // 这需要 Shell 权限，所以优先使用 Shizuku。
        if (displayId > 0) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null && intent.component != null) {
                    val componentName = intent.component!!.flattenToShortString()
                    // -S: Force stop target app (关键：防止热启动切回主屏)
                    // -W: Wait for launch to complete
                    // --windowingMode 1: FULLSCREEN (在目标 Display 上全屏)
                    val cmd = "am start -n $componentName --display $displayId -S -W --windowingMode 1"
                    Log.i(TAG, "Background Launch (Shizuku): $cmd")
                    
                    val process = runShizukuCommand(cmd)
                    val exitCode = process?.waitFor() ?: -1
                    if (exitCode == 0) {
                        Log.d(TAG, "✅ Shizuku launch success")
                        return true
                    }
                    Log.w(TAG, "⚠️ Shizuku launch failed (exit $exitCode), falling back to PendingIntent")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku background launch error", e)
            }
        }

        // 主屏启动 (displayId == 0) 或 Shizuku 失败时，使用 PendingIntent
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                Log.e(TAG, "Launch intent not found for $packageName")
                return false
            }
            
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT) 
            
            PendingIntent.getActivity(
                context, 
                packageName.hashCode(), 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, 
                options.toBundle()
            ).send()
            
            Log.d(TAG, "Started activity $packageName on display $displayId via PendingIntent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start activity failed", e)
            
            // Final Fallback
            Log.w(TAG, "Falling back to raw am start...")
            val cmd = "am start --display $displayId $packageName"
            runShizukuCommand(cmd)
            true
        }
    }

    override fun createVirtualDisplay(name: String, width: Int, height: Int, density: Int): Int {
        try {
            // 使用 App Context 获取 DisplayManager
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            
            val imageReader = android.media.ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            
            // Flags: PUBLIC | PRESENTATION
            val flags = 1 or 2 
            val virtualDisplay = displayManager.createVirtualDisplay(
                name, width, height, density, imageReader.surface, flags
            )
            
            if (virtualDisplay != null) {
                val id = virtualDisplay.display.displayId
                Log.i(TAG, "✅ VirtualDisplay created successfully: ID=$id")
                
                displayMap[id] = virtualDisplay
                imageReaders[id] = imageReader
                return id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay", e)
        }
        return -1
    }

    override fun releaseDisplay(displayId: Int) {
        displayMap.remove(displayId)?.release()
        imageReaders.remove(displayId)?.close()
    }

    override fun destroy() {
        displayMap.keys.forEach { releaseDisplay(it) }
    }
}
