package com.rolo.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rolo.app.ui.BusinessCard

class TextRecognitionHelper {

    // Cliente ML Kit Text Recognition nativo, offline y gratuito
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun extractDataFromBitmap(bitmap: Bitmap, onResult: (BusinessCard) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val card = parseTextToCard(visionText)
                onResult(card)
            }
            .addOnFailureListener {
                onResult(BusinessCard(name = "No se pudo leer"))
            }
    }

    private fun parseTextToCard(visionText: Text): BusinessCard {
        val blocks = visionText.textBlocks
        var name = ""
        var phone = ""
        var email = ""
        var address = ""

        val emailRegex = "[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}".toRegex()
        val phoneRegex = "(\\+?\\d[\\d\\-\\s\\.()]{8,15})".toRegex()
        val addressKeywords = listOf("street", "ave", "calle", "avenida", "blvd", "floor", "col.", "cp")

        for (block in blocks) {
            val blockText = block.text

            // Buscar Email
            if (email.isEmpty()) {
                val emailMatch = emailRegex.find(blockText)
                if (emailMatch != null) {
                    email = emailMatch.value
                    continue
                }
            }

            // Buscar Teléfono
            if (phone.isEmpty()) {
                val phoneMatch = phoneRegex.find(blockText)
                if (phoneMatch != null && blockText.length < 20) {
                    phone = phoneMatch.value
                    continue
                }
            }

            // Buscar Dirección 
            if (address.isEmpty() && addressKeywords.any { blockText.lowercase().contains(it) }) {
                address = blockText
                continue
            }

            // Nombre: asume el primer bloque corto que no contiene números
            if (name.isEmpty() && blockText.length in 3..30 && !blockText.contains(Regex("\\d"))) {
                name = blockText
            }
        }

        return BusinessCard(
            name = name.ifEmpty { "Desconocido" },
            phone = phone,
            email = email,
            address = address
        )
    }
}
