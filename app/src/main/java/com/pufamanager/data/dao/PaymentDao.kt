package com.pufamanager.data.dao

import androidx.room.*
import com.pufamanager.data.entity.Payment
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("SELECT * FROM Payment WHERE playerId = :playerId")
    fun getPaymentsForPlayer(playerId: Int): Flow<List<Payment>>

    @Query("SELECT * FROM Payment WHERE month = :month")
    fun getPaymentsForMonth(month: String): Flow<List<Payment>>

    @Query("SELECT * FROM Payment")
    suspend fun getAllPaymentsList(): List<Payment>

    @Query("SELECT * FROM Payment WHERE playerId = :playerId AND month = :month LIMIT 1")
    suspend fun getPaymentByPlayerAndMonth(playerId: Int, month: String): Payment?

    @Query("SELECT * FROM Payment")
    fun getAllPayments(): Flow<List<Payment>>

    @Update
    suspend fun updatePayment(payment: Payment)
}
