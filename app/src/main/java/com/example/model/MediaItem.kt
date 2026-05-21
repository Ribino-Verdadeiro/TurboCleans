package com.example.model

import android.net.Uri
import androidx.compose.ui.graphics.Color

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeStr: String,
    val sizeBytes: Long,
    val type: MediaType,
    val durationStr: String? = null,
    val path: String? = null,
    val gradientColors: List<Color> = defaultGradient()
)

enum class MediaType {
    PHOTO,
    VIDEO
}

fun defaultGradient(): List<Color> {
    val presetList = listOf(
        listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
        listOf(Color(0xFF130CB7), Color(0xFF52E5E7)),
        listOf(Color(0xFF1E3C72), Color(0xFF2A5298)),
        listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
        listOf(Color(0xFF3A6073), Color(0xFF3A2832)),
        listOf(Color(0xFF2C3E50), Color(0xFF000000)),
        listOf(Color(0xFF000000), Color(0xFF533483)),
        listOf(Color(0xFF141E30), Color(0xFF243B55))
    )
    return presetList.random()
}
