package com.rolo.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BusinessCardRepository(private val dao: BusinessCardDao) {

    fun getAllCards(): Flow<List<BusinessCard>> {
        return dao.getAllCards().map { entities ->
            entities.map { it.toBusinessCard() }
        }
    }

    fun getCardCount(): Flow<Int> {
        return dao.getCardCount()
    }

    suspend fun insertCard(card: BusinessCard) {
        dao.insertCard(card.toEntity())
    }

    suspend fun deleteCard(cardId: String) {
        dao.deleteCardById(cardId)
    }
}