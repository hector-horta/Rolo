package com.rolo.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessCardDao {
    @Query("SELECT * FROM business_cards ORDER BY createdAt DESC")
    fun getAllCards(): Flow<List<BusinessCardEntity>>

    @Query("SELECT COUNT(*) FROM business_cards")
    fun getCardCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: BusinessCardEntity)

    @Delete
    suspend fun deleteCard(card: BusinessCardEntity)

    @Query("DELETE FROM business_cards WHERE id = :cardId")
    suspend fun deleteCardById(cardId: String)

    @Query("SELECT * FROM business_cards WHERE id = :cardId")
    suspend fun getCardById(cardId: String): BusinessCardEntity?
}