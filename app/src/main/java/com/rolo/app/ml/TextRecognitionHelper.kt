package com.rolo.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rolo.app.data.BusinessCard

class TextRecognitionHelper {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun extractDataFromBitmap(bitmap: Bitmap, onResult: (BusinessCard) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val card = parseTextToCard(visionText, bitmap.width, bitmap.height)
                onResult(card)
            }
            .addOnFailureListener {
                onResult(BusinessCard(name = "No se pudo leer"))
            }
    }

    private fun parseTextToCard(visionText: Text, imageWidth: Int, imageHeight: Int): BusinessCard {
        val blocks = visionText.textBlocks
        var name = ""
        var phone = ""
        var email = ""
        var address = ""
        
        var phoneBounds: android.graphics.RectF? = null
        var emailBounds: android.graphics.RectF? = null
        var addressBounds: android.graphics.RectF? = null

        val emailRegex = "[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}".toRegex()
        val phoneRegex = "(\\+?\\d[\\d\\-\\s\\.()]{8,15})".toRegex()
        val addressKeywords = listOf("street", "ave", "calle", "avenida", "blvd", "floor", "col.", "cp", "suite", "office", "building")

        for (block in blocks) {
            val blockText = block.text

            if (email.isEmpty()) {
                val emailMatch = emailRegex.find(blockText)
                if (emailMatch != null) {
                    email = emailMatch.value
                    emailBounds = getBlockBounds(block, imageWidth, imageHeight)
                    continue
                }
            }

            if (phone.isEmpty()) {
                val phoneMatch = phoneRegex.find(blockText)
                if (phoneMatch != null && blockText.length < 20) {
                    phone = phoneMatch.value
                    phoneBounds = getBlockBounds(block, imageWidth, imageHeight)
                    continue
                }
            }

            if (address.isEmpty() && addressKeywords.any { blockText.lowercase().contains(it) }) {
                address = blockText
                addressBounds = getBlockBounds(block, imageWidth, imageHeight)
                continue
            }

            if (name.isEmpty() && blockText.length in 3..30 && !blockText.contains(Regex("\\d"))) {
                name = blockText
            }
        }

        return BusinessCard(
            name = name.ifEmpty { "Desconocido" },
            phone = phone,
            email = email,
            address = address,
            phoneBounds = phoneBounds,
            emailBounds = emailBounds,
            addressBounds = addressBounds
        )
    }

    private fun getBlockBounds(block: Text.TextBlock, imageWidth: Int, imageHeight: Int): android.graphics.RectF {
        val boundingBox = block.boundingBox
        return if (boundingBox != null) {
            android.graphics.RectF(
                boundingBox.left.toFloat() / imageWidth,
                boundingBox.top.toFloat() / imageHeight,
                boundingBox.right.toFloat() / imageWidth,
                boundingBox.bottom.toFloat() / imageHeight
            )
        } else {
            android.graphics.RectF(0f, 0f, 0f, 0f)
        }
    }
}