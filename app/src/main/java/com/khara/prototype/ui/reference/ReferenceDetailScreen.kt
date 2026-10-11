package com.khara.prototype.ui.reference

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import com.khara.prototype.data.ScanSession
import com.khara.prototype.ui.theme.MatrixGreen
import com.khara.prototype.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceDetailScreen(
    chipName: String,
    onBack: () -> Unit,
    onUseAsReference: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var currentRef by remember { mutableStateOf<ReferenceItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(chipName, refreshTrigger) {
        try {
            Log.d("KHARA_REF", "Loading reference detail for '$chipName'")
            currentRef = ReferenceStore.getAll(context).firstOrNull { it.name == chipName }
        } catch (e: Exception) {
            Log.e("KHARA_REF", "Error loading reference $chipName", e)
        }
    }

    val ref = currentRef

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ref?.name ?: chipName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MatrixGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (ref == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Reference '$chipName' not found", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Go Back")
                }
            }
        } else {
            val dateStr = remember(ref.createdAt) {
                try {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ref.createdAt))
                } catch (e: Exception) {
                    "Unknown date"
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Metadata Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                    border = BorderStroke(1.dp, MatrixGreen.copy(0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("REFERENCE DETAILS", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
                        Text("Chip Name: ${ref.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Registered: $dateStr", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Photos Stored: ${ref.photoPaths.size}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (ref.embeddings.isNotEmpty()) {
                            Text("Embedding Dim: ${ref.embeddings.first().size}-dim float vector", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                // "Use as Reference" Action Button
                Button(
                    onClick = {
                        ScanSession.selectedReferenceName = ref.name
                        onUseAsReference(ref.name)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Camera, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("USE AS REFERENCE TO SCAN", fontWeight = FontWeight.Bold)
                }

                // Warning if fewer than 3 photos
                if (ref.photoPaths.size < 3) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A00)),
                        border = BorderStroke(1.dp, Color(0xFFFFB300))
                    ) {
                        Text(
                            text = "Warning: Fewer than 3 reference photos remaining. Reference will be auto-deleted if all photos are removed.",
                            color = Color(0xFFFFB300),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Captured Photos Header
                Text(
                    text = "Captured Photos (${ref.photoPaths.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MatrixGreen
                )

                // Grid of Photos (2 columns)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ref.photoPaths) { path ->
                        PhotoGridTile(
                            photoPath = path,
                            onDeletePhoto = {
                                try {
                                    val file = File(path)
                                    if (file.exists()) file.delete()
                                } catch (e: Exception) {
                                    Log.e("KHARA_REF", "Failed to delete photo file $path", e)
                                }

                                val remainingPaths = ref.photoPaths.filter { it != path }
                                val remainingEmbeddings = ref.embeddings.take(remainingPaths.size)

                                if (remainingPaths.isEmpty()) {
                                    try {
                                        val remainingRefs = ReferenceStore.getAll(context).filter { it.name != ref.name }
                                        ReferenceStore.clear(context)
                                        remainingRefs.forEach { ReferenceStore.add(context, it) }
                                        Log.d("KHARA_REF", "Reference $chipName deleted because zero photos remain")
                                    } catch (e: Exception) {
                                        Log.e("KHARA_REF", "Error deleting reference $chipName", e)
                                    }
                                    onBack()
                                } else {
                                    try {
                                        val allRefs = ReferenceStore.getAll(context).toMutableList()
                                        val idx = allRefs.indexOfFirst { it.name == ref.name }
                                        if (idx != -1) {
                                            allRefs[idx] = ReferenceItem(ref.name, remainingPaths, remainingEmbeddings, ref.createdAt)
                                            ReferenceStore.clear(context)
                                            allRefs.forEach { ReferenceStore.add(context, it) }
                                        }
                                        refreshTrigger++
                                    } catch (e: Exception) {
                                        Log.e("KHARA_REF", "Error updating reference $chipName", e)
                                    }
                                }
                            }
                        )
                    }
                }

                // Delete Reference Button at bottom
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3131)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DELETE REFERENCE", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }

    if (showDeleteDialog && ref != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Reference") },
            text = { Text("Are you sure you want to delete reference '${ref.name}' and all its ${ref.photoPaths.size} photo files?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        try {
                            ref.photoPaths.forEach { p ->
                                val f = File(p)
                                if (f.exists()) f.delete()
                            }
                        } catch (e: Exception) {
                            Log.e("KHARA_REF", "Error deleting reference photo files", e)
                        }
                        try {
                            val remainingRefs = ReferenceStore.getAll(context).filter { it.name != ref.name }
                            ReferenceStore.clear(context)
                            remainingRefs.forEach { ReferenceStore.add(context, it) }
                            Log.d("KHARA_REF", "Reference $chipName deleted successfully")
                        } catch (e: Exception) {
                            Log.e("KHARA_REF", "Error deleting reference from store", e)
                        }
                        onBack()
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF3131), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PhotoGridTile(
    photoPath: String,
    onDeletePhoto: () -> Unit
) {
    val bitmap = remember(photoPath) {
        try {
            val file = File(photoPath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(bitmap) {
        onDispose {
            try {
                bitmap?.recycle()
            } catch (e: Exception) {
                Log.e("KHARA_REF", "Error recycling bitmap", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MatrixGreen.copy(0.4f), RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E)),
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
                tint = TextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }

        IconButton(
            onClick = onDeletePhoto,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color.Black.copy(0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete Photo",
                tint = Color.Red,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
