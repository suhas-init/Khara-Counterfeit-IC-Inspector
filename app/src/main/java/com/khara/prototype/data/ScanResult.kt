package com.khara.prototype.data

data class ScanResult(
    val overallScore: Int, // 0 to 100
    val isPassed: Boolean,
    val signals: List<SignalScore>
)
