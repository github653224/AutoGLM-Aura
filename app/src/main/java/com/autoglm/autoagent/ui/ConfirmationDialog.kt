package com.autoglm.autoagent.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.autoglm.autoagent.R

/**
 * 任务确认对话框
 * 用于复杂任务的计划展示和用户确认
 */
class ConfirmationDialog(
    context: Context,
    private val planSummary: String,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit,
    private val onVoiceInput: (() -> Unit)? = null
) : Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    init {
        setupDialog()
    }

    private fun setupDialog() {
        // 创建对话框布局
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            setBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        // 标题
        val titleView = TextView(context).apply {
            text = "📋 任务计划确认"
            textSize = 20f
            setTextColor(Color.parseColor("#212121"))
            setPadding(0, 0, 0, dpToPx(16))
        }
        layout.addView(titleView)

        // 计划内容
        val contentView = TextView(context).apply {
            text = planSummary
            textSize = 16f
            setTextColor(Color.parseColor("#616161"))
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            maxLines = 10
            // 可滚动
            isVerticalScrollBarEnabled = true
        }
        layout.addView(contentView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(16)
        })

        // 提示文本
        val hintView = TextView(context).apply {
            text = "💬 您也可以直接语音回复（如：\"好的，开始\"）"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
        }
        layout.addView(hintView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(16)
        })

        // 按钮容器
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // 取消按钮
        val cancelButton = createButton("取消", Color.parseColor("#757575")) {
            dismiss()
            onCancel()
        }
        buttonLayout.addView(cancelButton, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            rightMargin = dpToPx(8)
        })

        // 执行按钮
        val confirmButton = createButton("✅ 执行", Color.parseColor("#4CAF50")) {
            dismiss()
            onConfirm()
        }
        buttonLayout.addView(confirmButton, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            leftMargin = dpToPx(8)
        })

        layout.addView(buttonLayout)

        // 语音按钮（如果提供了回调）
        if (onVoiceInput != null) {
            val voiceButton = createButton("🎤 语音回复", Color.parseColor("#2196F3")) {
                onVoiceInput.invoke()
            }
            layout.addView(voiceButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            })
        }

        setContentView(layout)

        // 配置窗口属性
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
        }

        setCancelable(false) // 必须明确选择
    }

    private fun createButton(text: String, bgColor: Int, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(bgColor)
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setOnClickListener { onClick() }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
