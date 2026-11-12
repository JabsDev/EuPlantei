package br.com.jabsdev.euplantei.ml

import android.graphics.Bitmap

interface VisionClassifier {
    val labelsCount: Int
    suspend fun classify(bitmap: Bitmap): List<br.com.jabsdev.euplantei.domain.model.PlantPrediction>
    fun close()
}
