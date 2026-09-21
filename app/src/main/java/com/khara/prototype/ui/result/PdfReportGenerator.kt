package com.khara.prototype.ui.result

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.khara.prototype.data.ScanResult
import java.io.File
import java.io.FileOutputStream

object PdfReportGenerator {
    fun generate(
        context: Context,
        result: ScanResult,
        imageUri: Uri?,
        scanId: String,
        timestamp: String,
        hash: String
    ): Uri? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Background
        paint.color = Color.parseColor("#0A0A0A")
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        // Header
        paint.color = Color.parseColor("#00FF41")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        canvas.drawText("KHARA", 40f, 50f, paint)
        
        paint.color = Color.parseColor("#808080")
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Offline Counterfeit Component Triage", 40f, 70f, paint)

        paint.color = Color.parseColor("#00FF41")
        paint.textSize = 12f
        canvas.drawText("SCAN REPORT", 450f, 50f, paint)
        paint.color = Color.parseColor("#808080")
        paint.textSize = 10f
        canvas.drawText("ID: $scanId", 450f, 70f, paint)

        // Divider
        paint.color = Color.parseColor("#00FF41")
        paint.alpha = 102 // 40%
        canvas.drawLine(40f, 90f, 555f, 90f, paint)
        paint.alpha = 255

        // Verdict
        paint.color = Color.parseColor("#808080")
        paint.textSize = 10f
        canvas.drawText("TRUST SCORE", 40f, 130f, paint)
        
        paint.color = if (result.isPassed) Color.parseColor("#00FF41") else Color.parseColor("#FF3131")
        paint.textSize = 56f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${result.overallScore}%", 40f, 190f, paint)

        // Chip
        paint.style = Paint.Style.FILL
        val chipRect = RectF(400f, 140f, 520f, 180f)
        canvas.drawRoundRect(chipRect, 20f, 20f, paint)
        paint.color = Color.BLACK
        paint.textSize = 14f
        val label = if (result.isPassed) "PASS" else "FLAGGED"
        canvas.drawText(label, 430f, 165f, paint)

        // Summary
        paint.color = Color.parseColor("#808080")
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT
        val summary = if (result.isPassed) "All signals within expected range. Component appears genuine." else "Texture and visual embedding deviate from references. Possible sign of remarking."
        canvas.drawText(summary, 40f, 210f, paint)

        // Image
        imageUri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 300, 200, true)
                canvas.drawBitmap(scaledBitmap, 147.5f, 240f, null)
                
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = Color.parseColor("#00FF41")
                paint.alpha = 76 // 30%
                canvas.drawRect(147.5f, 240f, 447.5f, 440f, paint)
                paint.alpha = 255

                if (!result.isPassed) {
                    paint.color = Color.parseColor("#FF3131")
                    paint.alpha = 64 // 25%
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(207.5f, 280f, 387.5f, 400f, paint)
                    paint.alpha = 255
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawRect(207.5f, 280f, 387.5f, 400f, paint)
                } else {
                    paint.color = Color.parseColor("#00FF41")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawRect(147.5f, 240f, 447.5f, 440f, paint)
                }
            } catch (e: Exception) {}
        }

        // Signals
        var y = 500f
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#00FF41")
        paint.textSize = 12f
        canvas.drawText("SIGNAL BREAKDOWN", 40f, y, paint)
        y += 30f

        result.signals.forEach { signal ->
            paint.color = Color.WHITE
            paint.textSize = 11f
            canvas.drawText(signal.label, 40f, y, paint)
            paint.color = if (signal.score > 0.85f) Color.parseColor("#00FF41") else Color.parseColor("#FF3131")
            canvas.drawText("${(signal.score * 100).toInt()}%", 500f, y, paint)
            y += 10f
            paint.color = Color.DKGRAY
            canvas.drawRect(40f, y, 240f, y + 4f, paint)
            paint.color = if (signal.score > 0.85f) Color.parseColor("#00FF41") else Color.parseColor("#FF3131")
            canvas.drawRect(40f, y, 40f + (200f * signal.score), y + 4f, paint)
            y += 30f
        }

        // Audit
        y = 720f
        paint.color = Color.parseColor("#00FF41")
        paint.textSize = 12f
        canvas.drawText("AUDIT TRAIL", 40f, y, paint)
        y += 20f
        paint.color = Color.parseColor("#808080")
        paint.textSize = 10f
        canvas.drawText("Scan ID: $scanId", 40f, y, paint)
        y += 15f
        canvas.drawText("Timestamp: $timestamp", 40f, y, paint)
        y += 15f
        canvas.drawText("SHA-256: ${hash.take(24)}...", 40f, y, paint)
        y += 15f
        paint.color = Color.parseColor("#00FF41")
        canvas.drawText("Generated on-device. No network calls.", 40f, y, paint)

        // Footer
        paint.color = Color.parseColor("#00FF41")
        paint.alpha = 102
        canvas.drawLine(40f, 810f, 555f, 810f, paint)
        paint.alpha = 255
        paint.color = Color.parseColor("#808080")
        paint.textSize = 9f
        canvas.drawText("Khara · Offline · On-device · Tamper-evident · iQOO Hackathon 2026", 150f, 830f, paint)

        pdfDocument.finishPage(page)

        val fileName = "Khara_$scanId.pdf"
        var finalUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        pdfDocument.writeTo(os)
                    }
                    finalUri = it
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { fos ->
                    pdfDocument.writeTo(fos)
                }
                finalUri = Uri.fromFile(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
        
        return finalUri
    }
}
