package com.khara.prototype.engine

import android.net.Uri
import com.khara.prototype.data.ScanResult

interface AnalysisEngine {
    suspend fun analyze(imageUri: Uri): ScanResult
}
