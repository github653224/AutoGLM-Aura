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

/**
 * A complex, "Living" Orb that replaces the static button.
 * It consists of multiple rotating and pulsating layers to simulate organic energy.
 */
@Composable
fun LivingOrb(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    isListening: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_main")

    // 1. Core Breathing (Heartbeat) - Tighter range
    val coreScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_scale"
    )

    // 2. Orbital Rings Rotation
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
    
    // 3. Energy Flux
    val fluxAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
             animation = tween(1500, easing = LinearEasing),
             repeatMode = RepeatMode.Reverse
        ),
        label = "flux"
    )

    val primaryColor = if (isActive || isListening) PrimaryBlue else Color.Gray
    val secondaryColor = if (isActive || isListening) PrimaryPurple else Color.DarkGray
    val coreColor = if (isListening) PrimaryCyan else if (isActive) PrimaryBlue else Color(0xFF475569)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        
        // Layer 1: Glow Cloud (Background) - SCALED DOWN TO AVOID CLIPPING
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f }) {
            val maxR = size.minDimension / 2.0f
            // Ensure even at max scale it fits: 0.85 * 1.0 = 0.85 < 1.0
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
            val r = size.minDimension / 2.8f // Safe size
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
            val r = size.minDimension / 2.2f // Safe size
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
        // Increased container size to prevent internal clipping
        Canvas(modifier = Modifier.size(120.dp)) {
            val maxR = size.minDimension / 2
            val blobRadius = maxR * 0.7f * coreScale
            
            for (i in 0..2) {
                // Reduced offset to keep blob tight
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
    }
}
