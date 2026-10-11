package com.khara.prototype.ui.result

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
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

        val actualScanId = result.scanId ?: scanId
        val actualHash = result.hash ?: hash

        try {
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
            canvas.drawText("ID: $actualScanId", 450f, 70f, paint)

            // Divider
            paint.color = Color.parseColor("#00FF41")
            paint.alpha = 102 // 40%
            canvas.drawLine(40f, 90f, 555f, 90f, paint)
            paint.alpha = 255

            // Verdict
            paint.color = Color.parseColor("#808080")
            paint.textSize = 10f
            canvas.drawText("TRUST SCORE", 40f, 125f, paint)

            paint.color = if (result.isPassed) Color.parseColor("#00FF41") else Color.parseColor("#FF3131")
            paint.textSize = 52f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${result.overallScore}%", 40f, 175f, paint)

            // Chip
            paint.style = Paint.Style.FILL
            val chipRect = RectF(430f, 130f, 550f, 170f)
            canvas.drawRoundRect(chipRect, 20f, 20f, paint)
            paint.color = Color.BLACK
            paint.textSize = 14f
            val label = if (result.isPassed) "PASS" else "FLAGGED"
            canvas.drawText(label, 455f, 155f, paint)

            // Summary
            paint.color = Color.parseColor("#808080")
            paint.textSize = 10f
            paint.typeface = Typeface.DEFAULT
            val summary = result.summary.ifBlank {
                if (result.isPassed) "All signals within expected range. Component appears genuine."
                else "Texture and visual embedding deviate from reference."
            }
            canvas.drawText(summary, 40f, 195f, paint)

            // Side-by-side Images (Scan on left, Reference on right, 150pt tall)
            // Left: Scan Image
            imageUri?.let { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            val scaled = Bitmap.createScaledBitmap(bmp, 235, 150, true)
                            canvas.drawBitmap(scaled, 40f, 215f, null)

                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 1f
                            paint.color = if (result.isPassed) Color.parseColor("#00FF41") else Color.parseColor("#FF3131")
                            canvas.drawRect(40f, 215f, 275f, 365f, paint)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KHARA_PDF", "Error drawing scan image", e)
                }
            }

            // Right: Reference Image
            result.referencePhotoPath?.let { refPath ->
                try {
                    val refFile = File(refPath)
                    if (refFile.exists()) {
                        val bmp = BitmapFactory.decodeFile(refFile.absolutePath)
                        if (bmp != null) {
                            val scaled = Bitmap.createScaledBitmap(bmp, 235, 150, true)
                            canvas.drawBitmap(scaled, 290f, 215f, null)

                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 1f
                            paint.color = Color.parseColor("#00FF41")
                            canvas.drawRect(290f, 215f, 525f, 365f, paint)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KHARA_PDF", "Error drawing reference image", e)
                }
            }

            // Signals Section
            var y = 400f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#00FF41")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("SIGNAL BREAKDOWN", 40f, y, paint)
            y += 25f

            result.signals.forEach { signal ->
                paint.color = Color.WHITE
                paint.textSize = 10f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText(signal.label, 40f, y, paint)

                paint.color = if (signal.score >= 0.70f) Color.parseColor("#00FF41") else Color.parseColor("#FF3131")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("${(signal.score * 100).toInt()}%", 510f, y, paint)

                y += 8f
                paint.style = Paint.Style.FILL
                paint.color = Color.DKGRAY
                canvas.drawRect(40f, y, 240f, y + 3f, paint)
                paint.color = if (signal.score >= 0.70f) Color.parseColor("#00FF41") else Color.parseColor("#FF3131")
                canvas.drawRect(40f, y, 40f + (200f * signal.score), y + 3f, paint)
                y += 22f
            }

            // Audit Trail Section
            y = 650f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#00FF41")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("AUDIT TRAIL", 40f, y, paint)

            y += 20f
            paint.color = Color.parseColor("#808080")
            paint.textSize = 10f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("Scan ID: $actualScanId", 40f, y, paint)

            y += 15f
            canvas.drawText("Operator: ${result.userName}", 40f, y, paint)

            y += 15f
            canvas.drawText("Timestamp: $timestamp", 40f, y, paint)

            y += 15f
            canvas.drawText("SHA-256: ${actualHash.take(16)}...", 40f, y, paint)

            y += 15f
            paint.color = Color.parseColor("#00FF41")
            canvas.drawText("Generated on-device. Zero network dependencies.", 40f, y, paint)

            // QR Code in audit trail (bottom right)
            try {
                val writer = MultiFormatWriter()
                val matrix: BitMatrix = writer.encode(actualScanId, BarcodeFormat.QR_CODE, 100, 100)
                val qrBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
                for (qx in 0 until 100) {
                    for (qy in 0 until 100) {
                        qrBmp.setPixel(qx, qy, if (matrix.get(qx, qy)) Color.BLACK else Color.WHITE)
                    }
                }
                canvas.drawBitmap(qrBmp, 430f, 650f, null)
            } catch (e: Exception) {
                Log.e("KHARA_PDF", "Error generating QR code", e)
            }

            // Verified Badge in Footer
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#121212")
            val badgeRect = RectF(40f, 760f, 555f, 790f)
            canvas.drawRoundRect(badgeRect, 8f, 8f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.parseColor("#00FF41")
            canvas.drawRoundRect(badgeRect, 8f, 8f, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("✓ Verified by Khara", 52f, 779f, paint)

            paint.color = Color.parseColor("#808080")
            paint.textSize = 9f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("Hash: ${actualHash.take(16)}", 380f, 779f, paint)

            // Footer
            paint.color = Color.parseColor("#00FF41")
            paint.alpha = 102
            canvas.drawLine(40f, 805f, 555f, 805f, paint)
            paint.alpha = 255

            paint.color = Color.parseColor("#808080")
            paint.textSize = 9f
            canvas.drawText("Khara · Offline · On-device · Tamper-evident · iQOO Hackathon 2026", 130f, 825f, paint)

            Log.d("KHARA_PDF", "PDF page rendered successfully")
        } catch (e: Exception) {
            Log.e("KHARA_PDF", "Error generating PDF page", e)
        }

        pdfDocument.finishPage(page)

        val fileName = "Khara_$actualScanId.pdf"
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
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { fos ->
                    pdfDocument.writeTo(fos)
                }
                finalUri = Uri.fromFile(file)
            }
            Log.d("KHARA_PDF", "PDF saved to downloads: $finalUri")
        } catch (e: Exception) {
            Log.e("KHARA_PDF", "Error saving PDF file", e)
        } finally {
            pdfDocument.close()
        }

        return finalUri
    }
}
