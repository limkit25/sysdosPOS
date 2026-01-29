package com.sysdos.kasirpintar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sysdos.kasirpintar.data.dao.*
import com.sysdos.kasirpintar.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Product::class,
        Category::class,
        Transaction::class,
        User::class,
        ShiftLog::class,
        Supplier::class,
        StockLog::class,
        StoreConfig::class,
        Branch::class,        // 🔥 WAJIB DITAMBAHKAN
        ProductVariant::class // 🔥 WAJIB DITAMBAHKAN DI SINI
    ],
    version = 8, // 🔥 NAIKKAN VERSI (Misal dari 7 jadi 8) BIAR AMAN
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // Daftarkan semua DAO
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shiftDao(): ShiftDao
    abstract fun supplierDao(): SupplierDao
    abstract fun stockLogDao(): StockLogDao
    abstract fun storeConfigDao(): StoreConfigDao
    abstract fun branchDao(): BranchDao // 🔥 Tambah ini
    abstract fun transactionDao(): TransactionDao // (Pastikan ini ada juga jika dipakai)

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sysdos_pos_db"
                )
                    .fallbackToDestructiveMigration() // Reset data jika struktur berubah
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        // Seed Data Awal
                        val categoryDao = database.categoryDao()
                        categoryDao.insertCategory(Category(name = "Makanan"))
                        categoryDao.insertCategory(Category(name = "Minuman"))
                        categoryDao.insertCategory(Category(name = "Snack"))
                        categoryDao.insertCategory(Category(name = "Sembako"))
                        categoryDao.insertCategory(Category(name = "Lainnya"))
                    }
                }
            }
        }
    }
}