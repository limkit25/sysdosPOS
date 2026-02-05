package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sysdos.kasirpintar.data.model.Expense

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllExpenses(): List<Expense>

    @Query("SELECT * FROM expenses WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getExpensesByDateRange(start: Long, end: Long): List<Expense>
    
    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp BETWEEN :start AND :end")
    suspend fun getTotalExpenseByDateRange(start: Long, end: Long): Double?
}
