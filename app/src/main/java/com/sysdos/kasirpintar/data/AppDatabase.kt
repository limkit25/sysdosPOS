package com.sysdos.kasirpintar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.data.model.User // Import User
import com.sysdos.kasirpintar.data.dao.ProductDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Product::class, Transaction::class, User::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kasir_pintar_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback()) // Panggil Callback
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // FUNGSI UNTUK ISI DATA AWAL (SEEDING)
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = database.productDao()

                        // 1. SUPERADMIN (Akses Full)
                        dao.insertUser(User(username = "admin", password = "123", role = "superadmin"))

                        // 2. MANAGER (Bisa Laporan, Gak bisa Setting)
                        dao.insertUser(User(username = "manajer", password = "123", role = "manager"))

                        // 3. KASIR (Cuma Jualan)
                        dao.insertUser(User(username = "kasir", password = "123", role = "kasir"))
                    }
                }
            }
        }
    }
}