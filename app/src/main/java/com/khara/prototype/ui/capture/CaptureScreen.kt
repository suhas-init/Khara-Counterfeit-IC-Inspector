package com.khara.prototype.ui.capture

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khara.prototype.ui.theme.MatrixGreen
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(
    onCaptureComplete: (List<Uri>) -> Unit,
    viewModel: CaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    val currentAngleIndex by viewModel.currentAngleIndex.collectAsState()
    
    val customInstructions = listOf(
        "Step 1 of 3 — Top-down view",
        "Step 2 of 3 — Angled / Raking light (critical for blacktopping)",
        "Step 3 of 3 — Side markings view"
    )
    val currentInstruction = customInstructions.getOrElse(currentAngleIndex) { customInstructions.last() }

    var flashTrigger by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (flashTrigger) MatrixGreen else Color.Transparent,
        animationSpec = tween(200),
        finishedListener = { flashTrigger = false }
    )

    val infiniteTransition = rememberInfiniteTransition()
    val scanLineOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing))
    )

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(context))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).border(4.dp, borderColor)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val y = maxHeight * scanLineOffset
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).offset(y = y).background(MatrixGreen.copy(0.3f)))
            }

            Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(280.dp).border(1.dp, MatrixGreen.copy(0.3f)))
            }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 24.dp, end = 24.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MatrixGreen.copy(0.5f))
            ) {
                Text(
                    text = currentInstruction,
                    color = MatrixGreen,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            FloatingActionButton(
                onClick = {
                    takePhoto(context, imageCapture, cameraExecutor) { uri ->
                        flashTrigger = true
                        viewModel.onImageCaptured(uri)
                        val count = viewModel.capturedImages.value.size
                        val msg = if (count < 3) "Capture $count of 3 saved" else "Capture 3 of 3 saved. Analyzing..."
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                        if (viewModel.isCaptureComplete()) onCaptureComplete(viewModel.capturedImages.value)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).size(80.dp),
                shape = CircleShape,
                containerColor = MatrixGreen
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Capture", modifier = Modifier.size(40.dp), tint = Color.Black)
            }
        }
    }
}

private fun takePhoto(context: Context, imageCapture: ImageCapture, executor: ExecutorService, onImageCaptured: (Uri) -> Unit) {
    val photoFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            (context as? Activity)?.runOnUiThread { onImageCaptured(Uri.fromFile(photoFile)) }
        }
        override fun onError(exception: ImageCaptureException) { exception.printStackTrace() }
    })
}
