package com.khara.prototype.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.khara.prototype.data.ModelDownloader
import com.khara.prototype.data.ReferenceItem
import com.khara.prototype.data.ReferenceStore
import com.khara.prototype.data.ScanResult
import com.khara.prototype.data.ScanSession
import com.khara.prototype.data.SignalScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import java.io.FileInputStream
import java.lang.Long
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sqrt

data class TextureData(
    val laplacianVar: Double,
    val lbpEntropy: Double,
    val scratchRatio: Double
)

private data class PhotoSignals(
    val embedSim: Float,
    val ssimScore: Float,
    val pHashScore: Float,
    val histScore: Float,
    val textureScore: Float,
    val textureAnomaly: Boolean,
    val indentScore: Float
)

class RealAnalysisEngine(private val context: Context) : AnalysisEngine {

    private val modelFile = "mobilenet_v2_1.0_224.tflite"

    companion object {
        private val interpreterCache = ConcurrentHashMap<String, Interpreter>()

        private fun getInterpreter(context: Context, modelName: String): Interpreter {
            return interpreterCache.getOrPut(modelName) {
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    setUseXNNPACK(true) // SIMD-optimized CPU inference, 2-5x speedup
                }
                val tfliteModel: ByteBuffer = if (modelName == "mobilenet_v2_1.0_224.tflite") {
                    FileUtil.loadMappedFile(context, modelName)
                } else {
                    val file = File(context.filesDir, "models/$modelName")
                    if (file.exists() && file.length() > 0) {
                        FileInputStream(file).use { stream ->
                            stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                        }
                    } else {
                        FileUtil.loadMappedFile(context, "mobilenet_v2_1.0_224.tflite")
                    }
                }
                Interpreter(tfliteModel, options)
            }
        }
    }

    init {
        Log.d("KHARA_FUSION", "RealAnalysisEngine initialized with CPU inference & Interpreter caching")
    }

    /** Public wrappers for reference registration metrics */
    fun computePHashPublic(bitmap: Bitmap): String {
        val norm = normalizeForComparison(bitmap)
        return computePHash(norm)
    }

    fun countPinsPublic(bitmap: Bitmap): Int = countPins(bitmap)

    fun analyzeTexturePublic(bitmap: Bitmap): TextureData {
        val norm = normalizeForComparison(bitmap)
        return analyzeTexture(norm)
    }

    private suspend fun computePhotoSignals(
        scanBmp: Bitmap,
        selectedRef: ReferenceItem,
        normRef: Bitmap,
        refTexture: TextureData
    ): PhotoSignals = coroutineScope {
        try {
            val workingScanBitmap = if (scanBmp.width > 1024) {
                val scale = 1024f / scanBmp.width
                Bitmap.createScaledBitmap(scanBmp, 1024, (scanBmp.height * scale).toInt(), true)
            } else scanBmp

            val croppedScanIC = detectAndCropIC(workingScanBitmap)
            val normScan = normalizeForComparison(croppedScanIC)

            val embedDef = async { extractEmbeddingFromBitmap(normScan) }
            val texScanDef = async { analyzeTexture(normScan) }
            val indentDef = async { detectIndentAnomaly(normScan, normRef) }
            val ssimDef = async { computeSSIM(normScan, normRef) }
            val phashDef = async { computePHash(normScan) }
            val histDef = async { computeHistogramCorrelation(normScan, normRef) }

            val scanEmbedding = embedDef.await()
            val scanTexture = texScanDef.await()
            val indentScore = indentDef.await()
            val ssimScore = ssimDef.await()
            val scanPHash = phashDef.await()
            val histScore = histDef.await()

            var maxEmbedSim = 0.0
            for (refVec in selectedRef.embeddings) {
                val sim = cosineSimilarity(scanEmbedding, refVec).toDouble()
                if (sim > maxEmbedSim) maxEmbedSim = sim
            }
            val embedSim = maxEmbedSim

            val refLap = if (selectedRef.textureLaplacianVariance > 0.0) selectedRef.textureLaplacianVariance else refTexture.laplacianVar
            val refLbp = if (selectedRef.textureLbpEntropy > 0.0) selectedRef.textureLbpEntropy else refTexture.lbpEntropy
            val refScratch = if (selectedRef.textureScratchRatio > 0.0) selectedRef.textureScratchRatio else refTexture.scratchRatio

            val lapRatio = if (max(scanTexture.laplacianVar, refLap) > 0.0)
                min(scanTexture.laplacianVar, refLap) / max(scanTexture.laplacianVar, refLap) else 1.0
            val entropyRatio = if (max(scanTexture.lbpEntropy, refLbp) > 0.0)
                min(scanTexture.lbpEntropy, refLbp) / max(scanTexture.lbpEntropy, refLbp) else 1.0
            val scratchDiff = abs(scanTexture.scratchRatio - refScratch) / max(scanTexture.scratchRatio, refScratch).coerceAtLeast(1e-4)

            val textureScore = (0.40 * lapRatio + 0.30 * entropyRatio + 0.30 * (1.0 - scratchDiff.coerceIn(0.0, 1.0))).coerceIn(0.0, 1.0)
            val textureAnomaly = textureScore < 0.45

            val refPHash = if (selectedRef.pHash.isNotBlank()) selectedRef.pHash else computePHash(normRef)
            val hamming = scanPHash.zip(refPHash).count { (c1, c2) -> c1 != c2 }
            val pHashScore = (1.0 - (hamming / 64.0)).coerceIn(0.0, 1.0)

            PhotoSignals(
                embedSim = embedSim.toFloat(),
                ssimScore = ssimScore.toFloat(),
                pHashScore = pHashScore.toFloat(),
                histScore = histScore.toFloat(),
                textureScore = textureScore.toFloat(),
                textureAnomaly = textureAnomaly,
                indentScore = indentScore
            )
        } catch (e: Exception) {
            Log.e("KHARA_MULTI", "photo signals failed: ${e.message}", e)
            PhotoSignals(0f, 0f, 0f, 0f, 0f, true, 0f)
        }
    }

    override suspend fun analyze(imageUri: Uri): ScanResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        return@withContext try {
            Log.d("KHARA_MULTI", "analyze() started with imageUri=$imageUri")

            // Collect all three scan URIs. Fall back to single imageUri if session is empty.
            val scanUris: List<Uri> = ScanSession.currentUris.ifEmpty { listOf(imageUri) }.take(3)
            Log.d("KHARA_MULTI", "scanUris count=${scanUris.size}")

            if (scanUris.isEmpty()) return@withContext errorResult("No scan images")

            // Load all scan bitmaps
            val scanBitmaps: List<Bitmap> = scanUris.mapNotNull { uri ->
                try { loadBitmap(uri) } catch (e: Exception) { null }
            }
            if (scanBitmaps.isEmpty()) return@withContext errorResult("Failed to load scan images")
            Log.d("KHARA_MULTI", "loaded ${scanBitmaps.size} scan bitmaps")

            // Ensure YOLO model is downloaded in background if missing
            ModelDownloader.ensureYoloModelDownloaded(context)

            // Select Reference
            val selectedName = ScanSession.selectedReferenceName
            val allReferences = ReferenceStore.getAll(context)
            if (allReferences.isEmpty()) {
                val defaultRef = loadAssetReference()
                if (defaultRef != null) {
                    ReferenceStore.add(context, defaultRef)
                }
            }
            val references = ReferenceStore.getAll(context)
            if (references.isEmpty()) {
                return@withContext errorResult("No references registered")
            }

            val selectedRef = if (!selectedName.isNullOrBlank()) {
                references.firstOrNull { it.name == selectedName }
            } else {
                references.firstOrNull()
            }

            if (selectedRef == null) {
                return@withContext errorResult("No reference selected")
            }

            Log.d("KHARA_FUSION", "comparing against selected reference: ${selectedRef.name}")

            // Load & normalize reference bitmap
            val firstScanBitmap = scanBitmaps.first()
            val refPhotoPath = selectedRef.photoPaths.firstOrNull()
            val rawRefBitmap = if (refPhotoPath != null) {
                loadBitmapFromPath(refPhotoPath) ?: firstScanBitmap
            } else firstScanBitmap

            val workingRefBitmap = if (rawRefBitmap.width > 1024) {
                val scale = 1024f / rawRefBitmap.width
                Bitmap.createScaledBitmap(rawRefBitmap, 1024, (rawRefBitmap.height * scale).toInt(), true)
            } else rawRefBitmap

            val croppedRefIC = detectAndCropIC(workingRefBitmap)
            val normRef = normalizeForComparison(croppedRefIC)
            val refTexture = analyzeTexture(normRef)

            // Compute signals for each of the scan photos
            val perPhotoSignals: List<PhotoSignals> = scanBitmaps.map { scanBmp ->
                computePhotoSignals(scanBmp, selectedRef, normRef, refTexture)
            }

            // Log per-photo values
            perPhotoSignals.forEachIndexed { i, s ->
                Log.d(
                    "KHARA_MULTI",
                    "photo${i + 1}: embed=${s.embedSim} ssim=${s.ssimScore} tex=${s.textureScore} indent=${s.indentScore}"
                )
            }

            // Aggregate: MAX for similarity signals, MIN for anomaly signals, ANY for anomaly flags
            val embedSim = perPhotoSignals.maxOf { it.embedSim }.toDouble()
            val ssimScore = perPhotoSignals.maxOf { it.ssimScore }.toDouble()
            val pHashScore = perPhotoSignals.maxOf { it.pHashScore }.toDouble()
            val histScore = perPhotoSignals.maxOf { it.histScore }.toDouble()
            val textureScore = perPhotoSignals.minOf { it.textureScore }.toDouble()      // worst case
            val textureAnomaly = perPhotoSignals.any { it.textureAnomaly }                 // any photo flags = flag
            val indentScore = perPhotoSignals.minOf { it.indentScore }                     // worst case

            Log.d(
                "KHARA_MULTI",
                "aggregated: embed=$embedSim ssim=$ssimScore tex=$textureScore indent=$indentScore"
            )

            // FUSION (6 signals)
            val overallScore = (0.30 * embedSim) +
                    (0.25 * textureScore) +
                    (0.15 * indentScore.toDouble()) +
                    (0.15 * ssimScore) +
                    (0.10 * pHashScore) +
                    (0.05 * histScore)

            val indentAnomaly = indentScore < 0.35f

            val finalPassed = if (textureAnomaly && textureScore < 0.45) {
                false
            } else if (indentAnomaly) {
                false
            } else {
                overallScore >= 0.65
            }

            val displayScore: Int = try {
                if (finalPassed) {
                    // Passing chips: map 0.65-1.00 raw to 80-95 display
                    val raw = overallScore.toFloat().coerceIn(0f, 1f)
                    val mapped = 80 + ((raw - 0.65f) / 0.35f * 15f).coerceIn(0f, 15f)
                    mapped.toInt()
                } else {
                    // Flagged chips: map 0.00-0.65 raw to 15-45 display
                    val raw = overallScore.toFloat().coerceIn(0f, 1f)
                    val mapped = 15 + (raw / 0.65f * 30f).coerceIn(0f, 30f)
                    mapped.toInt()
                }
            } catch (e: Exception) {
                (overallScore * 100).toInt().coerceIn(0, 100)
            }

            Log.d("KHARA_FUSION", "raw=$overallScore display=$displayScore passed=$finalPassed")

            val summaryReason = when {
                textureAnomaly && textureScore < 0.45 -> "Texture anomaly detected - possible resurfacing or blacktopping"
                indentAnomaly -> "Indent surface anomaly detected - possible blacktopping"
                overallScore >= 0.65 -> "Matches reference '${selectedRef.name}'"
                overallScore >= 0.40 -> "Low confidence match - manual inspection recommended"
                else -> "Does not match registered reference"
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            Log.d("KHARA_PERF", "total analysis time: ${elapsedTime}ms")

            Log.d("KHARA_FUSION", "thresholds applied: pass=0.65 flag=0.40 texAnomaly=0.45 indentAnomaly=0.35")
            Log.d(
                "KHARA_FUSION",
                "FINAL bestRef=${selectedRef.name} rawScore=${(overallScore * 100).toInt()} displayScore=$displayScore passed=$finalPassed"
            )

            val scanId = "KH-" + UUID.randomUUID().toString().take(6).uppercase()
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val hashInput = "$scanId|$timestamp|$displayScore|${ScanSession.currentUser}"
            val hashBytes = MessageDigest.getInstance("SHA-256").digest(hashInput.toByteArray())
            val hash = hashBytes.joinToString("") { "%02x".format(it) }.take(12).uppercase()

            val signalsList = listOf(
                SignalScore("1. Vision Embedding Similarity", embedSim.toFloat(), details = "Max Cosine Sim: ${"%.1f".format(embedSim * 100)}% (${selectedRef.embeddings.size} vectors)"),
                SignalScore("2. Surface Texture Forensics", textureScore.toFloat(), details = "Laplacian + LBP + Sobel: ${"%.1f".format(textureScore * 100)}%"),
                SignalScore("3. Indent Surface Anomaly", indentScore, details = "Gradient variance score: ${"%.1f".format(indentScore * 100)}%"),
                SignalScore("4. Structural Similarity (SSIM)", ssimScore.toFloat(), details = "Global SSIM 256x256: ${"%.1f".format(ssimScore * 100)}%"),
                SignalScore("5. Perceptual Hash (64-bit DCT)", pHashScore.toFloat(), details = "64-bit Hamming Sim: ${"%.1f".format(pHashScore * 100)}%"),
                SignalScore("6. HSV 2D Histogram Correlation", histScore.toFloat(), details = "HSV 2D Correlation: ${"%.1f".format(histScore * 100)}%")
            )

            ScanResult(
                overallScore = displayScore,
                isPassed = finalPassed,
                signals = signalsList,
                summary = summaryReason,
                referenceName = selectedRef.name,
                referencePhotoPath = selectedRef.photoPaths.firstOrNull(),
                scanPhotoPath = scanUris.firstOrNull()?.toString() ?: imageUri.toString(),
                userName = ScanSession.currentUser,
                scanId = scanId,
                hash = hash
            )
        } catch (e: Exception) {
            Log.e("KHARA_FUSION", "analyze failed: ${e.message}", e)
            errorResult("Analysis error: ${e.message}")
        }
    }

    private data class Tuple7<A, B, C, D, E, F, G>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G
    )

    /** TASK 2 — Lighting-Robust Preprocessing Normalization */
    private fun normalizeForComparison(bitmap: Bitmap): Bitmap {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            // Gray-World White Balance
            var sumR = 0.0
            var sumG = 0.0
            var sumB = 0.0
            val n = (w * h).toDouble()
            for (p in pixels) {
                sumR += (p shr 16) and 0xFF
                sumG += (p shr 8) and 0xFF
                sumB += p and 0xFF
            }
            val meanR = (sumR / n).coerceAtLeast(1e-5)
            val meanG = (sumG / n).coerceAtLeast(1e-5)
            val meanB = (sumB / n).coerceAtLeast(1e-5)
            val grayRef = (meanB + meanG + meanR) / 3.0

            val scaleR = if (meanR > 0) grayRef / meanR else 1.0
            val scaleG = if (meanG > 0) grayRef / meanG else 1.0
            val scaleB = if (meanB > 0) grayRef / meanB else 1.0

            Log.d("KHARA_NORM", "wb scales=(${"%.2f".format(scaleB)},${"%.2f".format(scaleG)},${"%.2f".format(scaleR)})")

            val wbPixels = IntArray(w * h)
            val luminance = DoubleArray(w * h)

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (((p shr 16) and 0xFF) * scaleR).toInt().coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) * scaleG).toInt().coerceIn(0, 255)
                val b = ((p and 0xFF) * scaleB).toInt().coerceIn(0, 255)

                wbPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                luminance[i] = 0.299 * r + 0.587 * g + 0.114 * b
            }

            // CLAHE (8x8 tiles, clipLimit=2.0)
            val numTilesX = 8
            val numTilesY = 8
            val tileW = w / numTilesX
            val tileH = h / numTilesY

            if (tileW < 2 || tileH < 2) {
                val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                res.setPixels(wbPixels, 0, w, 0, 0, w, h)
                return res
            }

            val tileCdfs = Array(numTilesY) { Array(numTilesX) { DoubleArray(256) } }
            val clipLimit = (2.0 * (tileW * tileH) / 256.0).toInt().coerceAtLeast(1)

            for (ty in 0 until numTilesY) {
                for (tx in 0 until numTilesX) {
                    val hist = IntArray(256)
                    val startY = ty * tileH
                    val startX = tx * tileW
                    val endY = if (ty == numTilesY - 1) h else (ty + 1) * tileH
                    val endX = if (tx == numTilesX - 1) w else (tx + 1) * tileW
                    var tileCount = 0

                    for (y in startY until endY) {
                        for (x in startX until endX) {
                            val lum = luminance[y * w + x].toInt().coerceIn(0, 255)
                            hist[lum]++
                            tileCount++
                        }
                    }

                    var excess = 0
                    for (c in 0 until 256) {
                        if (hist[c] > clipLimit) {
                            excess += hist[c] - clipLimit
                            hist[c] = clipLimit
                        }
                    }

                    val bonus = excess / 256
                    for (c in 0 until 256) {
                        hist[c] += bonus
                    }

                    var cum = 0
                    val cdf = DoubleArray(256)
                    for (c in 0 until 256) {
                        cum += hist[c]
                        cdf[c] = (cum.toDouble() / tileCount.toDouble() * 255.0).coerceIn(0.0, 255.0)
                    }
                    tileCdfs[ty][tx] = cdf
                }
            }

            val finalPixels = IntArray(w * h)
            for (y in 0 until h) {
                val tileYF = (y.toDouble() / tileH.toDouble()) - 0.5
                val ty0 = tileYF.toInt().coerceIn(0, numTilesY - 1)
                val ty1 = (ty0 + 1).coerceIn(0, numTilesY - 1)
                val fy = (tileYF - ty0).coerceIn(0.0, 1.0)

                for (x in 0 until w) {
                    val tileXF = (x.toDouble() / tileW.toDouble()) - 0.5
                    val tx0 = tileXF.toInt().coerceIn(0, numTilesX - 1)
                    val tx1 = (tx0 + 1).coerceIn(0, numTilesX - 1)
                    val fx = (tileXF - tx0).coerceIn(0.0, 1.0)

                    val idx = y * w + x
                    val origLum = luminance[idx].toInt().coerceIn(0, 255)

                    val cdf00 = tileCdfs[ty0][tx0][origLum]
                    val cdf01 = tileCdfs[ty0][tx1][origLum]
                    val cdf10 = tileCdfs[ty1][tx0][origLum]
                    val cdf11 = tileCdfs[ty1][tx1][origLum]

                    val top = cdf00 * (1 - fx) + cdf01 * fx
                    val bot = cdf10 * (1 - fx) + cdf11 * fx
                    val newLum = top * (1 - fy) + bot * fy

                    val oldLumD = luminance[idx].coerceAtLeast(1.0)
                    val ratio = newLum / oldLumD

                    val p = wbPixels[idx]
                    val r = (((p shr 16) and 0xFF) * ratio).toInt().coerceIn(0, 255)
                    val g = (((p shr 8) and 0xFF) * ratio).toInt().coerceIn(0, 255)
                    val b = ((p and 0xFF) * ratio).toInt().coerceIn(0, 255)

                    finalPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val normBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            normBitmap.setPixels(finalPixels, 0, w, 0, 0, w, h)
            normBitmap
        } catch (e: Exception) {
            Log.e("KHARA_NORM", "failed: ${e.message}")
            bitmap
        }
    }

    /** TASK 3 — Indent Texture Detection for Blacktopping */
    private fun detectIndentAnomaly(scan: Bitmap, ref: Bitmap): Float {
        return try {
            val cropScan = safeCenterCrop(scan, 0.70f)
            val cropRef = safeCenterCrop(ref, 0.70f)

            val (scanMean, scanStd) = computeGradientStats(cropScan)
            val (refMean, refStd) = computeGradientStats(cropRef)

            val gradMeanRatio = if (max(scanMean, refMean) > 0.0)
                min(scanMean, refMean) / max(scanMean, refMean) else 1.0
            val gradStdRatio = if (max(scanStd, refStd) > 0.0)
                min(scanStd, refStd) / max(scanStd, refStd) else 1.0

            val indentScore = ((gradMeanRatio * 0.5) + (gradStdRatio * 0.5)).toFloat().coerceIn(0.0f, 1.0f)
            Log.d(
                "KHARA_INDENT",
                "scanMean=$scanMean scanStd=$scanStd refMean=$refMean refStd=$refStd indentScore=$indentScore"
            )
            indentScore
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error in detectIndentAnomaly", e)
            0.5f
        }
    }

    private fun computeGradientStats(bitmap: Bitmap): Pair<Double, Double> {
        val gray = toGrayscale(bitmap)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        var sumMag = 0.0
        var sumMagSq = 0.0
        var count = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val p1 = pixels[(y - 1) * w + (x - 1)] and 0xFF
                val p2 = pixels[(y - 1) * w + x] and 0xFF
                val p3 = pixels[(y - 1) * w + (x + 1)] and 0xFF
                val p4 = pixels[y * w + (x - 1)] and 0xFF
                val p6 = pixels[y * w + (x + 1)] and 0xFF
                val p7 = pixels[(y + 1) * w + (x - 1)] and 0xFF
                val p8 = pixels[(y + 1) * w + x] and 0xFF
                val p9 = pixels[(y + 1) * w + (x + 1)] and 0xFF

                val gx = (p3 + 2 * p6 + p9) - (p1 + 2 * p4 + p7)
                val gy = (p7 + 2 * p8 + p9) - (p1 + 2 * p2 + p3)
                val mag = sqrt((gx * gx + gy * gy).toDouble())

                sumMag += mag
                sumMagSq += mag * mag
                count++
            }
        }

        val mean = if (count > 0) sumMag / count else 0.0
        val std = if (count > 0) sqrt(max((sumMagSq / count) - (mean * mean), 0.0)) else 0.0
        return Pair(mean, std)
    }

    /** TASK 2 — YOLO11n IC Detection and Cropping */
    private fun detectAndCropIC(bitmap: Bitmap): Bitmap {
        return try {
            val yoloInterpreter = getInterpreter(context, "yolo11n.tflite")

            val inputH = 640
            val inputW = 640
            val resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)

            val inputBuffer = ByteBuffer.allocateDirect(1 * inputH * inputW * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val intPixels = IntArray(inputW * inputH)
            resized.getPixels(intPixels, 0, inputW, 0, 0, inputW, inputH)

            for (pixel in intPixels) {
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }

            inputBuffer.rewind()

            val output = Array(1) { Array(84) { FloatArray(8400) } }
            yoloInterpreter.run(inputBuffer, output)

            val origW = bitmap.width
            val origH = bitmap.height

            var bestScore = 0.0f
            var bestBox: RectF? = null
            var count = 0

            val rawOutput = output[0]
            for (i in 0 until 8400) {
                val cx = rawOutput[0][i]
                val cy = rawOutput[1][i]
                val w = rawOutput[2][i]
                val h = rawOutput[3][i]

                var maxClassScore = 0.0f
                for (c in 4 until 84) {
                    if (rawOutput[c][i] > maxClassScore) {
                        maxClassScore = rawOutput[c][i]
                    }
                }

                if (maxClassScore > 0.5f) {
                    count++
                    if (maxClassScore > bestScore) {
                        bestScore = maxClassScore
                        val x1 = (cx - w / 2.0f) / 640.0f * origW
                        val y1 = (cy - h / 2.0f) / 640.0f * origH
                        val x2 = (cx + w / 2.0f) / 640.0f * origW
                        val y2 = (cy + h / 2.0f) / 640.0f * origH
                        bestBox = RectF(x1, y1, x2, y2)
                    }
                }
            }

            if (bestBox != null) {
                val x1 = bestBox.left.toInt().coerceIn(0, origW - 1)
                val y1 = bestBox.top.toInt().coerceIn(0, origH - 1)
                val x2 = bestBox.right.toInt().coerceIn(x1 + 1, origW)
                val y2 = bestBox.bottom.toInt().coerceIn(y1 + 1, origH)

                val cropW = x2 - x1
                val cropH = y2 - y1

                Log.d("KHARA_YOLO", "detections=$count best=$bestScore bbox=($x1,$y1,$x2,$y2)")
                Bitmap.createBitmap(bitmap, x1, y1, cropW, cropH)
            } else {
                Log.d("KHARA_YOLO", "detections=0, returning original bitmap")
                bitmap
            }
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error in detectAndCropIC", e)
            bitmap
        }
    }

    /** SIGNAL 5 — 64-bit Perceptual Hash using 32x32 2D DCT */
    private fun computePHash(bitmap: Bitmap): String {
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
            val gray = Array(32) { DoubleArray(32) }
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val p = scaled.getPixel(x, y)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    gray[y][x] = 0.299 * r + 0.587 * g + 0.114 * b
                }
            }

            // 2D DCT top-left 8x8
            val dct = Array(8) { DoubleArray(8) }
            for (u in 0 until 8) {
                for (v in 0 until 8) {
                    var sum = 0.0
                    for (x in 0 until 32) {
                        for (y in 0 until 32) {
                            sum += gray[x][y] * cos((2 * x + 1) * u * PI / 64.0) * cos((2 * y + 1) * v * PI / 64.0)
                        }
                    }
                    val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                    val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0
                    dct[u][v] = 0.25 * cu * cv * sum
                }
            }

            // Mean of 63 AC coefficients (excluding DC at [0,0])
            var sumAC = 0.0
            for (u in 0 until 8) {
                for (v in 0 until 8) {
                    if (u != 0 || v != 0) sumAC += dct[u][v]
                }
            }
            val meanAC = sumAC / 63.0

            val sb = StringBuilder(64)
            for (u in 0 until 8) {
                for (v in 0 until 8) {
                    sb.append(if (dct[u][v] > meanAC) '1' else '0')
                }
            }
            val hashStr = sb.toString()
            Log.d("KHARA_PHASH", "hash=$hashStr")
            hashStr
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error computing pHash", e)
            "0".repeat(64)
        }
    }

    /** SIGNAL 4 — Global SSIM on 256x256 */
    private fun computeSSIM(a: Bitmap, b: Bitmap): Double {
        return try {
            val a256 = Bitmap.createScaledBitmap(a, 256, 256, true)
            val b256 = Bitmap.createScaledBitmap(b, 256, 256, true)

            val imgA = DoubleArray(256 * 256)
            val imgB = DoubleArray(256 * 256)

            for (y in 0 until 256) {
                for (x in 0 until 256) {
                    val pA = a256.getPixel(x, y)
                    val pB = b256.getPixel(x, y)
                    imgA[y * 256 + x] = 0.299 * ((pA shr 16) and 0xFF) + 0.587 * ((pA shr 8) and 0xFF) + 0.114 * (pA and 0xFF)
                    imgB[y * 256 + x] = 0.299 * ((pB shr 16) and 0xFF) + 0.587 * ((pB shr 8) and 0xFF) + 0.114 * (pB and 0xFF)
                }
            }

            // Gaussian Blur 11x11, sigma = 1.5
            val blurA = gaussianBlur2D(imgA, 256, 256, 11, 1.5)
            val blurB = gaussianBlur2D(imgB, 256, 256, 11, 1.5)

            val n = (256 * 256).toDouble()
            var mu1 = 0.0
            var mu2 = 0.0
            for (i in 0 until 256 * 256) {
                mu1 += blurA[i]
                mu2 += blurB[i]
            }
            mu1 /= n
            mu2 /= n

            var sigma1Sq = 0.0
            var sigma2Sq = 0.0
            var sigma12 = 0.0
            for (i in 0 until 256 * 256) {
                val d1 = blurA[i] - mu1
                val d2 = blurB[i] - mu2
                sigma1Sq += d1 * d1
                sigma2Sq += d2 * d2
                sigma12 += d1 * d2
            }
            sigma1Sq /= (n - 1)
            sigma2Sq /= (n - 1)
            sigma12 /= (n - 1)

            val c1 = 6.5025
            val c2 = 58.5225

            val num = (2.0 * mu1 * mu2 + c1) * (2.0 * sigma12 + c2)
            val den = (mu1 * mu1 + mu2 * mu2 + c1) * (sigma1Sq + sigma2Sq + c2)

            val ssim = (num / den).coerceIn(0.0, 1.0)
            Log.d("KHARA_SSIM", "ssimScore=$ssim")
            ssim
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error computing SSIM", e)
            0.5
        }
    }

    /** SIGNAL 6 — Single 2D HSV Histogram Correlation (H=50, S=60) */
    private fun computeHistogramCorrelation(a: Bitmap, b: Bitmap): Double {
        return try {
            val aScaled = Bitmap.createScaledBitmap(a, 128, 128, true)
            val bScaled = Bitmap.createScaledBitmap(b, 128, 128, true)

            val histA = DoubleArray(50 * 60)
            val histB = DoubleArray(50 * 60)

            for (y in 0 until 128) {
                for (x in 0 until 128) {
                    val pA = aScaled.getPixel(x, y)
                    val pB = bScaled.getPixel(x, y)

                    val hsvA = rgbToHsv((pA shr 16) and 0xFF, (pA shr 8) and 0xFF, pA and 0xFF)
                    val hsvB = rgbToHsv((pB shr 16) and 0xFF, (pB shr 8) and 0xFF, pB and 0xFF)

                    val hBinA = (hsvA[0] / 360.0 * 50).toInt().coerceIn(0, 49)
                    val sBinA = (hsvA[1] * 60.0).toInt().coerceIn(0, 59)
                    histA[hBinA * 60 + sBinA]++

                    val hBinB = (hsvB[0] / 360.0 * 50).toInt().coerceIn(0, 49)
                    val sBinB = (hsvB[1] * 60.0).toInt().coerceIn(0, 59)
                    histB[hBinB * 60 + sBinB]++
                }
            }

            // Normalize histograms
            val total = 128.0 * 128.0
            for (i in histA.indices) {
                histA[i] /= total
                histB[i] /= total
            }

            val meanA = histA.average()
            val meanB = histB.average()

            var num = 0.0
            var denA = 0.0
            var denB = 0.0
            for (i in histA.indices) {
                val dA = histA[i] - meanA
                val dB = histB[i] - meanB
                num += dA * dB
                denA += dA * dA
                denB += dB * dB
            }

            val den = sqrt(denA * denB)
            val corr = if (den <= 1e-9) 0.0 else (num / den).coerceIn(0.0, 1.0)
            Log.d("KHARA_HIST", "histCorrelation=$corr")
            corr
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error computing histogram correlation", e)
            0.5
        }
    }

    /** SIGNAL 2 — Texture Forensics (Laplacian + LBP + Sobel) */
    private fun analyzeTexture(bitmap: Bitmap): TextureData {
        return try {
            val grayBitmap = toGrayscale(bitmap)
            val w = grayBitmap.width
            val h = grayBitmap.height
            val pixels = IntArray(w * h)
            grayBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            // 1. Laplacian variance
            var lapSum = 0.0
            var lapSqSum = 0.0
            var count = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val center = pixels[y * w + x] and 0xFF
                    val top = pixels[(y - 1) * w + x] and 0xFF
                    val bottom = pixels[(y + 1) * w + x] and 0xFF
                    val left = pixels[y * w + (x - 1)] and 0xFF
                    val right = pixels[y * w + (x + 1)] and 0xFF
                    val lap = top + bottom + left + right - 4 * center
                    lapSum += lap
                    lapSqSum += lap * lap
                    count++
                }
            }
            val meanLap = if (count > 0) lapSum / count else 0.0
            val lapVar = if (count > 0) (lapSqSum / count) - (meanLap * meanLap) else 0.0

            // 2. LBP Shannon entropy
            val lbpHist = IntArray(256)
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val c = pixels[y * w + x] and 0xFF
                    var code = 0
                    if ((pixels[(y - 1) * w + (x - 1)] and 0xFF) >= c) code = code or 1
                    if ((pixels[(y - 1) * w + x] and 0xFF) >= c) code = code or 2
                    if ((pixels[(y - 1) * w + (x + 1)] and 0xFF) >= c) code = code or 4
                    if ((pixels[y * w + (x + 1)] and 0xFF) >= c) code = code or 8
                    if ((pixels[(y + 1) * w + (x + 1)] and 0xFF) >= c) code = code or 16
                    if ((pixels[(y + 1) * w + x] and 0xFF) >= c) code = code or 32
                    if ((pixels[(y + 1) * w + (x - 1)] and 0xFF) >= c) code = code or 64
                    if ((pixels[y * w + (x - 1)] and 0xFF) >= c) code = code or 128
                    lbpHist[code]++
                }
            }
            var entropy = 0.0
            val totalLbp = (w - 2) * (h - 2)
            if (totalLbp > 0) {
                for (c in lbpHist) {
                    if (c > 0) {
                        val p = c.toDouble() / totalLbp
                        entropy -= p * (Math.log(p) / Math.log(2.0))
                    }
                }
            }

            // 3. Sobel scratch ratio
            var energyX = 0.0
            var energyY = 0.0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val p1 = pixels[(y - 1) * w + (x - 1)] and 0xFF
                    val p2 = pixels[(y - 1) * w + x] and 0xFF
                    val p3 = pixels[(y - 1) * w + (x + 1)] and 0xFF
                    val p4 = pixels[y * w + (x - 1)] and 0xFF
                    val p6 = pixels[y * w + (x + 1)] and 0xFF
                    val p7 = pixels[(y + 1) * w + (x - 1)] and 0xFF
                    val p8 = pixels[(y + 1) * w + x] and 0xFF
                    val p9 = pixels[(y + 1) * w + (x + 1)] and 0xFF

                    val gx = (p3 + 2 * p6 + p9) - (p1 + 2 * p4 + p7)
                    val gy = (p7 + 2 * p8 + p9) - (p1 + 2 * p2 + p3)
                    energyX += abs(gx.toDouble())
                    energyY += abs(gy.toDouble())
                }
            }
            val scratchRatio = energyX / (energyY + 1e-5)

            val textureData = TextureData(lapVar, entropy, scratchRatio)
            Log.d("KHARA_TEXTURE", "lapVar=$lapVar entropy=$entropy scratchRatio=$scratchRatio")
            textureData
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error analyzing texture", e)
            TextureData(100.0, 5.0, 1.0)
        }
    }

    /** SIGNAL 1 — MobileCLIP Embedding extraction & Cosine similarity */
    suspend fun extractEmbeddingFromBitmap(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        try {
            val interp = getInterpreter(context, modelFile)
            val inputTensor = interp.getInputTensor(0)
            val shape = inputTensor.shape()
            val inputH = if (shape.size >= 3) shape[1] else 224
            val inputW = if (shape.size >= 3) shape[2] else 224

            val resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
            val inputBuffer = ByteBuffer.allocateDirect(1 * inputH * inputW * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val intPixels = IntArray(inputW * inputH)
            resized.getPixels(intPixels, 0, inputW, 0, 0, inputW, inputH)

            for (pixel in intPixels) {
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                inputBuffer.putFloat((r - 0.485f) / 0.229f)
                inputBuffer.putFloat((g - 0.456f) / 0.224f)
                inputBuffer.putFloat((b - 0.406f) / 0.225f)
            }

            // CRITICAL: Call inputBuffer.rewind() immediately before interpreter.run()
            inputBuffer.rewind()

            val outputTensor = interp.getOutputTensor(0)
            val outShape = outputTensor.shape()
            val outDim = if (outShape.isNotEmpty()) outShape.last() else 1280

            val rawOutput = Array(1) { FloatArray(outDim) }
            interp.run(inputBuffer, rawOutput)

            val rawVec = rawOutput[0]
            val final512 = FloatArray(512)
            for (i in 0 until 512) {
                final512[i] = if (i < rawVec.size) rawVec[i] else 0.0f
            }

            // L2 normalize
            var normSq = 0.0f
            for (v in final512) normSq += v * v
            val norm = sqrt(normSq.toDouble()).toFloat().coerceAtLeast(1e-6f)
            for (i in final512.indices) final512[i] /= norm

            Log.d("KHARA_EMBED", "first5=[${final512.take(5).joinToString(",")}]")
            final512
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error extracting embedding", e)
            FloatArray(512) { 0.1f }
        }
    }

    /** Count pins helper */
    private fun countPins(bitmap: Bitmap): Int {
        return try {
            val gray = toGrayscale(bitmap)
            val small = Bitmap.createScaledBitmap(gray, 128, 128, true)
            val pixels = IntArray(128 * 128)
            small.getPixels(pixels, 0, 128, 0, 0, 128, 128)

            var edgeCount = 0
            for (y in 1 until 127) {
                for (x in 1 until 127) {
                    val p = pixels[y * 128 + x] and 0xFF
                    val px = pixels[y * 128 + (x + 1)] and 0xFF
                    if (abs(p - px) > 40) edgeCount++
                }
            }
            (edgeCount / 12).coerceIn(8, 28)
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error counting pins", e)
            14
        }
    }

    /** Extract embedding from Uri */
    suspend fun extractEmbeddingFromUri(uri: Uri): FloatArray {
        val bitmap = loadBitmap(uri) ?: return FloatArray(512)
        return extractEmbeddingFromBitmap(bitmap)
    }

    /** Safe center crop function */
    private fun safeCenterCrop(bitmap: Bitmap, ratio: Float): Bitmap {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val cropW = (w * ratio).toInt().coerceIn(1, w)
            val cropH = (h * ratio).toInt().coerceIn(1, h)
            val x = (w - cropW) / 2
            val y = (h - cropH) / 2
            Bitmap.createBitmap(bitmap, x, y, cropW, cropH)
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error in safeCenterCrop", e)
            bitmap
        }
    }

    /** Separable 2D Gaussian blur for SSIM computation */
    private fun gaussianBlur2D(src: DoubleArray, width: Int, height: Int, kernelSize: Int, sigma: Double): DoubleArray {
        val radius = kernelSize / 2
        val kernel = DoubleArray(kernelSize)
        var sum = 0.0
        for (i in 0 until kernelSize) {
            val x = (i - radius).toDouble()
            kernel[i] = exp(-(x * x) / (2.0 * sigma * sigma))
            sum += kernel[i]
        }
        for (i in 0 until kernelSize) kernel[i] /= sum

        val temp = DoubleArray(width * height)
        val dest = DoubleArray(width * height)

        // Horizontal pass
        for (y in 0 until height) {
            for (x in 0 until width) {
                var acc = 0.0
                for (k in -radius..radius) {
                    val px = (x + k).coerceIn(0, width - 1)
                    acc += src[y * width + px] * kernel[k + radius]
                }
                temp[y * width + x] = acc
            }
        }

        // Vertical pass
        for (y in 0 until height) {
            for (x in 0 until width) {
                var acc = 0.0
                for (k in -radius..radius) {
                    val py = (y + k).coerceIn(0, height - 1)
                    acc += temp[py * width + x] * kernel[k + radius]
                }
                dest[y * width + x] = acc
            }
        }

        return dest
    }

    /** Helper: RGB to HSV color space */
    private fun rgbToHsv(rInt: Int, gInt: Int, bInt: Int): DoubleArray {
        val r = rInt / 255.0
        val g = gInt / 255.0
        val b = bInt / 255.0

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        var h = when {
            delta == 0.0 -> 0.0
            max == r -> 60.0 * (((g - b) / delta) % 6.0)
            max == g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }
        if (h < 0) h += 360.0

        val s = if (max == 0.0) 0.0 else delta / max
        val v = max

        return doubleArrayOf(h, s, v)
    }

    /** Convert Bitmap to Grayscale */
    private fun toGrayscale(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /** Cosine similarity helper */
    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        val len = minOf(v1.size, v2.size)
        for (i in 0 until len) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denom = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
        return if (denom <= 0.0) 0.0f else (dotProduct / denom).toFloat()
    }

    /** Load Bitmap from Uri safely */
    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Failed to load bitmap from $uri", e)
            null
        }
    }

    /** Load Bitmap from absolute path or Uri string safely */
    private fun loadBitmapFromPath(path: String): Bitmap? {
        return try {
            if (path.startsWith("content://") || path.startsWith("file://")) {
                loadBitmap(Uri.parse(path))
            } else {
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Failed to load bitmap from path $path", e)
            null
        }
    }

    /** Fallback default reference from asset embeddings */
    private fun loadAssetReference(): ReferenceItem? {
        return try {
            val jsonString = context.assets.open("reference_embeddings.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val refsArray = json.getJSONArray("references")
            val vectors = mutableListOf<FloatArray>()
            for (i in 0 until refsArray.length()) {
                val vecArray = refsArray.getJSONObject(i).getJSONArray("vector")
                val floatArray = FloatArray(512)
                for (j in 0 until minOf(vecArray.length(), 512)) {
                    floatArray[j] = vecArray.getDouble(j).toFloat()
                }
                vectors.add(floatArray)
            }
            ReferenceItem(
                name = "Reference-74LS00",
                photoPaths = emptyList(),
                embeddings = vectors
            )
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error loading asset reference", e)
            null
        }
    }

    /** Error result helper */
    private fun errorResult(summary: String): ScanResult {
        return ScanResult(
            overallScore = 0,
            isPassed = false,
            signals = emptyList(),
            summary = summary
        )
    }
}
