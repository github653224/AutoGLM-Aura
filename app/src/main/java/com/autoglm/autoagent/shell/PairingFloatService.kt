package com.autoglm.autoagent.shell

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 悬浮窗配对服务
 * 
 * 在设置页面上方显示输入框，避免切换 App 时配对码失效
 */
class PairingFloatService : Service() {
    
    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatWindow()
            ACTION_HIDE -> hideFloatWindow()
        }
        return START_NOT_STICKY
    }
    
    private fun showFloatWindow() {
        if (floatView != null) return
        
        // 创建简单的布局
        floatView = createFloatView()
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        try {
            windowManager.addView(floatView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }
    
    private fun createFloatView(): View {
        // 使用代码创建简单布局
        val context = this
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xE0303030.toInt())
            setPadding(48, 32, 48, 32)
        }
        
        val title = TextView(context).apply {
            text = "🔗 AutoDroid 配对"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 24)
        }
        container.addView(title)
        
        val portLabel = TextView(context).apply {
            text = "配对端口 (弹窗里的)："
            setTextColor(0xFFCCCCCC.toInt())
        }
        container.addView(portLabel)
        
        val portInput = EditText(context).apply {
            hint = "例如: 37123"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            tag = "port"
        }
        container.addView(portInput)
        
        val codeLabel = TextView(context).apply {
            text = "6位配对码："
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 16, 0, 0)
        }
        container.addView(codeLabel)
        
        val codeInput = EditText(context).apply {
            hint = "例如: 123456"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            tag = "code"
        }
        container.addView(codeInput)
        
        val buttonContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
        }
        
        val pairButton = Button(context).apply {
            text = "✓ 配对"
            setOnClickListener {
                val port = portInput.text.toString().toIntOrNull()
                val code = codeInput.text.toString()
                
                if (port == null || port < 1000) {
                    Toast.makeText(context, "请输入有效的端口", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (code.length != 6) {
                    Toast.makeText(context, "配对码必须是6位数字", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // 发送广播通知主 App 进行配对
                sendBroadcast(Intent(ACTION_PAIR).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_PORT, port)
                    putExtra(EXTRA_CODE, code)
                })
                
                Toast.makeText(context, "正在配对...", Toast.LENGTH_SHORT).show()
            }
        }
        buttonContainer.addView(pairButton)
        
        val cancelButton = Button(context).apply {
            text = "✕ 取消"
            setOnClickListener {
                hideFloatWindow()
                stopSelf()
            }
        }
        buttonContainer.addView(cancelButton)
        
        container.addView(buttonContainer)
        
        return container
    }
    
    private fun hideFloatWindow() {
        floatView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { /* ignore */ }
            floatView = null
        }
    }
    
    override fun onDestroy() {
        hideFloatWindow()
        scope.cancel()
        super.onDestroy()
    }
    
    companion object {
        const val ACTION_SHOW = "com.autoglm.autoagent.SHOW_PAIRING_FLOAT"
        const val ACTION_HIDE = "com.autoglm.autoagent.HIDE_PAIRING_FLOAT"
        const val ACTION_PAIR = "com.autoglm.autoagent.DO_PAIR"
        const val EXTRA_PORT = "port"
        const val EXTRA_CODE = "code"
        
        fun canDrawOverlays(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }
        
        fun show(context: Context) {
            context.startService(Intent(context, PairingFloatService::class.java).apply {
                action = ACTION_SHOW
            })
        }
        
        fun hide(context: Context) {
            context.startService(Intent(context, PairingFloatService::class.java).apply {
                action = ACTION_HIDE
            })
        }
    }
}
