package com.khara.prototype.ui.home

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khara.prototype.data.ReferenceItem
import com.khara.prototype.data.ReferenceStore
import com.khara.prototype.data.ScanSession
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRegisterReference: () -> Unit,
    onStartScan: () -> Unit,
    onOpenReference: (String) -> Unit,
    onOpenResult: () -> Unit = {}
) {
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }
    var references by remember { mutableStateOf(emptyList<ReferenceItem>()) }

    LaunchedEffect(Unit) {
        try {
            Log.d("KHARA_HOME", "Loading registered references")
            ReferenceStore.load(context)
            references = ReferenceStore.getAll(context)
        } catch (e: Exception) {
            Log.e("KHARA_HOME", "Error loading references", e)
        }
    }

    val recentRefs = remember(references) { references.sortedByDescending { it.createdAt }.take(3) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KharaLogo(Modifier.size(80.dp))
                    Spacer(modifier = Modifier.height(12.dp))
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
                }
            }

            // Action Buttons
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRegisterReference,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MatrixGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MatrixGreen)
                    ) {
                        Text("REGISTER REFERENCE CHIP", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp))
                    }

                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("START NEW SCAN", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp))
                    }
                }
            }

            // Recent References Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent References (${references.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (recentRefs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                        border = BorderStroke(1.dp, TextSecondary.copy(0.2f))
                    ) {
                        Text(
                            text = "No references registered yet. Tap 'Register Reference Chip' to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp).fillMaxWidth()
                        )
                    }
                }
            } else {
                items(recentRefs) { ref ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReference(ref.name) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                        border = BorderStroke(1.dp, MatrixGreen.copy(0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ReferenceThumbnail(photoPath = ref.photoPaths.firstOrNull())
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ref.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${ref.photoPaths.size} photo(s)",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Last Scan Result Card
            ScanSession.currentResult?.let { last ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { onOpenResult() },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                        border = BorderStroke(1.dp, MatrixGreen.copy(0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Last Scan Result", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${last.overallScore}% Trust", fontWeight = FontWeight.Bold, color = Color.White)
                                Surface(
                                    color = if (last.isPassed) MatrixGreen else Color(0xFFFF3131),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = if (last.isPassed) "PASS" else "FLAGGED",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (last.summary.isNotBlank()) {
                                Text(last.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
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
                    "Register a reference chip with 3-5 reference photos.",
                    "Tap on a registered reference to view or manage its photos.",
                    "Point the camera at the component to scan.",
                    "Capture three angles when prompted.",
                    "Wait for on-device multi-signal analysis.",
                    "Read the trust score and signal breakdown."
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
fun ReferenceThumbnail(photoPath: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(photoPath) {
        try {
            if (!photoPath.isNullOrBlank()) {
                val file = File(photoPath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(bitmap) {
        onDispose {
            try {
                bitmap?.recycle()
            } catch (e: Exception) {
                Log.e("KHARA_HOME", "Error recycling thumbnail bitmap", e)
            }
        }
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
            .border(1.dp, MatrixGreen.copy(0.4f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = MatrixGreen.copy(0.7f),
                modifier = Modifier.size(28.dp)
            )
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

        drawLine(MatrixGreen, Offset(size * 0.4f, size * 0.35f), Offset(size * 0.4f, size * 0.65f), strokeWidth = stroke * 1.5f)
        drawLine(MatrixGreen, Offset(size * 0.4f, size * 0.5f), Offset(size * 0.6f, size * 0.35f), strokeWidth = stroke * 1.5f)
        drawLine(MatrixGreen, Offset(size * 0.4f, size * 0.5f), Offset(size * 0.6f, size * 0.65f), strokeWidth = stroke * 1.5f)
    }
}
