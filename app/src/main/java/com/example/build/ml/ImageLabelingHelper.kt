package com.example.build.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class ImageLabelResult(
    val labels: List<String>,
    val confidenceMap: Map<String, Float>
)

class ImageLabelingHelper {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun labelImage(bitmap: Bitmap): ImageLabelResult = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val labelNames = labels.map { it.text }
                val map = labels.associate { it.text to it.confidence }
                continuation.resume(ImageLabelResult(labelNames, map))
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    suspend fun labelImage(context: Context, uri: Uri): ImageLabelResult = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    val labelNames = labels.map { it.text }
                    val map = labels.associate { it.text to it.confidence }
                    continuation.resume(ImageLabelResult(labelNames, map))
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
}
