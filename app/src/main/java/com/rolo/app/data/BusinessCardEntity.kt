package com.rolo.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "business_cards")
data class BusinessCardEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val address: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val phoneBounds: String = "",
    val emailBounds: String = "",
    val addressBounds: String = ""
)

fun BusinessCardEntity.toBusinessCard(): BusinessCard {
    return BusinessCard(
        id = id,
        name = name,
        phone = phone,
        email = email,
        address = address,
        imagePath = imagePath,
        phoneBounds = parseBounds(phoneBounds),
        emailBounds = parseBounds(emailBounds),
        addressBounds = parseBounds(addressBounds)
    )
}

fun BusinessCard.toEntity(): BusinessCardEntity {
    return BusinessCardEntity(
        id = id,
        name = name,
        phone = phone,
        email = email,
        address = address,
        imagePath = imagePath,
        phoneBounds = boundsToString(phoneBounds),
        emailBounds = boundsToString(emailBounds),
        addressBounds = boundsToString(addressBounds)
    )
}

private fun boundsToString(bounds: android.graphics.RectF?): String {
    if (bounds == null) return ""
    return "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
}

private fun parseBounds(str: String): android.graphics.RectF? {
    if (str.isEmpty()) return null
    val parts = str.split(",")
    if (parts.size != 4) return null
    return try {
        android.graphics.RectF(
            parts[0].toFloat(),
            parts[1].toFloat(),
            parts[2].toFloat(),
            parts[3].toFloat()
        )
    } catch (e: Exception) {
        null
    }
}