package com.khara.prototype.data

import android.net.Uri

object ScanSession {
    var currentUris: List<Uri> = emptyList()
    var currentResult: ScanResult? = null

    fun clear() {
        currentUris = emptyList()
        currentResult = null
    }
}
