package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sysdos.kasirpintar.data.model.ShiftLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Insert
    suspend fun insertLog(log: ShiftLog)

    @Query("SELECT * FROM shift_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ShiftLog>>
}