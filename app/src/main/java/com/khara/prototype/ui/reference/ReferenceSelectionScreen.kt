package com.khara.prototype.ui.reference

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khara.prototype.data.ReferenceItem
import com.khara.prototype.data.ReferenceStore
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceSelectionScreen(
    onReferenceSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var references by remember { mutableStateOf(emptyList<ReferenceItem>()) }

    LaunchedEffect(Unit) {
        try {
            ReferenceStore.load(context)
            references = ReferenceStore.getAll(context)
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error loading references in SelectionScreen", e)
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = { Text("SELECT REFERENCE CHIP", style = MaterialTheme.typography.titleMedium, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (references.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = MatrixGreen.copy(0.5f), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No references registered yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "You must register at least one reference chip before starting a scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Register Chip", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(references) { ref ->
                    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
                    val dateStr = remember(ref.createdAt) { dateFormat.format(Date(ref.createdAt)) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReferenceSelected(ref.name) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                        border = BorderStroke(1.dp, MatrixGreen.copy(0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ReferenceSelectionThumbnail(photoPath = ref.photoPaths.firstOrNull())
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
                                Text(
                                    text = "Registered: $dateStr",
                                    fontSize = 11.sp,
                                    color = TextSecondary.copy(0.7f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select",
                                tint = MatrixGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReferenceSelectionThumbnail(photoPath: String?, modifier: Modifier = Modifier) {
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
                Log.e("KHARA_ERROR", "Error recycling selection thumbnail bitmap", e)
            }
        }
    }

    Box(
        modifier = modifier
            .size(72.dp)
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
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
