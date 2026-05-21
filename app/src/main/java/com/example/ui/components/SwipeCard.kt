package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun SwipeCard(
    item: MediaItem,
    onSwipeLeft: (MediaItem) -> Unit,
    onSwipeRight: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Core state keyed to item.id to ensure instant reset on card swap
    var isDragging by remember(item.id) { mutableStateOf(false) }
    var dragX by remember(item.id) { mutableFloatStateOf(0f) }
    var dragY by remember(item.id) { mutableFloatStateOf(0f) }
    
    val animOffsetX = remember(item.id) { Animatable(0f) }
    val animOffsetY = remember(item.id) { Animatable(0f) }

    // Defer state reads using lambdas to completely bypass recomposition during active dragging
    val getOffsetX = { if (isDragging) dragX else animOffsetX.value }
    val getOffsetY = { if (isDragging) dragY else animOffsetY.value }

    val maxSwipeDistance = 450f // Threshold for deciding swiping actions

    // Calculate background hue alpha opacity depending on swipe side (deferred to draw phase)
    val getLeftSwipeAlpha = { (abs(getOffsetX().coerceAtMost(0f)) / maxSwipeDistance).coerceIn(0f, 0.82f) }
    val getRightSwipeAlpha = { (getOffsetX().coerceAtLeast(0f) / maxSwipeDistance).coerceIn(0f, 0.82f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(410.dp)
            .graphicsLayer {
                translationX = getOffsetX()
                translationY = getOffsetY()
                rotationZ = (getOffsetX() / 25f).coerceIn(-15f, 15f)
            }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            // First, snap animatable to the drag offset so switching is completely seamless
                            animOffsetX.snapTo(dragX)
                            animOffsetY.snapTo(dragY)
                            isDragging = false // Transition the source of truth to the animatable
                            
                            if (dragX < -maxSwipeDistance) {
                                // Swipe Left (Apagar/Delete) - Slide Off Screen Left
                                animOffsetX.animateTo(-1200f, tween(250))
                                onSwipeLeft(item)
                            } else if (dragX > maxSwipeDistance) {
                                // Swipe Right (Salvar/Save) - Slide Off Screen Right
                                animOffsetX.animateTo(1200f, tween(250))
                                onSwipeRight(item)
                            } else {
                                // Return to center position
                                launch { animOffsetX.animateTo(0f, tween(200)) }
                                launch { animOffsetY.animateTo(0f, tween(200)) }
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animOffsetX.snapTo(dragX)
                            animOffsetY.snapTo(dragY)
                            isDragging = false
                            launch { animOffsetX.animateTo(0f, tween(200)) }
                            launch { animOffsetY.animateTo(0f, tween(200)) }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    }
                )
            }
            .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            // 1. Image Thumbnail or Premium Gradient Fallback
            if (item.uri != Uri.EMPTY) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Background visual simulation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = item.gradientColors
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = if (item.type == MediaType.VIDEO) Icons.Default.PlayCircle else Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (item.type == MediaType.VIDEO) "VÍDEO PESADO" else "IMAGEM CACHED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // 2. Play Indicator for Video files
            if (item.type == MediaType.VIDEO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(54.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(100.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video marker",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (item.durationStr != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = item.durationStr,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Size badge on the top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.sizeStr,
                    color = PrimaryTeal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // 3. Bottom Information overlay with text shadow background
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (item.type == MediaType.VIDEO) "Armazenamento Interno > Vídeos" else "Armazenamento Interno > Fotos",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 4. Overlays displaying selection cues (DELETE vs KEEP)
            // Red overlay (DELETE) is always in composition but opacity is evaluated purely on the GPU/draw phase
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = getLeftSwipeAlpha()
                    }
                    .background(AccentRed),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Swipe to delete indicator",
                        tint = Color.White,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "APAGAR PARA SEMPRE",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Green overlay (KEEP) is always in composition but opacity is evaluated purely on the GPU/draw phase
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = getRightSwipeAlpha()
                    }
                    .background(AccentGreen),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Swipe to save indicator",
                        tint = Color.White,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SALVAR / MANTER",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
