package com.sysdos.kasirpintar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sysdos.kasirpintar.data.dao.* // Import semua DAO
import com.sysdos.kasirpintar.data.model.* // Import semua Model (Termasuk StoreConfig)
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// [PERBAIKAN]
// 1. Ditambahkan StoreConfig::class ke entities (WAJIB)
// 2. Version dinaikkan ke 6 (agar aman refresh database)
@Database(
    entities = [
        Product::class,
        Category::class,
        Transaction::class,
        User::class,
        ShiftLog::class,
        Supplier::class,
        StockLog::class,
        StoreConfig::class // 🔥 WAJIB ADA: Agar tabel setting toko dibuat
    ],
    version = 6, // 🔥 Naikkan versi jika mengubah struktur
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // Daftarkan semua DAO yang Anda pakai
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shiftDao(): ShiftDao
    abstract fun supplierDao(): SupplierDao
    abstract fun stockLogDao(): StockLogDao
    abstract fun storeConfigDao(): StoreConfigDao // 🔥 Akses ke DAO Config

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
                    .fallbackToDestructiveMigration() // Reset data jika versi berubah
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
                        // Isi data awal (Seed) jika perlu
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