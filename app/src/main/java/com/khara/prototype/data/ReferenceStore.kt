package com.khara.prototype.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class ReferenceItem(
    val name: String,
    val photoPaths: List<String>,
    val embeddings: List<FloatArray>,
    val createdAt: Long = System.currentTimeMillis(),
    val pHash: String = "",
    val pinCount: Int = 14,
    val textureLaplacianVariance: Double = 0.0,
    val textureLbpEntropy: Double = 0.0,
    val textureScratchRatio: Double = 0.0,
    val avgColorLAB: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0)
)

object ReferenceStore {
    private val references = CopyOnWriteArrayList<ReferenceItem>()
    private var isLoaded = false

    fun load(context: Context) {
        if (isLoaded) return
        try {
            val file = File(context.filesDir, "references.json")
            if (file.exists()) {
                val jsonString = file.readText()
                val jsonArray = if (jsonString.trim().startsWith("[")) {
                    JSONArray(jsonString)
                } else {
                    JSONObject(jsonString).optJSONArray("references") ?: JSONArray()
                }
                references.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.getString("name")
                    val createdAt = obj.optLong("registeredAt", obj.optLong("createdAt", System.currentTimeMillis()))
                    val pHash = obj.optString("pHash", "")
                    val pinCount = obj.optInt("pinCount", 14)
                    val lapVar = obj.optDouble("textureLaplacianVariance", 0.0)
                    val lbpEnt = obj.optDouble("textureLbpEntropy", 0.0)
                    val scratch = obj.optDouble("textureScratchRatio", 0.0)

                    val labObj = obj.optJSONObject("avgColorLAB")
                    val labL = labObj?.optDouble("L", 0.0) ?: obj.optDouble("colorLabL", 0.0)
                    val labA = labObj?.optDouble("A", 0.0) ?: obj.optDouble("colorLabA", 0.0)
                    val labB = labObj?.optDouble("B", 0.0) ?: obj.optDouble("colorLabB", 0.0)

                    val pathsArr = obj.optJSONArray("photoPaths") ?: JSONArray()
                    val photoPaths = mutableListOf<String>()
                    for (j in 0 until pathsArr.length()) {
                        photoPaths.add(pathsArr.getString(j))
                    }

                    val embsArr = obj.optJSONArray("vectors") ?: obj.optJSONArray("embeddings") ?: JSONArray()
                    val embeddings = mutableListOf<FloatArray>()
                    for (j in 0 until embsArr.length()) {
                        val vecArr = embsArr.getJSONArray(j)
                        val vec = FloatArray(vecArr.length())
                        for (k in 0 until vecArr.length()) {
                            vec[k] = vecArr.getDouble(k).toFloat()
                        }
                        embeddings.add(vec)
                    }

                    references.add(
                        ReferenceItem(
                            name = name,
                            photoPaths = photoPaths,
                            embeddings = embeddings,
                            createdAt = createdAt,
                            pHash = pHash,
                            pinCount = pinCount,
                            textureLaplacianVariance = lapVar,
                            textureLbpEntropy = lbpEnt,
                            textureScratchRatio = scratch,
                            avgColorLAB = Triple(labL, labA, labB)
                        )
                    )
                }
            }
            isLoaded = true
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error loading references.json", e)
        }
    }

    fun save(context: Context) {
        try {
            val file = File(context.filesDir, "references.json")
            val rootObj = JSONObject()
            val jsonArray = JSONArray()
            for (item in references) {
                val obj = JSONObject()
                obj.put("name", item.name)
                obj.put("registeredAt", item.createdAt)
                obj.put("pHash", item.pHash)
                obj.put("pinCount", item.pinCount)
                obj.put("textureLaplacianVariance", item.textureLaplacianVariance)
                obj.put("textureLbpEntropy", item.textureLbpEntropy)
                obj.put("textureScratchRatio", item.textureScratchRatio)

                val labObj = JSONObject()
                labObj.put("L", item.avgColorLAB.first)
                labObj.put("A", item.avgColorLAB.second)
                labObj.put("B", item.avgColorLAB.third)
                obj.put("avgColorLAB", labObj)

                val pathsArr = JSONArray()
                item.photoPaths.forEach { pathsArr.put(it) }
                obj.put("photoPaths", pathsArr)

                val embsArr = JSONArray()
                for (emb in item.embeddings) {
                    val vecArr = JSONArray()
                    emb.forEach { vecArr.put(it.toDouble()) }
                    embsArr.put(vecArr)
                }
                obj.put("vectors", embsArr)

                jsonArray.put(obj)
            }
            rootObj.put("references", jsonArray)
            file.writeText(rootObj.toString(2))
        } catch (e: Exception) {
            Log.e("KHARA_ERROR", "Error saving references.json", e)
        }
    }

    fun add(context: Context, reference: ReferenceItem) {
        load(context)
        references.add(reference)
        save(context)
    }

    fun add(
        context: Context,
        name: String,
        photoPaths: List<String>,
        embeddings: List<FloatArray>,
        pHash: String = "",
        pinCount: Int = 14,
        textureLaplacianVariance: Double = 0.0,
        textureLbpEntropy: Double = 0.0,
        textureScratchRatio: Double = 0.0,
        avgColorLAB: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0)
    ) {
        add(
            context,
            ReferenceItem(
                name = name,
                photoPaths = photoPaths,
                embeddings = embeddings,
                pHash = pHash,
                pinCount = pinCount,
                textureLaplacianVariance = textureLaplacianVariance,
                textureLbpEntropy = textureLbpEntropy,
                textureScratchRatio = textureScratchRatio,
                avgColorLAB = avgColorLAB
            )
        )
    }

    fun add(reference: ReferenceItem) {
        references.add(reference)
    }

    fun getAll(context: Context? = null): List<ReferenceItem> {
        if (context != null) load(context)
        return references.toList()
    }

    fun clear(context: Context? = null) {
        references.clear()
        if (context != null) {
            try {
                File(context.filesDir, "references.json").delete()
            } catch (e: Exception) {
                Log.e("KHARA_ERROR", "Error deleting references.json", e)
            }
        }
    }

    fun isEmpty(context: Context? = null): Boolean {
        if (context != null) load(context)
        return references.isEmpty()
    }
}
