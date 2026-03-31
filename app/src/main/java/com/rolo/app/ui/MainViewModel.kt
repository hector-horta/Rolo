package com.rolo.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rolo.app.data.AppDatabase
import com.rolo.app.data.BusinessCard
import com.rolo.app.data.BusinessCardRepository
import com.rolo.app.ml.TextRecognitionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class UiState(
    val cards: List<BusinessCard> = emptyList(),
    val cardCount: Int = 0,
    val isPremium: Boolean = false,
    val showPaywall: Boolean = false,
    val isProcessing: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BusinessCardRepository
    private val textRecognitionHelper = TextRecognitionHelper()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BusinessCardRepository(database.businessCardDao())

        viewModelScope.launch {
            combine(
                repository.getAllCards(),
                repository.getCardCount()
            ) { cards, count ->
                _uiState.value.copy(cards = cards, cardCount = count)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun processImage(bitmap: Bitmap) {
        if (_uiState.value.cardCount >= 25 && !_uiState.value.isPremium) {
            _uiState.value = _uiState.value.copy(showPaywall = true)
            return
        }

        _uiState.value = _uiState.value.copy(isProcessing = true)

        textRecognitionHelper.extractDataFromBitmap(bitmap) { extractedCard ->
            viewModelScope.launch {
                val imagePath = saveImage(bitmap, extractedCard.id)
                val cardWithImage = extractedCard.copy(imagePath = imagePath)
                repository.insertCard(cardWithImage)
                
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    private fun saveImage(bitmap: Bitmap, cardId: String): String {
        val context = getApplication<Application>()
        val imagesDir = File(context.filesDir, "card_images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        val imageFile = File(imagesDir, "$cardId.jpg")
        imageFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return imageFile.absolutePath
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            val card = _uiState.value.cards.find { it.id == cardId }
            card?.let {
                if (it.imagePath.isNotEmpty()) {
                    File(it.imagePath).delete()
                }
            }
            repository.deleteCard(cardId)
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
        _uiState.value = _uiState.value.copy(isPremium = true, showPaywall = false)
    }

    fun exportDatabaseToDrive(context: Context) {
        viewModelScope.launch {
            val dbPath = context.getDatabasePath("rolo_database").absolutePath
            // El export se maneja en MainActivity con Intent
        }
    }
}