package com.autoglm.autoagent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.autoglm.autoagent.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.path

// Custom Mic Icon definition to avoid dependency issues with material-icons-extended
private val MicIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fillAlpha = 1f, strokeAlpha = 1f, pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero) {
            moveTo(12f, 14f)
            curveTo(14.21f, 14f, 16f, 12.21f, 16f, 10f)
            verticalLineTo(5f)
            curveTo(16f, 2.79f, 14.21f, 1f, 12f, 1f)
            curveTo(9.79f, 1f, 8f, 2.79f, 8f, 5f)
            verticalLineTo(10f)
            curveTo(8f, 12.21f, 9.79f, 14f, 12f, 14f)
            close()
            moveTo(17f, 10f)
            horizontalLineTo(19f)
            curveTo(19f, 13.53f, 16.39f, 16.46f, 13f, 16.92f)
            verticalLineTo(20f)
            horizontalLineTo(17f)
            verticalLineTo(22f)
            horizontalLineTo(7f)
            verticalLineTo(20f)
            horizontalLineTo(11f)
            verticalLineTo(16.92f)
            curveTo(7.61f, 16.46f, 5f, 13.53f, 5f, 10f)
            horizontalLineTo(7f)
            curveTo(7f, 12.76f, 9.24f, 15f, 12f, 15f)
            curveTo(14.76f, 15f, 17f, 12.76f, 17f, 10f)
            close()
        }
    }.build()

/**
 * A complex, "Living" Orb that replaces the static button.
 * It consists of multiple rotating and pulsating layers to simulate organic energy.
 */
@Composable
fun LivingOrb(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    isListening: Boolean,
    isPressed: Boolean = false // Magnetic Feedback Input
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_main")

    // 1. Core Breathing (Heartbeat)
    val coreScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_scale"
    )

    // 2. Magnetic Feedback (Press Bounce)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "press_scale"
    )

    // 3. Orbital Rings Rotation
    val rotationFast by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_fast"
    )

    val rotationSlow by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_slow"
    )
    
    // 4. Energy Flux
    val fluxAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
             animation = tween(1500, easing = LinearEasing),
             repeatMode = RepeatMode.Reverse
        ),
        label = "flux"
    )

    // 5. Sonar Ripple (Idle Only)
    val sonarRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing, delayMillis = 1000), // Every 3s + delay
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar"
    )

     // 6. Lightning Scan (Mic)
    val lightningOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f, // Expanded range for smooth pass
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lightning"
    )


    val primaryColor = if (isActive || isListening) PrimaryBlue else Color.Gray
    val secondaryColor = if (isActive || isListening) PrimaryPurple else Color.DarkGray
    val coreColor = if (isListening) PrimaryCyan else if (isActive) PrimaryBlue else Color(0xFF475569)

    Box(
        modifier = modifier.graphicsLayer { 
             scaleX = pressScale
             scaleY = pressScale
        }, 
        contentAlignment = Alignment.Center
    ) {
        
        // Layer 0: Sonar Ripple (Behind everything)
        if (!isActive && !isListening) {
             Canvas(modifier = Modifier.fillMaxSize()) {
                 val maxR = size.minDimension / 1.6f
                 val currentR = maxR * sonarRadius
                 val currentAlpha = (1f - sonarRadius) * 0.3f 
                 
                 drawCircle(
                     color = PrimaryBlue.copy(alpha = currentAlpha),
                     radius = currentR,
                     style = Stroke(width = 1.dp.toPx())
                 )
             }
        }

        // Layer 1: Glow Cloud
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f }) {
            val maxR = size.minDimension / 2.0f
            val safeRadius = maxR * 0.85f * coreScale 
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent),
                    center = center,
                    radius = safeRadius
                ),
                radius = safeRadius
            )
        }

        // Layer 2: Fast Orbiting Ring
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotationFast }) {
            val r = size.minDimension / 2.8f
            drawCircle(
                style = Stroke(width = 2.dp.toPx()),
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, primaryColor, Color.Transparent)
                ),
                radius = r
            )
        }

        // Layer 3: Slow Orbiting Geometric Ring
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotationSlow }) {
            val r = size.minDimension / 2.2f
            drawCircle(
                style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 20f), 0f)),
                color = secondaryColor.copy(alpha = 0.5f),
                radius = r
            )
            drawCircle(
                color = secondaryColor,
                radius = 3.dp.toPx(),
                center = center + Offset(r, 0f) 
            )
        }

        // Layer 4: The Core (Liquid Blob)
        Canvas(modifier = Modifier.size(120.dp)) {
            val maxR = size.minDimension / 2
            val blobRadius = maxR * 0.7f * coreScale
            
            for (i in 0..2) {
                val offsetX = cos(Math.toRadians(rotationFast.toDouble() + i * 120)) * 8.dp.toPx() 
                val offsetY = sin(Math.toRadians(rotationFast.toDouble() + i * 120)) * 8.dp.toPx()
                
                drawCircle(
                    color = coreColor.copy(alpha = fluxAlpha),
                    radius = blobRadius,
                    center = center + Offset(offsetX.toFloat(), offsetY.toFloat())
                )
            }
            // Center solid
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha=0.9f), coreColor),
                ),
                radius = maxR * 0.5f
            )
        }
        
        // Layer 5: Lightning Microphone
        Box(contentAlignment = Alignment.Center) {
             // Base White Mic
             androidx.compose.material3.Icon(
                 imageVector = MicIcon,
                 contentDescription = null,
                 tint = Color.White.copy(alpha = 0.9f),
                 modifier = Modifier.size(32.dp)
             )
             
             // Active Blue Mic (Masked by Scan)
             androidx.compose.material3.Icon(
                 imageVector = MicIcon,
                 contentDescription = null,
                 tint = PrimaryBlue.copy(alpha=1f), // High brightness
                 modifier = Modifier
                     .size(32.dp)
                     .drawWithContent {
                         val bandHeight = size.height * 0.3f
                         val currentY = size.height * lightningOffset
                         
                         // Clip to the moving band
                         clipRect(
                             top = currentY - bandHeight/2,
                             bottom = currentY + bandHeight/2
                         ) {
                             this@drawWithContent.drawContent()
                         }
                     }
             )
             
             // Full Blue Flash (when listening)
             if (isListening) {
                 androidx.compose.material3.Icon(
                     imageVector = MicIcon,
                     contentDescription = null,
                     tint = PrimaryBlue,
                     modifier = Modifier.size(32.dp)
                 )
             }
        }
    }
}
