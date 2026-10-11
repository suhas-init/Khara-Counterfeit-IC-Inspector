package com.khara.prototype.data

import android.net.Uri

object ScanSession {
    var currentUris: List<Uri> = emptyList()
    var currentResult: ScanResult? = null
    var selectedReferenceName: String? = null
    var currentUser: String = "Suhas"

    fun clear() {
        currentUris = emptyList()
        currentResult = null
        selectedReferenceName = null
    }
}
