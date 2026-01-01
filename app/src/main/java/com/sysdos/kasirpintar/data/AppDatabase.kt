package com.sysdos.kasirpintar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sysdos.kasirpintar.data.dao.CategoryDao // Import DAO Kategori
import com.sysdos.kasirpintar.data.dao.ProductDao
import com.sysdos.kasirpintar.data.model.Category // Import Model Kategori
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// VERSION NAIK JADI 5 (Karena nambah tabel Category)
@Database(entities = [Product::class, Transaction::class, User::class, Category::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao // AKSES DAO KATEGORI

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
                    .fallbackToDestructiveMigration() // Hapus data lama biar aman saat update versi
                    .addCallback(DatabaseCallback())  // TETAP PAKAI CALLBACK BAPAK
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // --- CALLBACK SEEDING DATA ---
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val productDao = database.productDao()
                        val categoryDao = database.categoryDao()

                        // 1. [HAPUS/KOMENTARI BAGIAN INI]
                        // Supaya aplikasi mendeteksi ini sebagai "Toko Baru"
                        // productDao.insertUser(User(username = "admin", password = "123", role = "superadmin"))
                        // productDao.insertUser(User(username = "manajer", password = "123", role = "manager"))
                        // productDao.insertUser(User(username = "kasir", password = "123", role = "kasir"))

                        // 2. ISI KATEGORI BAWAAN (Ini Boleh Dipertahankan, Membantu User)
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