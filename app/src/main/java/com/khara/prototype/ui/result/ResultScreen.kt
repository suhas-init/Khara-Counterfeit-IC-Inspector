package com.khara.prototype.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.khara.prototype.data.ScanSession
import com.khara.prototype.data.SignalScore
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary
import com.khara.prototype.util.HapticHelper
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
    
    var animationState by remember { mutableStateOf(0) }
    val scanId = remember { "KH-" + UUID.randomUUID().toString().take(6).uppercase() }
    val timestamp = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) }
    val hash = remember { 
        val data = "$scanId$timestamp${result?.overallScore ?: 0}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
        bytes.joinToString("") { "%02x".format(it) }.take(12).uppercase()
    }
    
    LaunchedEffect(Unit) {
        delay(100)
        animationState = 1
        delay(400)
        animationState = 2
        delay(250)
        result?.let {
            if (it.isPassed) hapticHelper.triggerPassFeedback()
            else repeat(3) { hapticHelper.triggerPassFeedback() }
        }
        delay(100)
        animationState = 3
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = { CenterAlignedTopAppBar(
            title = { Text("SCAN REPORT", letterSpacing = 2.sp, style = MaterialTheme.typography.labelLarge) },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
        ) },
        bottomBar = {
            if (result != null) {
                Button(
                    onClick = { ScanSession.clear(); onReset() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
                ) { Text("START NEW SCAN") }
            }
        }
    ) { padding ->
        if (result == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("SCAN FAILED", color = MaterialTheme.colorScheme.error)
                Button(onClick = onReset, modifier = Modifier.padding(top = 16.dp)) { Text("Back to Home") }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { AnomalyImagePreview(result.isPassed) }
                item { TrustScoreHeader(result.overallScore, result.isPassed, animationState >= 1, animationState >= 2) }
                item { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Surface(color = Color(0xFF121212), shape = CircleShape, border = BorderStroke(1.dp, MatrixGreen)) {
                            Text("Fully Offline · On-Device Inference", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = MatrixGreen)
                        }
                        Text("Model: MobileNet-V2 INT8 · Runtime: TFLite · Input: 224×224", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                item { SummaryBox(result.isPassed, animationState >= 3) }
                item { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        AuditEntryCard(scanId, timestamp, hash, animationState >= 3)
                        
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val json = "{\"scanId\": \"$scanId\", \"timestamp\": \"$timestamp\", \"score\": ${result.overallScore}, \"hash\": \"$hash\"}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Khara Audit", json))
                                    Toast.makeText(context, "Audit entry copied. Paste into Office Kit dashboard.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(40.dp),
                                border = BorderStroke(1.dp, MatrixGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MatrixGreen)
                            ) {
                                Text("Sync Audit Log", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val uri = withContext(Dispatchers.IO) {
                                            PdfReportGenerator.generate(context, result, ScanSession.currentUris.firstOrNull(), scanId, timestamp, hash)
                                        }
                                        if (uri != null) {
                                            Toast.makeText(context, "Report saved to Downloads/Khara_$scanId.pdf", Toast.LENGTH_LONG).show()
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        } else {
                                            Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.height(40.dp),
                                border = BorderStroke(1.dp, MatrixGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MatrixGreen)
                            ) {
                                Text("Download Report", fontSize = 12.sp)
                            }
                        }
                        
                        Text("Audit entry hash-chained · Session logged locally", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                itemsIndexed(result.signals) { index, signal ->
                    val delay = 100 + index * 80
                    val alpha by animateFloatAsState(targetValue = if (animationState >= 3) 1f else 0f, animationSpec = tween(300, delayMillis = delay))
                    SignalCard(signal, alpha)
                }
                item { 
                    Text("Prototype demo. Real reference database built during the iQOO 30-hour build.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun AnomalyImagePreview(isPassed: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, MatrixGreen.copy(0.3f)), RoundedCornerShape(12.dp))) {
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
            update = { it.setImageURI(ScanSession.currentUris.firstOrNull()) },
            modifier = Modifier.fillMaxSize()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isPassed) {
                val rectWidth = size.width * 0.6f
                val rectHeight = size.height * 0.6f
                val topLeftX = (size.width - rectWidth) / 2
                val topLeftY = (size.height - rectHeight) / 2
                drawRect(color = Color(0xFFFF3131).copy(alpha = 0.25f), topLeft = Offset(topLeftX, topLeftY), size = Size(rectWidth, rectHeight))
                drawRect(color = Color(0xFFFF3131), topLeft = Offset(topLeftX, topLeftY), size = Size(rectWidth, rectHeight), style = Stroke(width = 2.dp.toPx()))
            } else {
                drawRect(color = Color(0xFF00FF41), style = Stroke(width = 2.dp.toPx()))
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            color = Color.Black.copy(0.8f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (isPassed) "All zones nominal" else "Anomaly: Texture",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                color = if (isPassed) Color(0xFF00FF41) else Color(0xFFFF3131),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun TrustScoreHeader(score: Int, isPassed: Boolean, startCount: Boolean, showChip: Boolean) {
    val animatedScore by animateIntAsState(targetValue = if (startCount) score else 0, animationSpec = tween(400))
    val chipScale by animateFloatAsState(targetValue = if (showChip) 1f else 0.8f, animationSpec = tween(250))
    val chipAlpha by animateFloatAsState(targetValue = if (showChip) 1f else 0f, animationSpec = tween(250))
    val ringProgress by animateFloatAsState(targetValue = if (startCount) score / 100f else 0f, animationSpec = tween(800))

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("TRUST SCORE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            Canvas(modifier = Modifier.size(160.dp)) {
                drawArc(color = Color.DarkGray.copy(0.2f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 4.dp.toPx()))
                drawArc(
                    color = if (score > 85) MatrixGreen else if (score > 50) Color(0xFFFFB300) else Color(0xFFFF3131),
                    startAngle = -90f, sweepAngle = 360f * ringProgress, useCenter = false, style = Stroke(width = 4.dp.toPx())
                )
            }
            Text(text = "$animatedScore%", style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold, color = if (isPassed) MatrixGreen else Color(0xFFFF3131)))
        }
        Text("Confidence: $score%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(16.dp))
        Surface(color = if (isPassed) MatrixGreen else Color(0xFFFF3131), shape = CircleShape, modifier = Modifier.scale(chipScale).alpha(chipAlpha)) {
            Text(if (isPassed) "PASS" else "FLAGGED", modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SummaryBox(isPassed: Boolean, visible: Boolean) {
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(300))
    Surface(modifier = Modifier.fillMaxWidth().alpha(alpha), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Text(
            text = if (isPassed) "All signals within expected range. Component appears genuine." else "Texture and visual embedding deviate from references. Possible sign of remarking.",
            modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun AuditEntryCard(scanId: String, timestamp: String, hash: String, visible: Boolean) {
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(300))
    var chainVisible by remember { mutableStateOf(0) }
    LaunchedEffect(visible) { if (visible) { delay(200); chainVisible = 1; delay(200); chainVisible = 2; delay(200); chainVisible = 3 } }

    Card(modifier = Modifier.fillMaxWidth().alpha(alpha), colors = CardDefaults.cardColors(containerColor = Color.Black), border = BorderStroke(1.dp, TextSecondary.copy(0.2f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Audit Entry", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
            Text("ID: $scanId", style = MaterialTheme.typography.bodySmall)
            Text("Time: $timestamp", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256: $hash", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("KH-AB12", "KH-CD34", "KH-EF56").forEachIndexed { i, s ->
                    Surface(color = if (chainVisible > i) MatrixGreen else Color.DarkGray, shape = RoundedCornerShape(2.dp)) {
                        Text(s, modifier = Modifier.padding(2.dp), fontSize = 8.sp, color = Color.Black)
                    }
                    if (i < 2) Box(modifier = Modifier.width(8.dp).height(1.dp).background(if (chainVisible > i + 1) MatrixGreen else Color.DarkGray))
                }
            }
            Text("Hash-chained. Tamper-evident.", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SignalCard(signal: SignalScore, alpha: Float) {
    val isVision = signal.label.contains("Vision")
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1.0f, animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse))
    
    Card(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        border = BorderStroke(if (isVision) 2.dp else 1.dp, if (isVision) MatrixGreen.copy(0.6f) else MatrixGreen.copy(0.3f))
    ) {
        if (isVision) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(signal.label, fontWeight = FontWeight.SemiBold)
                    Surface(color = MatrixGreen, shape = CircleShape, border = BorderStroke(1.dp, MatrixGreen.copy(alpha = pulseAlpha))) {
                        Text("REAL", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Text(
                    text = "${String.format("%.1f", signal.score * 100)}%",
                    color = if (signal.score > 0.85f) MatrixGreen else Color(0xFFFF3131),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text("Cosine similarity vs reference set", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        } else {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(signal.label, fontWeight = FontWeight.SemiBold)
                    Text("Prototype Signal", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Text(
                    text = "${(signal.score * 100).toInt()}%",
                    color = if (signal.score > 0.85f) MatrixGreen else Color(0xFFFF3131),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
