package com.khara.prototype.ui.processing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khara.prototype.data.ScanSession
import com.khara.prototype.engine.RealAnalysisEngine
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

@Composable
fun ProcessingScreen(onProcessingComplete: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(0f) }
    var completedSteps by remember { mutableStateOf(0) }
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(2000))

    val steps = listOf("Vision embedding", "OCR marking check", "Surface texture analysis", "Fusion and trust score")

    LaunchedEffect(Unit) {
        if (ScanSession.currentUris.isEmpty()) { onProcessingComplete(); return@LaunchedEffect }
        val engine = RealAnalysisEngine(context)
        val deferred = async { engine.analyze(ScanSession.currentUris.first()) }
        
        progress = 0.2f; delay(400); completedSteps = 1
        progress = 0.4f; delay(400); completedSteps = 2
        progress = 0.6f; delay(400); completedSteps = 3
        progress = 1.0f; delay(800); completedSteps = 4
        
        ScanSession.currentResult = try { deferred.await() } catch (e: Exception) { null }
        onProcessingComplete()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color(0xFF121212),
            shape = CircleShape,
            border = BorderStroke(1.dp, MatrixGreen),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(
                "Fully Offline · On-Device Inference",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MatrixGreen
            )
        }

        Text("ANALYZING SIGNALS", style = MaterialTheme.typography.labelLarge, color = MatrixGreen, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(32.dp))
        LinearProgressIndicator(progress = animatedProgress, modifier = Modifier.fillMaxWidth().height(8.dp), color = MatrixGreen, trackColor = MatrixGreen.copy(0.1f))
        Spacer(modifier = Modifier.height(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (completedSteps > i) MatrixGreen else TextSecondary.copy(0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(step, color = if (completedSteps > i) Color.White else TextSecondary)
                }
            }
        }
        Spacer(modifier = Modifier.height(64.dp))
        Text("All processing on-device. No network calls.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
