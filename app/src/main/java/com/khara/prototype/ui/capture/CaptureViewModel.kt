package com.khara.prototype.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CaptureViewModel : ViewModel() {

    val angles = listOf(
        "Top-down view (1/3)",
        "Angled / raking light view (2/3)",
        "Side / marking view (3/3)"
    )

    private val _currentAngleIndex = MutableStateFlow(0)
    val currentAngleIndex: StateFlow<Int> = _currentAngleIndex.asStateFlow()

    private val _capturedImages = MutableStateFlow<List<Uri>>(emptyList())
    val capturedImages: StateFlow<List<Uri>> = _capturedImages.asStateFlow()

    fun onImageCaptured(uri: Uri) {
        val newList = _capturedImages.value + uri
        _capturedImages.value = newList
        if (_currentAngleIndex.value < angles.size - 1) {
            _currentAngleIndex.value++
        }
    }

    fun isCaptureComplete(): Boolean {
        return _capturedImages.value.size >= 3
    }
}
