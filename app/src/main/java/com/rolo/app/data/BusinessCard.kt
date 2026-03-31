package com.rolo.app.data

import android.graphics.RectF

data class BusinessCard(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val imagePath: String = "",
    val phoneBounds: RectF? = null,
    val emailBounds: RectF? = null,
    val addressBounds: RectF? = null
) {
    fun hasPhoneBounds() = phoneBounds != null
    fun hasEmailBounds() = emailBounds != null
    fun hasAddressBounds() = addressBounds != null
}