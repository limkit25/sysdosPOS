package com.sysdos.kasirpintar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sysdos.kasirpintar.data.dao.CategoryDao
import com.sysdos.kasirpintar.data.dao.ProductDao
import com.sysdos.kasirpintar.data.dao.ShiftDao
import com.sysdos.kasirpintar.data.dao.StockLogDao // <--- IMPORT INI
import com.sysdos.kasirpintar.data.dao.SupplierDao
import com.sysdos.kasirpintar.data.model.Category
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.ShiftLog
import com.sysdos.kasirpintar.data.model.StockLog // <--- IMPORT INI
import com.sysdos.kasirpintar.data.model.Supplier
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// [PERBAIKAN]
// 1. Tambahkan StockLog::class di entities
// 2. Naikkan version menjadi 4
@Database(
    entities = [
        Product::class,
        Category::class,
        Transaction::class,
        User::class,
        ShiftLog::class,
        Supplier::class,
        StockLog::class // <--- WAJIB ADA (Agar tabel dibuat)
    ],
    version = 4, // <--- Naikkan ke 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shiftDao(): ShiftDao
    abstract fun supplierDao(): SupplierDao
    abstract fun stockLogDao(): StockLogDao // <--- WAJIB ADA (Agar bisa simpan history)

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
                    .fallbackToDestructiveMigration() // Reset data lama agar struktur baru masuk
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
                        val categoryDao = database.categoryDao()
                        // Isi Kategori Default
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