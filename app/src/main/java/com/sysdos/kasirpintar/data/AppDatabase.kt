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
        Branch::class,
        ProductVariant::class,
        Recipe::class,
        Expense::class // 🔥 WAJIB DITAMBAHKAN (Phase 34)
    ],
    version = 14, // 🔥 NAIKKAN VERSI KE 14
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
    abstract fun branchDao(): BranchDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recipeDao(): RecipeDao
    abstract fun expenseDao(): ExpenseDao // 🔥 Phase 34

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 🔥 MIGRATION 10 -> 11 (Add 'unit' column)
        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE products ADD COLUMN unit TEXT NOT NULL DEFAULT 'Pcs'")
            }
        }

        // 🔥 MIGRATION 11 -> 12 (Add 'orderType' & 'markupAmount' to Transaction)
        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Perhatikan nama tabel harus sesuai Entity @Entity(tableName = "transaction_table")
                try {
                    database.execSQL("ALTER TABLE transaction_table ADD COLUMN orderType TEXT NOT NULL DEFAULT 'Dine In'")
                    database.execSQL("ALTER TABLE transaction_table ADD COLUMN markupAmount REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    // Ignore duplicate column errors if any
                }
            }
        }

        // 🔥 MIGRATION 12 -> 13 (Add 'markup' columns to store_config)
        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                 try {
                     database.execSQL("ALTER TABLE store_config ADD COLUMN markupGoFood REAL NOT NULL DEFAULT 20.0")
                     database.execSQL("ALTER TABLE store_config ADD COLUMN markupGrab REAL NOT NULL DEFAULT 20.0")
                     database.execSQL("ALTER TABLE store_config ADD COLUMN markupShopee REAL NOT NULL DEFAULT 20.0")
                 } catch (e: Exception) {}
            }
        }

        // 🔥 MIGRATION 13 -> 14 (Add Expense Table)
        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expenses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amount` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `branchId` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sysdos_pos_db"
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14) // 🔥 TAMBAHKAN MIGRASI BARU
                    .fallbackToDestructiveMigration() // Jaga-jaga
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