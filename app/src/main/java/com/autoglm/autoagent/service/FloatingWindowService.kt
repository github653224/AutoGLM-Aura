package com.autoglm.autoagent.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.autoglm.autoagent.data.AgentRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.widget.ImageView
import com.autoglm.autoagent.data.AgentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FloatingWindowService : Service() {

    @Inject
    lateinit var agentRepository: AgentRepository

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
        observeAgentState()
    }

    private fun createFloatingView() {
        val imageView = ImageView(this)
        // Default State (Will be updated by observer)
        imageView.setImageResource(com.autoglm.autoagent.R.drawable.ic_stop_glass) 
        // 彻底移除背景,实现纯图标悬浮
        imageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        imageView.setPadding(0, 0, 0, 0) 
        
        // ... (Listeners remain same)
        // IMPLEMENT DRAGGING AND CLICK HANDLING VIA ONTOUCHLISTENER
        
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val touchSlop = 10 // Threshold to detect drag vs click

        imageView.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    
                    if (java.lang.Math.abs(dx) > touchSlop || java.lang.Math.abs(dy) > touchSlop) {
                         isDragging = true
                         layoutParams.x = initialX + dx
                         layoutParams.y = initialY + dy
                         windowManager?.updateViewLayout(floatView, layoutParams)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // IT WAS A CLICK
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        // Logic handled in performClick via OnClickListener
        imageView.setOnClickListener {
            val state = agentRepository.agentState.value
            when (state) {
                is AgentState.Idle, is AgentState.Error -> {
                    Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()
                    agentRepository.setListening(true)
                }
                is AgentState.Listening -> {
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                    agentRepository.setListening(false)
                }
                is AgentState.Paused -> {
                    Toast.makeText(this, "Resuming...", Toast.LENGTH_SHORT).show()
                    agentRepository.resumeAgent()
                }
                is AgentState.Running, is AgentState.Planning -> {
                    Toast.makeText(this, "Stopping...", Toast.LENGTH_SHORT).show()
                    agentRepository.stopAgent()
                }
            }
        }
        
        // REMOVED Long Press to Close (As per user feedback)

        
        // ... Listeners set previously ...
        
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Remove alpha on the window itself, let the view handle alpha/transparency
            // If we set window alpha, the whole icon becomes ghost-like
            // But user wanted transparency... let's keep it 1.0f for the window and handle it in the view if needed
            // 图标本身已有透明度或设计,窗口 alpha 设为 1.0 避免重叠变淡
            alpha = 1.0f 
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }
        floatView = imageView
        windowManager?.addView(floatView, layoutParams)
    }

    private fun observeAgentState() {
        serviceScope.launch {
            agentRepository.agentState.collectLatest { state ->
                val view = floatView as? ImageView ?: return@collectLatest
                
                // 移除此处强制设置的背景
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                when (state) {
                    is AgentState.Idle, is AgentState.Error -> {
                        view.setImageResource(com.autoglm.autoagent.R.drawable.ic_mic_glass)
                    }
                    is AgentState.Listening -> {
                        view.setImageResource(com.autoglm.autoagent.R.drawable.ic_mic_glass)
                    }
                    is AgentState.Paused -> {
                        view.setImageResource(com.autoglm.autoagent.R.drawable.ic_keyboard_glass)
                    }
                    is AgentState.Running, is AgentState.Planning -> {
                        view.setImageResource(com.autoglm.autoagent.R.drawable.ic_stop_glass)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (floatView != null) windowManager?.removeView(floatView)
    }
}
