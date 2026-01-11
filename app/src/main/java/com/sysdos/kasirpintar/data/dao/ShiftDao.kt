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

    // 🔥 PERBAIKAN DI SINI (Ditambahkan parameter uid dan WHERE userId)
    @Query("SELECT * FROM shift_logs WHERE userId = :uid ORDER BY timestamp DESC")
    fun getAllLogs(uid: Int): Flow<List<ShiftLog>>

    // Tambahan untuk cek status shift terakhir user
    @Query("SELECT * FROM shift_logs WHERE userId = :uid ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLog(uid: Int): ShiftLog?
}