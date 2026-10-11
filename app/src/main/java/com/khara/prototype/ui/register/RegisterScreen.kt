package com.khara.prototype.ui.register

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.khara.prototype.data.ReferenceStore
import com.khara.prototype.engine.RealAnalysisEngine
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary
import com.khara.prototype.util.HapticHelper
import com.khara.prototype.util.SoundHelper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val hapticHelper = remember { HapticHelper(context) }

    var chipName by remember { mutableStateOf("") }
    val photoFiles = remember { mutableStateListOf<File>() }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var isFlashOn by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
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
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = { Text("REGISTER REFERENCE CHIP", style = MaterialTheme.typography.titleMedium, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Chip Name TextField
            OutlinedTextField(
                value = chipName,
                onValueChange = { chipName = it },
                label = { Text("Chip Name (e.g., 74LS00)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MatrixGreen,
                    unfocusedBorderColor = TextSecondary,
                    focusedLabelColor = MatrixGreen,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Live Camera Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, MatrixGreen), RoundedCornerShape(12.dp))
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                // Overlay reticle box (70% width, 60% height)
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Flash Toggle Button
                IconButton(
                    onClick = { isFlashOn = !isFlashOn },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flash",
                        tint = MatrixGreen
                    )
                }

                // Vertical Zoom Slider
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .width(40.dp)
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Slider(
                        value = zoomRatio,
                        onValueChange = { zoomRatio = it },
                        valueRange = 1.0f..5.0f,
                        modifier = Modifier
                            .graphicsLayer { rotationZ = -90f }
                            .width(160.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MatrixGreen,
                            activeTrackColor = MatrixGreen
                        )
                    )
                }
            }

            // Capture Controls & Progress Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Photos: ${photoFiles.size} / 5",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until 5) {
                            val isDone = i < photoFiles.size
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isDone) MatrixGreen else Color.Transparent)
                                    .border(1.dp, if (isDone) MatrixGreen else TextSecondary.copy(0.5f), CircleShape)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))

                        val sanitizedName = chipName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default_chip" }
                        val refDir = File(context.filesDir, "refs/$sanitizedName").apply { mkdirs() }
                        val destFile = File(refDir, "photo_${System.currentTimeMillis()}.jpg")
                        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")

                        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                        imageCapture.takePicture(
                            outputOptions,
                            cameraExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    try {
                                        val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                                        if (bitmap != null) {
                                            FileOutputStream(destFile).use { out ->
                                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                            }
                                        }
                                        tempFile.delete()
                                        (context as? Activity)?.runOnUiThread {
                                            if (photoFiles.size < 5) photoFiles.add(destFile)
                                            SoundHelper.playCapture(context)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                }
                            }
                        )
                    },
                    enabled = chipName.isNotBlank() && photoFiles.size < 5,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            if (chipName.isNotBlank() && photoFiles.size < 5) MatrixGreen else Color.Gray,
                            CircleShape
                        )
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Capture Reference Photo", tint = Color.Black)
                }
            }

            // Thumbnail Review Strip
            if (photoFiles.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(photoFiles) { file ->
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MatrixGreen, RoundedCornerShape(8.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                    }
                                },
                                update = { imageView ->
                                    if (file.exists()) {
                                        imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = {
                                    file.delete()
                                    photoFiles.remove(file)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(24.dp)
                                    .background(Color.Black.copy(0.7f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete Photo",
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Reference Button (enabled when >= 3 photos)
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    scope.launch {
                        val engine = RealAnalysisEngine(context)
                        val absolutePaths = photoFiles.map { it.absolutePath }
                        val embeddings = mutableListOf<FloatArray>()

                        var pHash = ""
                        var pinCount = 14
                        var texLapVar = 0.0
                        var texLbpEnt = 0.0
                        var texScratch = 0.0

                        withContext(Dispatchers.Default) {
                            for (file in photoFiles) {
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                if (bitmap != null) {
                                    val emb = engine.extractEmbeddingFromBitmap(bitmap)
                                    embeddings.add(emb)
                                } else {
                                    embeddings.add(FloatArray(512))
                                }
                            }

                            val firstFile = photoFiles.firstOrNull()
                            val firstBitmap = if (firstFile != null && firstFile.exists()) {
                                BitmapFactory.decodeFile(firstFile.absolutePath)
                            } else null

                            if (firstBitmap != null) {
                                pHash = engine.computePHashPublic(firstBitmap)
                                pinCount = engine.countPinsPublic(firstBitmap)
                                val tex = engine.analyzeTexturePublic(firstBitmap)
                                texLapVar = tex.laplacianVar
                                texLbpEnt = tex.lbpEntropy
                                texScratch = tex.scratchRatio
                            }
                        }

                        ReferenceStore.add(
                            context = context,
                            name = chipName.trim(),
                            photoPaths = absolutePaths,
                            embeddings = embeddings,
                            pHash = pHash,
                            pinCount = pinCount,
                            textureLaplacianVariance = texLapVar,
                            textureLbpEntropy = texLbpEnt,
                            textureScratchRatio = texScratch
                        )

                        hapticHelper.triggerPassFeedback()
                        SoundHelper.playSaveSuccess(context)
                        Toast.makeText(context, "Reference saved", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
                },
                enabled = chipName.isNotBlank() && photoFiles.size >= 3 && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Text("SAVE REFERENCE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}
