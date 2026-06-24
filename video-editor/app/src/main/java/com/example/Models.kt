package com.example

import java.io.Serializable

data class VideoTextOverlay(
    val text: String,
    val size: Float, // text size (e.g., 20f to 100f)
    val xCo: Float,  // -1f (left) to 1f (right)
    val yCo: Float,  // -1f (bottom) to 1f (top)
    val startMs: Long,
    val endMs: Long,
    val rotation: Float = 0f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
) : Serializable

data class SubtitleItem(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val colorHex: String = "#FFFF00" // Default Yellow
) : Serializable

enum class EffectType {
    ZOOM_IN, ZOOM_OUT, SLIDE_LEFT, SLIDE_RIGHT, FADE_IN, FADE_OUT
}

data class EffectItem(
    val type: EffectType,
    val startMs: Long,
    val endMs: Long
) : Serializable

enum class FilterType {
    GRAYSCALE, SEPIA, CYBERPUNK, VINTAGE, COOL, WARM
}

data class FilterItem(
    val type: FilterType,
    val startMs: Long,
    val endMs: Long
) : Serializable
