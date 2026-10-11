package com.khara.prototype.ui.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khara.prototype.data.ScanSession
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary
import com.khara.prototype.util.SoundHelper
import com.khara.prototype.util.VoiceCommandHelper
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(
    onCaptureComplete: (List<Uri>) -> Unit,
    refName: String = "",
    viewModel: CaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val imageCapture = remember { ImageCapture.Builder().build() }
    val voiceHelper = remember { VoiceCommandHelper(context) }
    var isListening by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                isListening = true
                voiceHelper.startContinuousListening(
                    onCommand = { text ->
                        val lower = text.lowercase().trim()
                        val triggers = listOf("scan", "capture", "next", "photo", "take")
                        if (triggers.any { lower.contains(it) }) {
                            if (viewModel.capturedImages.value.size < 3) {
                                takePhoto(context, imageCapture, cameraExecutor) { uri ->
                                    (context as? Activity)?.runOnUiThread {
                                        viewModel.onImageCaptured(uri)
                                        if (viewModel.capturedImages.value.size >= 3) {
                                            voiceHelper.stopContinuousListening()
                                            isListening = false
                                            if (refName.isNotBlank()) {
                                                ScanSession.selectedReferenceName = refName
                                            }
                                            onCaptureComplete(viewModel.capturedImages.value)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onError = { /* silent */ }
                )
            } else {
                Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            try { voiceHelper.stopContinuousListening() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(refName) {
        if (refName.isNotBlank()) {
            ScanSession.selectedReferenceName = refName
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var isFlashOn by remember { mutableStateOf(true) }

    val previewView = remember { PreviewView(context) }

    val currentAngleIndex by viewModel.currentAngleIndex.collectAsState()
    val capturedImages by viewModel.capturedImages.collectAsState()

    val angleInstructions = listOf(
        "Angle 1 of 3: Top-down view",
        "Angle 2 of 3: Angled raking light",
        "Angle 3 of 3: Side markings"
    )
    val currentInstruction = angleInstructions.getOrElse(currentAngleIndex) { angleInstructions.last() }

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

    // Trigger Photo Capture Action
    val triggerPhotoCapture: () -> Unit = {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))

        takePhoto(context, imageCapture, cameraExecutor) { uri ->
            (context as? Activity)?.runOnUiThread {
                flashTrigger = true
                SoundHelper.playCapture(context)
                viewModel.onImageCaptured(uri)
                val count = viewModel.capturedImages.value.size
                val msg = if (count < 3) "Capture $count of 3 saved" else "Capture 3 of 3 saved. Analyzing..."
                scope.launch { snackbarHostState.showSnackbar(msg) }
                if (count >= 3 || viewModel.isCaptureComplete()) {
                    voiceHelper.stopContinuousListening()
                    isListening = false
                    if (refName.isNotBlank()) {
                        ScanSession.selectedReferenceName = refName
                    }
                    onCaptureComplete(viewModel.capturedImages.value)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                cameraProvider.unbindAll()
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                camera = boundCamera
                boundCamera.cameraControl.enableTorch(isFlashOn)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(zoomRatio) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    LaunchedEffect(isFlashOn) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).border(4.dp, borderColor)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Laser scan animation line
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val y = maxHeight * scanLineOffset
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).offset(y = y).background(MatrixGreen.copy(0.3f)))
            }

            // Semi-transparent center rectangle overlay (70% width, 60% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .fillMaxHeight(0.60f)
                    .align(Alignment.Center)
                    .border(1.dp, MatrixGreen, RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(0.2f))
            ) {
                Text(
                    text = "Fill this area with the chip",
                    color = Color.White.copy(0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // Top status header: Angle instruction, Caption, & Progress Dots
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MatrixGreen.copy(0.6f))
                ) {
                    Text(
                        text = currentInstruction,
                        color = MatrixGreen,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = if (isListening) "Listening... say 'scan'" else "Tap mic for hands-free",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until 3) {
                        val isDone = i < capturedImages.size
                        val isCurrent = i == capturedImages.size
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isDone) MatrixGreen else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isCurrent) MatrixGreen else TextSecondary.copy(0.5f),
                                    CircleShape
                                )
                        )
                    }
                }
            }

            // Top Action Controls: Flash Toggle & Voice Command Toggle
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voice Command Button
                IconButton(
                    onClick = {
                        if (isListening) {
                            voiceHelper.stopContinuousListening()
                            isListening = false
                        } else {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                isListening = true
                                voiceHelper.startContinuousListening(
                                    onCommand = { text ->
                                        val lower = text.lowercase().trim()
                                        val triggers = listOf("scan", "capture", "next", "photo", "take")
                                        if (triggers.any { lower.contains(it) }) {
                                            if (viewModel.capturedImages.value.size < 3) {
                                                takePhoto(context, imageCapture, cameraExecutor) { uri ->
                                                    (context as? Activity)?.runOnUiThread {
                                                        viewModel.onImageCaptured(uri)
                                                        if (viewModel.capturedImages.value.size >= 3) {
                                                            voiceHelper.stopContinuousListening()
                                                            isListening = false
                                                            if (refName.isNotBlank()) {
                                                                ScanSession.selectedReferenceName = refName
                                                            }
                                                            onCaptureComplete(viewModel.capturedImages.value)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onError = { /* silent */ }
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Voice",
                        tint = if (isListening) Color(0xFF00FF41) else Color(0xFF808080)
                    )
                }

                // Flash Toggle Button
                IconButton(
                    onClick = { isFlashOn = !isFlashOn },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flash",
                        tint = MatrixGreen
                    )
                }
            }

            // Vertical Zoom Slider
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .width(40.dp)
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Slider(
                    value = zoomRatio,
                    onValueChange = { zoomRatio = it },
                    valueRange = 1.0f..5.0f,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = -90f }
                        .width(180.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MatrixGreen,
                        activeTrackColor = MatrixGreen
                    )
                )
            }

            // Capture Button
            FloatingActionButton(
                onClick = triggerPhotoCapture,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .size(80.dp),
                shape = CircleShape,
                containerColor = MatrixGreen
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Capture", modifier = Modifier.size(40.dp), tint = Color.Black)
            }
        }
    }
}

private fun takePhoto(context: Context, imageCapture: ImageCapture, executor: ExecutorService, onImageCaptured: (Uri) -> Unit) {
    val photoFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            (context as? Activity)?.runOnUiThread { onImageCaptured(Uri.fromFile(photoFile)) }
        }
        override fun onError(exception: ImageCaptureException) { exception.printStackTrace() }
    })
}
