package com.khara.prototype.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    private const val YOLO_URL = "https://github.com/john-rocky/LiteRT-Models/releases/download/v1/yolo11n.tflite"

    suspend fun ensureYoloModelDownloaded(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val yoloFile = File(modelsDir, "yolo11n.tflite")
            if (yoloFile.exists() && yoloFile.length() > 0) {
                return@withContext yoloFile
            }

            Log.d("KHARA_YOLO", "Downloading yolo11n.tflite from $YOLO_URL...")
            val url = URL(YOLO_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK || connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                connection.inputStream.use { input ->
                    FileOutputStream(yoloFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("KHARA_YOLO", "Successfully downloaded yolo11n.tflite (${yoloFile.length()} bytes)")
                return@withContext yoloFile
            } else {
                Log.e("KHARA_YOLO", "Failed to download yolo11n.tflite HTTP response: ${connection.responseCode}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error downloading YOLO model", e)
            return@withContext null
        }
    }
}
