package com.jarvis.ceotitan.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ceotitan.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.shadow(elevation, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = JarvisWhite.copy(alpha = 0.92f)
        ),
        border = BorderStroke(1.dp, JarvisBlue.copy(alpha = 0.1f))
    ) {
        Column(content = content)
    }
}

@Composable
fun PulsingOrb(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    isThinking: Boolean = false,
    size: Dp = 120.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f

            if (isListening) {
                drawCircle(
                    color = JarvisBlue.copy(alpha = 0.15f * glowAlpha),
                    radius = radius * pulseScale * 1.3f,
                    center = center
                )
            }

            val gradient = Brush.radialGradient(
                colors = listOf(
                    if (isListening) JarvisAccent else if (isThinking) JarvisGold else JarvisBlue,
                    if (isListening) JarvisBlue else if (isThinking) Color(0xFFFF6D00) else JarvisDarkBlue
                ),
                center = center,
                radius = radius
            )
            drawCircle(brush = gradient, radius = radius * if (isListening) pulseScale else 1f, center = center)

            if (isListening || isThinking) {
                for (i in 0..7) {
                    val angle = (rotationAngle + i * 45f) * PI.toFloat() / 180f
                    val x = center.x + (radius + 8.dp.toPx()) * cos(angle)
                    val y = center.y + (radius + 8.dp.toPx()) * sin(angle)
                    drawCircle(
                        color = JarvisAccent.copy(alpha = glowAlpha * 0.7f),
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

        Text(
            text = "J",
            color = Color.White,
            fontSize = (size.value * 0.35f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun WaveformAnimation(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    color: Color = JarvisBlue,
    barCount: Int = 20
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val amplitudes = (0 until barCount).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = if (isActive) (0.3f + (i % 5) * 0.15f) else 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + i * 60,
                    easing = EaseInOutSine
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$i"
        )
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 2f - 1f)
        val maxHeight = size.height

        amplitudes.forEachIndexed { index, amplitudeState ->
            val barHeight = maxHeight * amplitudeState.value
            val x = index * (barWidth * 2f)
            val y = (maxHeight - barHeight) / 2f

            drawRoundRect(
                color = color.copy(alpha = 0.7f + amplitudeState.value * 0.3f),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
fun JarvisButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    isPrimary: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) JarvisBlue else JarvisLightBlue,
            contentColor = if (isPrimary) Color.White else JarvisBlue
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isPrimary) 4.dp else 0.dp
        )
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun StatusChip(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (isActive) JarvisSuccess.copy(alpha = 0.15f)
                else JarvisError.copy(alpha = 0.1f)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) JarvisSuccess else JarvisError)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = if (isActive) JarvisSuccess else JarvisError,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun JarvisTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = JarvisBlue
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF1A1C2E),
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
