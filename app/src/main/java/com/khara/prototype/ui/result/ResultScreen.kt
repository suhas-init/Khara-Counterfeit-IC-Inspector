package com.khara.prototype.ui.result

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khara.prototype.data.ScanSession
import com.khara.prototype.data.SignalScore
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary
import com.khara.prototype.util.HapticHelper
import com.khara.prototype.util.OfficeKitBridge
import com.khara.prototype.util.SoundHelper
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(onReset: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticHelper = remember { HapticHelper(context) }
    val result = ScanSession.currentResult

    var animationState by remember { mutableIntStateOf(0) }
    val scanId = remember { result?.scanId ?: ("KH-" + UUID.randomUUID().toString().take(6).uppercase()) }
    val timestamp = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) }
    val hash = remember {
        result?.hash ?: run {
            val data = "$scanId$timestamp${result?.overallScore ?: 0}"
            val bytes = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(12).uppercase()
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        animationState = 1
        delay(400)
        animationState = 2
        delay(250)
        result?.let {
            if (it.isPassed) {
                hapticHelper.triggerPassFeedback()
                SoundHelper.playPass(context)
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                SoundHelper.playFlag(context)
            }
        }
        delay(100)
        animationState = 3
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("INSPECTION REPORT", letterSpacing = 2.sp, style = MaterialTheme.typography.labelLarge, color = MatrixGreen) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (result != null) {
                Button(
                    onClick = { ScanSession.clear(); onReset() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
                ) { Text("START NEW SCAN", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            }
        }
    ) { padding ->
        if (result == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("SCAN FAILED", color = MaterialTheme.colorScheme.error)
                Button(onClick = onReset, modifier = Modifier.padding(top = 16.dp)) { Text("Back to Home") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Trust Score Header & Verdict Chip
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        TrustScoreHeader(
                            score = result.overallScore,
                            isPassed = result.isPassed,
                            startCount = animationState >= 1,
                            showChip = animationState >= 2
                        )
                    }
                }

                // Summary Line Box
                item {
                    SummaryBox(
                        summary = result.summary.ifBlank {
                            if (result.isPassed) "All signals match registered reference '${result.referenceName ?: "Default"}'"
                            else "Signals deviate from reference"
                        },
                        isPassed = result.isPassed,
                        visible = animationState >= 3
                    )
                }

                // Side-by-side Image Comparison (150dp height, rounded corners)
                item {
                    SideBySideImageComparison(
                        scanPhotoPath = result.scanPhotoPath ?: ScanSession.currentUris.firstOrNull()?.toString(),
                        refPhotoPath = result.referencePhotoPath,
                        isPassed = result.isPassed
                    )
                }

                // Metadata Sub-header
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = Color(0xFF121212),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MatrixGreen)
                        ) {
                            Text(
                                "Fully Offline · On-Device Inference",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = MatrixGreen
                            )
                        }
                        Text(
                            "Model: MobileNet-V2 · Runtime: TFLite · Input: 224×224",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Audit Entry Card & PDF / OfficeKit Buttons
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        AuditEntryCard(scanId, result.userName, timestamp, hash, animationState >= 3)

                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val ok = OfficeKitBridge.syncScanToClipboard(context, result)
                                    if (ok) {
                                        OfficeKitBridge.writeScanToSyncFolder(context, result)
                                        Toast.makeText(context, "Scan sent to supervisor via Office Kit", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sync failed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.height(40.dp),
                                border = BorderStroke(1.dp, MatrixGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MatrixGreen)
                            ) {
                                Text("Send to Supervisor", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val pdfUri = withContext(Dispatchers.IO) {
                                            PdfReportGenerator.generate(
                                                context,
                                                result,
                                                ScanSession.currentUris.firstOrNull(),
                                                scanId,
                                                timestamp,
                                                hash
                                            )
                                        }
                                        if (pdfUri != null) {
                                            Toast.makeText(context, "Report saved to Downloads", Toast.LENGTH_LONG).show()
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(pdfUri, "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        } else {
                                            Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.height(40.dp),
                                border = BorderStroke(1.dp, MatrixGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MatrixGreen)
                            ) {
                                Text("Download PDF", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Per-Signal Breakdown Cards
                item {
                    Text(
                        "PER-SIGNAL BREAKDOWN (${result.signals.size} SIGNALS)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MatrixGreen,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                itemsIndexed(result.signals) { index, signal ->
                    val delayMs = 100 + index * 60
                    val alpha by animateFloatAsState(
                        targetValue = if (animationState >= 3) 1f else 0f,
                        animationSpec = tween(300, delayMillis = delayMs)
                    )
                    SignalCard(signal, alpha)
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun SideBySideImageComparison(
    scanPhotoPath: String?,
    refPhotoPath: String?,
    isPassed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: Scan Image
        ImageCard(
            title = "SCAN",
            imagePath = scanPhotoPath,
            borderColor = if (isPassed) Color(0xFF00FF41) else Color(0xFFFF3131),
            modifier = Modifier.weight(1f)
        )

        // Right: Best-Matching Reference Image
        ImageCard(
            title = "REFERENCE",
            imagePath = refPhotoPath,
            borderColor = Color(0xFF00FF41).copy(0.6f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ImageCard(
    title: String,
    imagePath: String?,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(imagePath) { loadBitmapFromPath(context, imagePath) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "No image",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            color = Color.Black.copy(0.8f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = if (title == "REFERENCE") MatrixGreen else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun loadBitmapFromPath(context: Context, path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    return try {
        if (path.startsWith("content://") || path.startsWith("file://")) {
            val uri = Uri.parse(path)
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } else {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun TrustScoreHeader(score: Int, isPassed: Boolean, startCount: Boolean, showChip: Boolean) {
    val animatedScore by animateIntAsState(targetValue = if (startCount) score else 0, animationSpec = tween(600))
    val chipScale by animateFloatAsState(targetValue = if (showChip) 1f else 0.8f, animationSpec = tween(250))
    val chipAlpha by animateFloatAsState(targetValue = if (showChip) 1f else 0f, animationSpec = tween(250))
    val ringProgress by animateFloatAsState(targetValue = if (startCount) score / 100f else 0f, animationSpec = tween(800))

    val ringColor = if (isPassed) Color(0xFF00FF41) else Color(0xFFFF3131)
    val scoreColor = if (isPassed) Color(0xFF00FF41) else Color(0xFFFF3131)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("TRUST SCORE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            Canvas(modifier = Modifier.size(150.dp)) {
                drawArc(color = Color.DarkGray.copy(0.2f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 6.dp.toPx()))
                drawArc(
                    color = ringColor,
                    startAngle = -90f, sweepAngle = 360f * ringProgress, useCenter = false, style = Stroke(width = 6.dp.toPx())
                )
            }
            Text(
                text = "$animatedScore%",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, fontWeight = FontWeight.Bold, color = scoreColor)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Verdict Chip: PASS (green #00FF41) or FLAG (red #FF3131)
        Surface(
            color = if (isPassed) Color(0xFF00FF41) else Color(0xFFFF3131),
            shape = CircleShape,
            modifier = Modifier.scale(chipScale).alpha(chipAlpha)
        ) {
            Text(
                text = if (isPassed) "PASS" else "FLAGGED",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun SummaryBox(summary: String, isPassed: Boolean, visible: Boolean) {
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(300))
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        color = Color(0xFF121212),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isPassed) Color(0xFF00FF41).copy(0.4f) else Color(0xFFFF3131).copy(0.4f))
    ) {
        Text(
            text = summary,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

@Composable
fun AuditEntryCard(scanId: String, userName: String, timestamp: String, hash: String, visible: Boolean) {
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(300))

    Card(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, TextSecondary.copy(0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Audit Entry", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
            Text("ID: $scanId", style = MaterialTheme.typography.bodySmall, color = Color.White)
            Text("Operator: $userName", style = MaterialTheme.typography.bodySmall, color = Color.White)
            Text("Time: $timestamp", style = MaterialTheme.typography.bodySmall, color = Color.White)
            Text("SHA-256: $hash", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun SignalCard(signal: SignalScore, alpha: Float) {
    val scorePct = (signal.score * 100).toInt().coerceIn(0, 100)
    val isGood = signal.score >= 0.70f

    Card(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = BorderStroke(1.dp, if (isGood) Color(0xFF00FF41).copy(0.4f) else Color(0xFFFF3131).copy(0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(signal.label, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (signal.details.isNotBlank()) {
                    Text(signal.details, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Text(
                text = "$scorePct%",
                color = if (isGood) Color(0xFF00FF41) else Color(0xFFFF3131),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
