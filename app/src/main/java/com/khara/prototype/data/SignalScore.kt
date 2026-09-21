package com.khara.prototype.data

data class SignalScore(
    val label: String,
    val score: Float, // 0.0 to 1.0
    val isPlaceholder: Boolean = false
)
