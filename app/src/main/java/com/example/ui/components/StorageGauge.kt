package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.PrimaryTealDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StorageGauge(
    percentage: Int,
    usedLabel: String,
    totalLabel: String,
    title: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 140
) {
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(percentage) {
        animationTriggered = true
    }

    val progressAngle by animateFloatAsState(
        targetValue = if (animationTriggered) (percentage / 100f) * 270f else 0f,
        animationSpec = tween(durationMillis = 1500),
        label = "ProgressAngle"
    )

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp - 10).dp)) {
            val center = size / 2f
            val strokeWidthPx = 14.dp.toPx()
            val arcSize = size.width - strokeWidthPx

            // Draw track (270 degrees arc starting at 135)
            drawArc(
                color = CardBorder,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Draw glowing progress arc with linear gradient
            val progressBrush = Brush.linearGradient(
                colors = listOf(PrimaryTeal, PrimaryTealDark)
            )

            drawArc(
                brush = progressBrush,
                startAngle = 135f,
                sweepAngle = progressAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$percentage%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "$usedLabel / $totalLabel",
                fontSize = 11.sp,
                color = PrimaryTeal,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
