package com.sysdos.kasirpintar

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.StockLog
import com.sysdos.kasirpintar.data.model.Supplier
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.io.File
import java.io.FileOutputStream

class ProductListActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    // 1. LAUNCHER PILIH FILE
    private val pickCsvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadCsvFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // =============================================================
        // 🔥 2. MODIFIKASI TOMBOL IMPORT (Munculkan Pilihan)
        // =============================================================
        val btnImport = findViewById<ImageButton>(R.id.btnImportCsv)
        btnImport?.setOnClickListener {
            showImportOptionDialog() // <--- Panggil Dialog Pilihan
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        // --- ADAPTER TAB ---
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> ProductFragment()
                    1 -> ReportFragment()
                    else -> ProductFragment()
                }
            }
        }

        // --- JUDUL TAB ---
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "PRODUK"
                1 -> "ASET/STOK"
                else -> ""
            }
        }.attach()

        // BUKA TAB SPESIFIK
        val targetTab = intent.getIntExtra("OPEN_TAB_INDEX", -1)
        if (targetTab != -1 && targetTab < 2) {
            viewPager.setCurrentItem(targetTab, false)
        }
    }

    // =================================================================
    // 🔥 3. DIALOG PILIHAN (Download Template / Upload)
    // =================================================================
    private fun showImportOptionDialog() {
        val options = arrayOf("⬇️ Download Template CSV", "📂 Upload File CSV")

        AlertDialog.Builder(this)
            .setTitle("Import Barang")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveTemplateToDownloads() // Download Template
                    1 -> pickCsvLauncher.launch("text/*") // Upload File
                }
            }
            .show()
    }

    // =================================================================
    // 🔥 4. FUNGSI DOWNLOAD TEMPLATE KE HP
    // =================================================================
    private fun saveTemplateToDownloads() {
        // Isi Template CSV
        val csvContent = "Nama Produk,Kategori,Harga Modal,Harga Jual,Stok\nContoh Kopi,Minuman,5000,10000,50"
        val fileName = "template_produk_sysdos.csv"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Cara Baru (Android 10 ke atas) - Tidak butuh izin storage
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                    }
                    Toast.makeText(this, "✅ Template tersimpan di folder Download!", Toast.LENGTH_LONG).show()
                }
            } else {
                // Cara Lama (Android 9 ke bawah)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }
                Toast.makeText(this, "✅ Template tersimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal simpan template: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // =================================================================
    // 🔥 5. FUNGSI UPLOAD HELPER
    // =================================================================
    private fun uploadCsvFromUri(uri: Uri) {
        try {
            Toast.makeText(this, "Memproses file...", Toast.LENGTH_SHORT).show()

            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = File(cacheDir, "import_temp.csv")
            val outputStream = FileOutputStream(tempFile)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            viewModel.importCsv(tempFile)

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- FUNGSI RESTOCK MANUAL (Biarkan tetap ada) ---
    fun showRestockDialog(product: Product) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        // 1. SPINNER SUPPLIER
        val spnSupplier = Spinner(this)
        var supplierList: List<Supplier> = emptyList()

        viewModel.allSuppliers.observe(this) { suppliers ->
            supplierList = suppliers
            val names = suppliers.map { it.name }.toMutableList()
            names.add(0, "-- Pilih Supplier --")
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            spnSupplier.adapter = adapter
        }

        // 2. INPUT JUMLAH & HARGA
        val etQty = EditText(this).apply {
            hint = "Jumlah Masuk (Qty)"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etCost = EditText(this).apply {
            hint = "Harga Beli Baru"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(product.costPrice.toInt().toString())
        }

        layout.addView(TextView(this).apply { text = "Supplier:"; textSize = 12f })
        layout.addView(spnSupplier)
        layout.addView(TextView(this).apply { text = "Jumlah:"; textSize = 12f; setPadding(0,20,0,0) })
        layout.addView(etQty)
        layout.addView(TextView(this).apply { text = "Harga Modal (Update):"; textSize = 12f; setPadding(0,20,0,0) })
        layout.addView(etCost)

        AlertDialog.Builder(this)
            .setTitle("Restock: ${product.name}")
            .setView(layout)
            .setPositiveButton("SIMPAN") { _, _ ->
                val qtyStr = etQty.text.toString()
                if (qtyStr.isNotEmpty()) {
                    val qtyIn = qtyStr.toInt()
                    val newCost = etCost.text.toString().toDoubleOrNull() ?: product.costPrice

                    val selectedPos = spnSupplier.selectedItemPosition
                    val supplierName = if (selectedPos > 0 && selectedPos - 1 < supplierList.size) {
                        supplierList[selectedPos - 1].name
                    } else {
                        product.supplier ?: "Umum"
                    }

                    val newProduct = product.copy(
                        stock = product.stock + qtyIn,
                        costPrice = newCost,
                        supplier = supplierName
                    )
                    viewModel.update(newProduct)

                    val generatedPurchaseId = System.currentTimeMillis()
                    val log = StockLog(
                        purchaseId = generatedPurchaseId,
                        timestamp = generatedPurchaseId,
                        productName = product.name,
                        supplierName = supplierName,
                        quantity = qtyIn,
                        costPrice = newCost,
                        totalCost = qtyIn * newCost,
                        type = "IN"
                    )
                    viewModel.recordPurchase(log)

                    Toast.makeText(this, "Stok Berhasil Ditambah! 📦", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Jumlah wajib diisi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}