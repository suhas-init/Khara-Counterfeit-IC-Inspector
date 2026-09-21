package com.khara.prototype.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khara.prototype.data.ScanSession
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onStartScan: () -> Unit) {
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = {
                    Surface(
                        color = Color.Black,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MatrixGreen)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.background(MatrixGreen, CircleShape).size(6.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OFFLINE", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Offline · On-device · No cloud · No data leaves the phone",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            KharaLogo(Modifier.size(96.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "KHARA",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                color = MatrixGreen
            )
            Text(
                text = "Offline Counterfeit Inspector",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onStartScan,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("START NEW SCAN", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp))
            }
            
            ScanSession.currentResult?.let { last ->
                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                    border = BorderStroke(1.dp, MatrixGreen.copy(0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Last Scan", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${last.overallScore}% Trust", fontWeight = FontWeight.Bold)
                            Surface(
                                color = if (last.isPassed) MatrixGreen else Color(0xFFFF3131),
                                shape = CircleShape
                            ) {
                                Text(
                                    if (last.isPassed) "PASS" else "FLAGGED",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text("12:45:00", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                        Text("Scan ID: KH-XXXXXX", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }
    }

    if (showHelp) {
        ModalBottomSheet(onDismissRequest = { showHelp = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
                Text("How to use Khara", style = MaterialTheme.typography.headlineSmall, color = MatrixGreen)
                Spacer(modifier = Modifier.height(16.dp))
                val steps = listOf(
                    "Point the camera at the component.",
                    "Capture three angles when prompted.",
                    "Wait two seconds for on-device analysis.",
                    "Read the trust score and per-signal breakdown.",
                    "Green = pass. Red = flagged, send to lab."
                )
                steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", modifier = Modifier.padding(vertical = 4.dp))
                }
                Text(
                    "Works fully offline. Nothing leaves the phone.",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showHelp = false }, modifier = Modifier.fillMaxWidth()) { Text("Got it") }
            }
        }
    }
}

@Composable
fun KharaLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 4.dp.toPx()
        val size = size.minDimension
        val padding = size * 0.2f
        val chipSize = size - (padding * 2)
        
        drawRect(
            color = MatrixGreen,
            topLeft = Offset(padding, padding),
            size = Size(chipSize, chipSize),
            style = Stroke(width = stroke)
        )
        
        for (i in 0..3) {
            val offset = padding + (chipSize * (i + 1) / 5f)
            drawLine(MatrixGreen, Offset(offset, 0f), Offset(offset, padding), strokeWidth = stroke)
            drawLine(MatrixGreen, Offset(offset, size - padding), Offset(offset, size), strokeWidth = stroke)
            drawLine(MatrixGreen, Offset(0f, offset), Offset(padding, offset), strokeWidth = stroke)
            drawLine(MatrixGreen, Offset(size - padding, offset), Offset(size, offset), strokeWidth = stroke)
        }
        
        drawLine(MatrixGreen, Offset(size*0.4f, size*0.35f), Offset(size*0.4f, size*0.65f), strokeWidth = stroke*1.5f)
        drawLine(MatrixGreen, Offset(size*0.4f, size*0.5f), Offset(size*0.6f, size*0.35f), strokeWidth = stroke*1.5f)
        drawLine(MatrixGreen, Offset(size*0.4f, size*0.5f), Offset(size*0.6f, size*0.65f), strokeWidth = stroke*1.5f)
    }
}
