package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sysdos.kasirpintar.data.model.StoreConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreConfigDao {
    // Ambil data toko (Live Update)
    @Query("SELECT * FROM store_config WHERE id = 1 LIMIT 1")
    fun getStoreConfig(): Flow<StoreConfig?>

    // Ambil data toko (Sekali panggil, untuk fungsi print dsb)
    @Query("SELECT * FROM store_config WHERE id = 1 LIMIT 1")
    suspend fun getStoreConfigOneShot(): StoreConfig?

    // Simpan atau Update (Jika ID 1 sudah ada, dia akan menimpa/update)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: StoreConfig)
}