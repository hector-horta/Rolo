package com.rolo.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolo.app.ml.TextRecognitionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BusinessCard(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val imagePath: String = "" // Ruta para almacenamiento interno de la foto
)

data class UiState(
    val cards: List<BusinessCard> = emptyList(),
    val cardCount: Int = 0,
    val isPremium: Boolean = false,
    val showPaywall: Boolean = false,
    val isProcessing: Boolean = false
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val textRecognitionHelper = TextRecognitionHelper()

    fun processImage(bitmap: Bitmap) {
        if (_uiState.value.cardCount >= 25 && !_uiState.value.isPremium) {
            _uiState.value = _uiState.value.copy(showPaywall = true)
            return
        }

        _uiState.value = _uiState.value.copy(isProcessing = true)

        textRecognitionHelper.extractDataFromBitmap(bitmap) { extractedCard ->
            val updatedCards = _uiState.value.cards + extractedCard
            _uiState.value = _uiState.value.copy(
                cards = updatedCards,
                cardCount = updatedCards.size,
                isProcessing = false
            )
            // Aquí llamarías al Room DAO para guardar en BD (viewModelScope.launch { dao.insert(card) })
            // Y al utilitario para guardar File(context.filesDir, "image_xyz.jpg")
        }
    }

    fun showPaywallIfLimitReached(force: Boolean = false) {
        if (force || (_uiState.value.cardCount >= 25 && !_uiState.value.isPremium)) {
            _uiState.value = _uiState.value.copy(showPaywall = true)
        }
    }

    fun dismissPaywall() {
        _uiState.value = _uiState.value.copy(showPaywall = false)
    }

    fun purchasePremium(context: Context) {
        // Aquí debe inicializarse Google Play Billing API:
        // val billingClient = BillingClient.newBuilder(context)...
        // Si el resultado es OK:
        _uiState.value = _uiState.value.copy(isPremium = true, showPaywall = false)
    }

    fun exportDatabaseToDrive(context: Context) {
        // Lógica de respaldo:
        // 1. context.getDatabasePath("rolo_db.sqlite")
        // 2. Disparar un Intent.ACTION_CREATE_DOCUMENT en el UI y escribir el SQLite ahí
    }
}
