package com.bible.starsnap.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BibleColors {
    val Brand = Color(0xFFFFE55B)
    val BrandStrong = Color(0xFFE0BE2A)
    val Ink = Color(0xFF171B24)
    val Canvas = Color(0xFFF6F7FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFEEF1F6)
    val Border = Color(0xFFE2E6ED)
    val TextSubtle = Color(0xFF505866)
    val TextMuted = Color(0xFF687285)
    val Danger = Color(0xFFB91C1C)
    val DangerSoft = Color(0xFFFEF2F2)
    val Success = Color(0xFF047857)
    val SuccessSoft = Color(0xFFECFDF5)
    val Warning = Color(0xFFD97706)
}

private val lightColors = lightColorScheme(
    primary = BibleColors.Ink,
    onPrimary = Color.White,
    secondary = BibleColors.BrandStrong,
    onSecondary = BibleColors.Ink,
    background = BibleColors.Canvas,
    onBackground = BibleColors.Ink,
    surface = BibleColors.Surface,
    onSurface = BibleColors.Ink,
    surfaceVariant = BibleColors.SurfaceSubtle,
    outline = BibleColors.Border,
    error = BibleColors.Danger,
)

private val darkColors = darkColorScheme(
    primary = BibleColors.Brand,
    onPrimary = BibleColors.Ink,
    secondary = Color(0xFFFFEB7A),
    onSecondary = BibleColors.Ink,
    background = Color(0xFF121722),
    onBackground = Color(0xFFF4F6FA),
    surface = Color(0xFF19202C),
    onSurface = Color(0xFFF4F6FA),
    surfaceVariant = Color(0xFF222C3B),
    outline = Color(0xFF60708A),
    error = Color(0xFFFF7B7B),
)

@Composable
fun StarSnapBibleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors,
        content = content,
    )
}
