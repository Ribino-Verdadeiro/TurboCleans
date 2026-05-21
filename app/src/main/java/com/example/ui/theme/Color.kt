package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Elegant Dark design theme palette
val PrimaryTeal = Color(0xFFD1E4FF)       // Glowing azure / Light blue
val PrimaryTealDark = Color(0xFF0061A4)   // Deep accent blue
val AccentGreen = Color(0xFF00E676)       // Save / Safe state
val AccentRed = Color(0xFFFF1744)         // Trash / Deletion
val WarningOrange = Color(0xFFFFAD1F)     // Warning state

val DarkBackground = Color(0xFF111318)    // Elegant dark pure background
val CardBackground = Color(0xFF1A1C1E)    // Premium textured cards
val CardBorder = Color(0xFF43474E)        // Crisp subtle border
val TextPrimary = Color(0xFFE2E2E6)       // Clean light text matching template
val TextSecondary = Color(0xFFC4C6D0)     // Classic secondary label text
val TextMuted = Color(0xFF727782)         // Small metadata logs/dates

// Additional contextual container colors from Elegant Dark theme
val ContainerMedium = Color(0xFF2D3135)   // Middle-depth panels
val ContainerNav = Color(0xFF1A1C1E)      // Bottom nav bar background
val AccentGlowGlint = Color(0xFF33485D)   // High-depth details/avatar ring

// Keep legacy values in case templates refer to them or to prevent any build breaks
val Purple80 = PrimaryTeal
val PurpleGrey80 = PrimaryTealDark
val Pink80 = AccentGreen
val Purple40 = PrimaryTeal
val PurpleGrey40 = PrimaryTealDark
val Pink40 = AccentRed
