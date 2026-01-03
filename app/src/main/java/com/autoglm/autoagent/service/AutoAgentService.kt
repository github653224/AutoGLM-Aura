package com.autoglm.autoagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.*

class AutoAgentService : AccessibilityService() {

    companion object {
        var instance: AutoAgentService? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var currentPackageName: String = ""
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AutoAgent", "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        if (event?.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let {
                currentPackageName = it.toString()
                Log.d("AutoAgent", "App changed: $currentPackageName")
            }
        }
    }

    override fun onInterrupt() {
        Log.w("AutoAgent", "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("AutoAgent", "AccessibilityService destroyed")
    }

    // Basic click
    fun click(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        val builder = GestureDescription.Builder()
        val gestureDescription = builder
            .addStroke(StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gestureDescription, null, null)
    }
    
    // Long press implementation
    fun longPress(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        val builder = GestureDescription.Builder()
        // Long press usually requires > 500ms, using 800ms to be safe
        val gestureDescription = builder
            .addStroke(StrokeDescription(path, 0, 800)) 
            .build()
        Log.d("AutoAgent", "Dispatching Long Press at ($x, $y)")
        return dispatchGesture(gestureDescription, null, null)
    }

    // Double tap implementation
    fun doubleTap(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        val builder = GestureDescription.Builder()
        // First tap
        val stroke1 = StrokeDescription(path, 0, 50)
        // Second tap, starts 100ms after first one ends (so at 150ms)
        val stroke2 = StrokeDescription(path, 150, 50)
        
        val gestureDescription = builder
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
            
        Log.d("AutoAgent", "Dispatching Double Tap at ($x, $y)")
        return dispatchGesture(gestureDescription, null, null)
    }

    // Swipe / Scroll
    fun scroll(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val builder = GestureDescription.Builder()
        val gestureDescription = builder
            .addStroke(StrokeDescription(path, 0, 500))
            .build()
        return dispatchGesture(gestureDescription, null, null)
    }
    
    // Text input
    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focusedNode.recycle()
            root.recycle()
            return result
        }
        root.recycle()
        return false
    }

    // Screenshot (API 30+)
    suspend fun takeScreenshotAsync(): Bitmap? = suspendCoroutine { cont ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            // 开启协程在后台线程处理，避免阻塞主线程（takeScreenshot 在 mainExecutor 回调）
                            serviceScope.launch(Dispatchers.Default) {
                                val buffer = screenshot.hardwareBuffer
                                try {
                                    val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                    if (hwBitmap != null) {
                                        // 关键修复：Hardware Bitmap 在模拟器上压缩会变黑，且 copy 是耗时操作
                                        // 必须在非 UI 线程执行
                                        val softwareBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        hwBitmap.recycle()
                                        
                                        if (softwareBitmap != null) {
                                            Log.d("AutoAgent", "✅ Screenshot SUCCESS (Software): ${softwareBitmap.width}x${softwareBitmap.height}")
                                            cont.resume(softwareBitmap)
                                        } else {
                                            Log.e("AutoAgent", "❌ Failed to copy to software bitmap")
                                            cont.resume(null)
                                        }
                                    } else {
                                        Log.e("AutoAgent", "❌ wrapHardwareBuffer returned null")
                                        cont.resume(null)
                                    }
                                } catch (e: Exception) {
                                    Log.e("AutoAgent", "❌ Failed to process screenshot", e)
                                    cont.resume(null)
                                } finally {
                                    // 必须显式关闭 HardwareBuffer，否则会造成严重的 Native 内存泄漏
                                    try { buffer.close() } catch (e: Exception) { /* IGNORE */ }
                                }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                             Log.e("AutoAgent", "❌ Screenshot FAILED: $errorCode")
                             cont.resume(null)
                        }
                    })
            } catch (e: Exception) {
                 Log.e("AutoAgent", "❌ Exception calling takeScreenshot", e)
                 cont.resume(null)
            }
        } else {
             cont.resume(null)
        }
    }

    fun dumpOptimizedUiTree(): String {
        val root = rootInActiveWindow ?: return "{ \"error\": \"No active window\" }"
        val sb = StringBuilder()
        
        // Get screen metrics for normalization
        val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val metrics = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        sb.append("{ \"ui\": [")
        dumpOptimizedNode(root, sb, true, screenW, screenH)
        sb.append("] }")
        
        root.recycle()
        return sb.toString()
    }

    private fun dumpOptimizedNode(node: android.view.accessibility.AccessibilityNodeInfo?, sb: StringBuilder, isFirst: Boolean, screenW: Int, screenH: Int): Boolean {
        if (node == null) return false
        if (!node.isVisibleToUser) return false

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        
        // 边界检查,允许一小部分在屏幕外(处理半滑出列表)
        if (bounds.right < -50 || bounds.bottom < -50 || bounds.left > screenW + 50 || bounds.top > screenH + 50) return false

        // 提取属性
        val text = node.text?.toString()?.replace("\"", "\\\"")?.replace("\n", " ")
        val desc = node.contentDescription?.toString()?.replace("\"", "\\\"")?.replace("\n", " ")
        val resId = node.viewIdResourceName?.split('/')?.lastOrNull() // 仅提取 ID 末尾
        
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isScrollable = node.isScrollable
        val isEnabled = node.isEnabled
        val isChecked = node.isChecked
        val isSelected = node.isSelected
        val isFocused = node.isFocused

        val hasInfo = !text.isNullOrBlank() || !desc.isNullOrBlank() || !resId.isNullOrBlank()
        val isInteractive = isClickable || isEditable || isScrollable
        
        // 递归处理子节点
        val childrenSb = StringBuilder()
        var hasImportantChild = false
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (dumpOptimizedNode(child, childrenSb, !hasImportantChild, screenW, screenH)) {
                hasImportantChild = true
            }
            child?.recycle()
        }

        // 放宽裁剪: 只要有信息(ID/Text/Desc)或者是交互组件, 或者包含重要子节点, 就保留
        if (!isInteractive && !hasInfo && !hasImportantChild) return false

        // 序列化 JSON
        if (!isFirst) sb.append(",")
        sb.append("{")
        
        val className = node.className?.toString()?.split('.')?.lastOrNull() ?: ""
        sb.append("\"c\":\"$className\"")
        
        if (!resId.isNullOrBlank()) sb.append(",\"id\":\"$resId\"")
        if (!text.isNullOrBlank()) sb.append(",\"t\":\"$text\"")
        if (!desc.isNullOrBlank()) sb.append(",\"d\":\"$desc\"")
        
        // 坐标归一化
        val l = (bounds.left.toFloat() / screenW * 1000).toInt().coerceIn(0, 1000)
        val t = (bounds.top.toFloat() / screenH * 1000).toInt().coerceIn(0, 1000)
        val r = (bounds.right.toFloat() / screenW * 1000).toInt().coerceIn(0, 1000)
        val b = (bounds.bottom.toFloat() / screenH * 1000).toInt().coerceIn(0, 1000)
        sb.append(",\"b\":[$l,$t,$r,$b]")
        
        // 标志位
        if (isClickable) sb.append(",\"k\":1")
        if (isEditable) sb.append(",\"e\":1")
        if (isScrollable) sb.append(",\"s\":1")
        if (!isEnabled) sb.append(",\"dis\":1") // 仅记录“禁用”状态以省 token
        if (isChecked) sb.append(",\"ch\":1")
        if (isSelected) sb.append(",\"sel\":1")
        if (isFocused) sb.append(",\"f\":1")

        if (hasImportantChild) {
            sb.append(",\"children\":[").append(childrenSb).append("]")
        }

        sb.append("}")
        return true
    }
}
