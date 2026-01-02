package com.autoglm.autoagent.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// 1. Backgrounds & Surface (Midnight Void)
val DarkBackground = Color(0xFF0F172A)      // Deep Blue-Grey
val DarkBackgroundSecondary = Color(0xFF1E293B) // Slightly lighter
val DarkSurface = Color(0xFF1E293B)

// 2. Glass Layer (The core of Glassmorphism)
val GlassLight = Color(0x33FFFFFF)          // 20% White
val GlassMedium = Color(0x661E293B)         // 40% Dark Blue
val GlassHeavy = Color(0xCC0F172A)          // 80% Dark

// 3. Primary Accents (Neon)
val PrimaryBlue = Color(0xFF3B82F6)         // Neon Azure
val PrimaryBlueDark = Color(0xFF1D4ED8)     // Darker Blue
val PrimaryBlueLight = Color(0xFF60A5FA)    // Lighter Blue
val PrimaryCyan = Color(0xFF06B6D4)         // Electric Cyan
val PrimaryPurple = Color(0xFF8B5CF6)       // Electric Purple
val AccentPurple = PrimaryPurple            // Alias for AnimatedGlowingCircle
val GlowBlue = PrimaryBlueLight             // Alias for AnimatedGlowingCircle

// 4. Status Colors
val StatusRed = Color(0xFFEF4444)           // Plasma Red
val StatusGreen = Color(0xFF10B981)         // Aurora Green
val StatusAmber = Color(0xFFF59E0B)         // Warning Amber

// 5. Text
val TextPrimary = Color(0xFFF8FAFC)         // Starlight
val TextSecondary = Color(0xFF94A3B8)       // Moon Grey
val TextAccent = Color(0xFF60A5FA)          // Light Blue Text
val TextHint = Color(0xFF64748B)            // Slate Grey for Placeholders

// 6. Gradients
val GradientPrimary = Brush.linearGradient(
    colors = listOf(PrimaryBlue, PrimaryCyan)
)
val GradientSurface = Brush.verticalGradient(
    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
)

