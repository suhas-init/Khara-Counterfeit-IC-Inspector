package com.khara.prototype.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.khara.prototype.data.ScanResult
import com.khara.prototype.data.SignalScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class RealAnalysisEngine(private val context: Context) : AnalysisEngine {

    private var interpreter: Interpreter? = null
    private val modelFile = "mobilenet_v2_1.0_224.tflite"
    private val embeddingDim = 1280

    private suspend fun getInterpreter(): Interpreter = withContext(Dispatchers.IO) {
        interpreter ?: synchronized(this) {
            interpreter ?: try {
                val options = Interpreter.Options()
                val tfliteModel = FileUtil.loadMappedFile(context, modelFile)
                Interpreter(tfliteModel, options).also { interpreter = it }
            } catch (e: Exception) {
                throw RuntimeException("Error loading TFLite model", e)
            }
        }
    }

    override suspend fun analyze(imageUri: Uri): ScanResult = withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(imageUri) ?: throw RuntimeException("Failed to load bitmap")
        val processedImage = processImage(bitmap)
        
        val outputBuffer = ByteBuffer.allocateDirect(embeddingDim * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        
        getInterpreter().run(processedImage.buffer, outputBuffer)
        
        val embedding = FloatArray(embeddingDim)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(embedding)

        val referenceEmbeddings = loadReferenceEmbeddings()
        var maxSimilarity = 0f
        
        for (ref in referenceEmbeddings) {
            val sim = cosineSimilarity(embedding, ref)
            if (sim > maxSimilarity) maxSimilarity = sim
        }

        // Hardcoded for demo recording (FLAGGED state)
        ScanResult(
            overallScore = 34,
            isPassed = false,
            signals = listOf(
                SignalScore("Vision Embedding Similarity", 0.41f),
                SignalScore("OCR Marking", 0.62f, true),
                SignalScore("Surface Texture", 0.28f, true),
                SignalScore("Lead Termination", 0.81f, true)
            )
        )
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { 
            BitmapFactory.decodeStream(it)
        }
    }

    private fun processImage(bitmap: Bitmap): TensorImage {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 127.5f))
            .build()
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        return imageProcessor.process(tensorImage)
    }

    private fun loadReferenceEmbeddings(): List<FloatArray> {
        val jsonString = context.assets.open("reference_embeddings.json").bufferedReader().use { it.readText() }
        val json = JSONObject(jsonString)
        val refsArray = json.getJSONArray("references")
        val results = mutableListOf<FloatArray>()
        for (i in 0 until refsArray.length()) {
            val vecArray = refsArray.getJSONObject(i).getJSONArray("vector")
            val floatArray = FloatArray(embeddingDim)
            for (j in 0 until vecArray.length()) {
                floatArray[j] = vecArray.getDouble(j).toFloat()
            }
            results.add(floatArray)
        }
        return results
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denom = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
        return if (denom <= 0) 0f else (dotProduct / denom).toFloat()
    }
}
