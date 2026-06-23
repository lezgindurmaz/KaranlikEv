package com.example

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
)

data class SubtitleItem(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
