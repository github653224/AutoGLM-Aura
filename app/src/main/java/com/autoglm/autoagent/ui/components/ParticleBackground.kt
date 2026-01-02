package com.autoglm.autoagent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.autoglm.autoagent.ui.theme.PrimaryBlue
import com.autoglm.autoagent.ui.theme.PrimaryPurple
import kotlin.random.Random

data class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val angle: Float,
    val color: Color
)

@Composable
fun ParticleBackground(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Generate fixed random particles (deterministic for recomposition stability)
    val particles = remember {
        List(30) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 4 + 2,
                speed = Random.nextFloat() * 0.2f + 0.05f,
                angle = Random.nextFloat() * 360,
                color = if (Random.nextBoolean()) PrimaryBlue.copy(alpha=0.2f) else PrimaryPurple.copy(alpha=0.2f)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        particles.forEach { p ->
            // Simple movement logic: y moves up, x drifts slightly
            val currentY = (p.y - time * p.speed + 1) % 1 // Loop 0..1
            val currentX = (p.x + kotlin.math.sin(time * 10 + p.angle) * 0.05f).toFloat() // Gentle drift
            
            drawCircle(
                color = p.color,
                radius = p.size,
                center = Offset(currentX * w, currentY * h)
            )
        }
    }
}
