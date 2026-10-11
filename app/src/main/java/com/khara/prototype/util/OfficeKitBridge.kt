package com.khara.prototype.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.khara.prototype.data.ScanResult
import java.io.File

object OfficeKitBridge {
    fun syncScanToClipboard(context: Context, result: ScanResult): Boolean {
        return try {
            val json = buildScanJson(result)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Khara Scan", json))
            Log.d("KHARA_OFFICEKIT", "scan copied to clipboard")
            true
        } catch (e: Exception) {
            Log.e("KHARA_OFFICEKIT", "clipboard failed: ${e.message}")
            false
        }
    }

    fun writeScanToSyncFolder(context: Context, result: ScanResult): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "office_kit_sync")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "scan_${result.scanId ?: System.currentTimeMillis()}.json")
            file.writeText(buildScanJson(result))
            Log.d("KHARA_OFFICEKIT", "scan written to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("KHARA_OFFICEKIT", "file write failed: ${e.message}")
            null
        }
    }

    private fun buildScanJson(result: ScanResult): String {
        return """
        {
          "scanId": "${result.scanId ?: "unknown"}",
          "operator": "${result.userName}",
          "timestamp": ${System.currentTimeMillis()},
          "score": ${result.overallScore},
          "passed": ${result.isPassed},
          "summary": "${result.summary.replace("\"", "'")}",
          "referenceName": "${result.referenceName ?: "unknown"}",
          "hash": "${result.hash ?: ""}"
        }
        """.trimIndent()
    }
}
