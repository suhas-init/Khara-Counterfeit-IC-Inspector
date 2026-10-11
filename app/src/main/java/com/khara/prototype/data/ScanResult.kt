package com.khara.prototype.data

data class ScanResult(
    val overallScore: Int, // 0 to 100
    val isPassed: Boolean,
    val signals: List<SignalScore>,
    val summary: String = "",
    val referenceName: String? = null,
    val referencePhotoPath: String? = null,
    val scanPhotoPath: String? = null,
    val confidence: Float = 0.1f,
    val confidenceLabel: String = "High confidence",
    val userName: String = "Unknown",
    val scanId: String? = null,
    val hash: String? = null
)
