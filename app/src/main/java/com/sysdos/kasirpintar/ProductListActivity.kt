package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView // <--- Tambahan
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract // <--- Tambahan Scanner
import com.journeyapps.barcodescanner.ScanOptions // <--- Tambahan Scanner
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class ProductListActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: ProductAdapter

    // Variabel Search & List
    private lateinit var svSearch: SearchView
    private var fullList: List<Product> = ArrayList()

    // --- 1. SIAPKAN PELUNCUR SCANNER ---
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            // HASIL SCAN -> Masukkan ke Kolom Pencarian
            val barcode = result.contents
            svSearch.setQuery(barcode, true) // 'true' artinya langsung submit pencarian

            Toast.makeText(this, "Scan: $barcode", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Hubungkan View
        svSearch = findViewById(R.id.svProduct) // Pastikan ID ini benar di XML (dulu namanya svProduct, sesuaikan ya)
        val rvProductList = findViewById<RecyclerView>(R.id.rvProductList)
        val btnAdd = findViewById<FloatingActionButton>(R.id.btnAddProduct)

        // --- 2. HUBUNGKAN TOMBOL SCAN BARU ---
        // Pastikan di XML ID-nya adalah @id/btnScanGudang
        val btnScan = findViewById<ImageView>(R.id.btnScanGudang)

        // Setup Adapter
        adapter = ProductAdapter(
            onItemClick = { product -> showEditDialog(product) },
            onItemLongClick = { product -> showDeleteDialog(product) }
        )

        rvProductList.layoutManager = GridLayoutManager(this, 2)
        rvProductList.adapter = adapter

        // Observe Data
        viewModel.allProducts.observe(this) { products ->
            fullList = products
            val query = svSearch.query.toString()
            if (query.isNotEmpty()) filterList(query) else adapter.submitList(products)
        }

        // Logika Search Manual
        svSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        // --- 3. KLIK TOMBOL SCAN -> BUKA KAMERA ---
        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)

            // PENTING: Gunakan Activity Scan Custom kita
            options.setCaptureActivity(ScanActivity::class.java)

            scanLauncher.launch(options)
        }

        // Tombol Tambah Barang Manual
        btnAdd.setOnClickListener {
            startActivity(Intent(this, ProductEntryActivity::class.java))
        }
    }

    private fun filterList(query: String?) {
        // 1. Amankan Query pencarian
        val searchText = query ?: return

        val filtered = fullList.filter { product ->
            // 2. AMANKAN DATA PRODUK (Ini obat merahnya)
            // Kalau product.name NULL, ganti jadi "" (kosong)
            // Kalau product.barcode NULL, ganti jadi "" (kosong)
            val pName = product.name ?: ""
            val pBarcode = product.barcode ?: ""

            // 3. Baru bandingkan dengan aman
            pName.lowercase().contains(searchText.lowercase()) ||
                    pBarcode.contains(searchText)
        }
        adapter.submitList(filtered)
    }

    // --- DIALOG EDIT (Tetap Sama) ---
    private fun showEditDialog(product: Product) {
        val options = arrayOf("✏️ Edit Detail Barang", "📦 Hapus Barang")
        AlertDialog.Builder(this)
            .setTitle("Menu: ${product.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, ProductEntryActivity::class.java)
                        intent.putExtra("PRODUCT_TO_EDIT", product)
                        startActivity(intent)
                    }
                    1 -> showDeleteDialog(product)
                }
            }
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