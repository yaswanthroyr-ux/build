package com.example.build.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class RecognitionResult(
    val fullText: String,
    val lines: List<String>,
    val detectedIngredients: List<String>,
    val amountsAndItems: List<Pair<String, String>>
)

class TextRecognitionHelper(context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap): RecognitionResult = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val result = parseVisionText(visionText.text)
                continuation.resume(result)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    suspend fun processImage(context: Context, uri: Uri): RecognitionResult = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = parseVisionText(visionText.text)
                    continuation.resume(result)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    private fun parseVisionText(fullText: String): RecognitionResult {
        val rawLines = fullText.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        val ignoreWords = getNoiseWords()
        val detectedIngredients = mutableListOf<String>()
        val amountsAndItems = mutableListOf<Pair<String, String>>()

        val amountRegex = Regex("""(\$\s*\d+[,.]?\d*|\d+[,.]?\d*\s*(?:g|kg|lb|oz|l|ml|pcs|pack|x)?\b)""", RegexOption.IGNORE_CASE)

        for (line in rawLines) {
            val amountMatch = amountRegex.find(line)
            val amount = amountMatch?.value ?: ""
            val cleanItemName = line.replace(amountRegex, "")
                .replace(Regex("""[^a-zA-Z\s]"""), " ")
                .trim()

            if (cleanItemName.length > 2 && !ignoreWords.contains(cleanItemName.lowercase())) {
                val words = cleanItemName.split(Regex("""\s+""")).filter {
                    it.length > 2 && !ignoreWords.contains(it.lowercase())
                }
                for (word in words) {
                    if (!detectedIngredients.contains(word.lowercase())) {
                        detectedIngredients.add(word)
                    }
                }
                if (amount.isNotBlank()) {
                    amountsAndItems.add(Pair(amount, cleanItemName))
                }
            }
        }

        return RecognitionResult(
            fullText = fullText,
            lines = rawLines,
            detectedIngredients = detectedIngredients,
            amountsAndItems = amountsAndItems
        )
    }

    private fun getNoiseWords(): Set<String> {
        return setOf(
            "total", "subtotal", "tax", "cash", "credit", "card", "visa", "mastercard",
            "date", "time", "receipt", "thank", "you", "welcome", "store", "supermarket",
            "qty", "price", "amount", "change", "bal", "balance", "inv", "invoice", "no",
            "tel", "fax", "www", "com", "http", "https", "order", "item", "items"
        )
    }
}
