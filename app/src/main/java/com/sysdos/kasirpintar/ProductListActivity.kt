package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton // Pastikan pakai ImageButton sesuai XML
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.setPadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.util.Locale

class ProductListActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: ProductAdapter

    // Variabel Search & List
    private lateinit var svSearch: SearchView
    private var fullList: List<Product> = ArrayList()

    // --- 1. SCANNER BARCODE (Untuk Cari Barang Cepat) ---
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val barcode = result.contents
            svSearch.setQuery(barcode, true) // Otomatis ketik barcode di kolom cari
            Toast.makeText(this, "Scan: $barcode", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // --- HUBUNGKAN VIEW (DENGAN PENGAMAN '?' AGAR TIDAK CRASH) ---
        // Kalau ID tidak ketemu di XML, variabel akan bernilai null (aman)
        svSearch = findViewById(R.id.svProduct)
        val btnScan = findViewById<ImageView?>(R.id.btnScanGudang)
        val rvProductList = findViewById<RecyclerView>(R.id.rvProductList)
        val btnAdd = findViewById<FloatingActionButton>(R.id.btnAddProduct)

        // Tombol Laporan Stok (Ini tersangka utama penyebab crash)
        val btnReport = findViewById<android.view.View?>(R.id.btnStockReport)

        // --- SETUP ADAPTER ---
        adapter = ProductAdapter(
            onItemClick = { product -> showMenuDialog(product) },
            onItemLongClick = { product -> showDeleteDialog(product) }
        )

        rvProductList.layoutManager = GridLayoutManager(this, 2)
        rvProductList.adapter = adapter

        // --- OBSERVE DATA ---
        viewModel.allProducts.observe(this) { products ->
            fullList = products
            val query = svSearch.query.toString()
            if (query.isNotEmpty()) filterList(query) else adapter.submitList(products)
        }

        // --- LOGIKA SEARCH ---
        svSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        // --- LOGIKA TOMBOL (DENGAN PENGAMAN '?.') ---

        // 1. Tombol Scan (Hanya jalan kalau tombolnya ada)
        btnScan?.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(ScanActivity::class.java)
            scanLauncher.launch(options)
        }

        // 2. Tombol Laporan Stok (Hanya jalan kalau tombolnya ada)
        btnReport?.setOnClickListener {
            startActivity(Intent(this, StockReportActivity::class.java))
        }

        // 3. Tombol Tambah Barang
        btnAdd.setOnClickListener {
            startActivity(Intent(this, ProductEntryActivity::class.java))
        }
    }

    private fun filterList(query: String?) {
        val searchText = query ?: return
        val filtered = fullList.filter { product ->
            val pName = product.name ?: ""
            val pBarcode = product.barcode ?: ""

            pName.lowercase().contains(searchText.lowercase()) ||
                    pBarcode.contains(searchText)
        }
        adapter.submitList(filtered)
    }

    // --- DIALOG MENU UTAMA ---
    private fun showMenuDialog(product: Product) {
        // KITA TAMBAHKAN MENU "RESTOCK" DI SINI
        val options = arrayOf(
            "🚛 Restock (Barang Masuk)",  // <--- FITUR BARU
            "✏️ Edit Detail Barang",
            "📦 Hapus Barang"
        )

        AlertDialog.Builder(this)
            .setTitle("Menu: ${product.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRestockDialog(product) // Buka Dialog Restock
                    1 -> {
                        // Buka Halaman Edit Full
                        val intent = Intent(this, ProductEntryActivity::class.java)
                        intent.putExtra("PRODUCT_TO_EDIT", product)
                        startActivity(intent)
                    }
                    2 -> showDeleteDialog(product)
                }
            }
            .show()
    }

    // --- [FITUR BARU] DIALOG RESTOCK / PEMBELIAN KE SUPPLIER ---
    private fun showRestockDialog(product: Product) {
        // Kita bikin Layout Dialognya pakai kodingan saja (biar praktis gak perlu XML baru)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50)

        // Input 1: Jumlah Masuk
        val etQty = EditText(this)
        etQty.hint = "Jumlah Barang Masuk (Qty)"
        etQty.inputType = InputType.TYPE_CLASS_NUMBER

        // Input 2: Update Harga Modal (Opsional)
        val etCost = EditText(this)
        etCost.hint = "Update Harga Beli/Modal (Per Pcs)"
        etCost.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etCost.setText(product.costPrice.toInt().toString()) // Isi otomatis dengan modal lama

        // Tambahkan ke Layout
        layout.addView(etQty)

        // Jarak antar input
        val space = TextView(this)
        space.height = 30
        layout.addView(space)

        layout.addView(etCost)

        AlertDialog.Builder(this)
            .setTitle("Restock: ${product.name}")
            .setMessage("Masukkan jumlah barang yang baru dibeli dari Supplier.")
            .setView(layout)
            .setPositiveButton("SIMPAN STOK") { _, _ ->
                val qtyStr = etQty.text.toString()
                val costStr = etCost.text.toString()

                if (qtyStr.isNotEmpty()) {
                    val qtyIn = qtyStr.toInt()
                    val newCost = if (costStr.isNotEmpty()) costStr.toDouble() else product.costPrice

                    // HITUNG STOK BARU
                    val newStock = product.stock + qtyIn

                    // UPDATE DATABASE
                    // Kita copy produk lama, tapi ganti stok & modalnya
                    val updatedProduct = product.copy(
                        stock = newStock,
                        costPrice = newCost
                    )

                    viewModel.update(updatedProduct)

                    Toast.makeText(this, "✅ Stok Bertambah: +$qtyIn", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Jumlah tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDeleteDialog(product: Product) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Barang?")
            .setMessage("Yakin ingin menghapus '${product.name}'? Data tidak bisa kembali.")
            .setPositiveButton("HAPUS") { _, _ ->
                viewModel.delete(product)
                Toast.makeText(this, "Barang dihapus.", Toast.LENGTH_SHORT).show()
                svSearch.setQuery("", false)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}