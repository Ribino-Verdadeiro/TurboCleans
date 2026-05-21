package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.PrimaryTealDark

@Composable
fun PulseRadar(
    modifier: Modifier = Modifier,
    sizeDp: Int = 180
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")

    // Radar rotating line sweep angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Pulsing circles
    val pulseSize1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse1"
    )

    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha1"
    )

    val pulseSize2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, delayMillis = 750, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse2"
    )

    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, delayMillis = 750, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha2"
    )

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val maxRadius = width / 2f

            // 1. Draw pulsating ripples
            drawCircle(
                color = PrimaryTeal.copy(alpha = pulseAlpha1),
                radius = maxRadius * pulseSize1,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = PrimaryTealDark.copy(alpha = pulseAlpha2),
                radius = maxRadius * pulseSize2,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 2. Static grid-like circles
            drawCircle(
                color = PrimaryTeal.copy(alpha = 0.15f),
                radius = maxRadius * 0.4f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            drawCircle(
                color = PrimaryTeal.copy(alpha = 0.15f),
                radius = maxRadius * 0.7f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            drawCircle(
                color = PrimaryTeal.copy(alpha = 0.25f),
                radius = maxRadius,
                center = center,
                style = Stroke(width = 1.5f.dp.toPx())
            )

            // 3. Draw crosshairs
            drawLine(
                color = PrimaryTeal.copy(alpha = 0.1f),
                start = androidx.compose.ui.geometry.Offset(0f, center.y),
                end = androidx.compose.ui.geometry.Offset(width, center.y),
                strokeWidth = 1.dp.toPx()
            )

            drawLine(
                color = PrimaryTeal.copy(alpha = 0.1f),
                start = androidx.compose.ui.geometry.Offset(center.x, 0f),
                end = androidx.compose.ui.geometry.Offset(center.x, height),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 4. Rotating sweep sensor beam
        Box(
            modifier = Modifier
                .size((sizeDp - 10).dp)
                .rotate(rotationAngle)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                // Sweep radar arc
                val brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        PrimaryTeal.copy(alpha = 0.01f),
                        PrimaryTeal.copy(alpha = 0.35f)
                    ),
                    center = center
                )
                drawCircle(
                    brush = brush,
                    radius = radius,
                    center = center
                )
            }
        }

        // Center glowing radar target icon
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val innerCenter = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = PrimaryTeal.copy(alpha = 0.2f),
                    radius = size.width / 2f,
                    center = innerCenter
                )
                drawCircle(
                    color = PrimaryTeal,
                    radius = 8.dp.toPx(),
                    center = innerCenter
                )
            }
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Radar scan identifier",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
