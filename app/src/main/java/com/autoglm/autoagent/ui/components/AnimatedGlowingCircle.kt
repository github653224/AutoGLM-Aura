package com.autoglm.autoagent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.autoglm.autoagent.ui.theme.*

@Composable
fun AnimatedGlowingCircle(
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    // 呼吸动画 - 缩放效果
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // 透明度动画
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    // 旋转动画 (外圈缓慢旋转)
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(200.dp)) {
        val canvasSize = size.minDimension
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        val baseRadius = canvasSize / 2.5f
        val scaledRadius = baseRadius * scale

        // 内层圆环 - 实心发光核心
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    PrimaryBlue.copy(alpha = alpha * 0.8f),
                    AccentPurple.copy(alpha = alpha * 0.4f),
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                radius = scaledRadius * 0.4f
            ),
            radius = scaledRadius * 0.4f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )

        // 中层圆环 - 蓝色主圆环
        drawCircle(
            color = PrimaryBlueLight.copy(alpha = alpha * 0.8f),
            radius = scaledRadius * 0.7f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // 外层圆环 - 紫色辉光
        drawCircle(
            color = AccentPurple.copy(alpha = alpha * 0.6f),
            radius = scaledRadius,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 最外层发光层 - 模糊效果
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GlowBlue.copy(alpha = alpha * 0.3f),
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                radius = scaledRadius * 1.3f
            ),
            radius = scaledRadius * 1.3f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )
    }
}
