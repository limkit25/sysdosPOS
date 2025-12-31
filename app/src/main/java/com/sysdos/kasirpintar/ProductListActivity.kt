package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class ProductListActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: ProductAdapter

    // Variabel Search
    private lateinit var svSearch: SearchView
    private var fullList: List<Product> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Hubungkan View
        svSearch = findViewById(R.id.svSearchGudang)
        val rvProductList = findViewById<RecyclerView>(R.id.rvProductList)
        val btnAdd = findViewById<FloatingActionButton>(R.id.btnAddProduct) // Sekarang tipe-nya FAB

        // Setup Adapter
        adapter = ProductAdapter(
            onItemClick = { product ->
                // KLIK BARANG -> EDIT
                showEditDialog(product)
            },
            onItemLongClick = { product ->
                // KLIK TAHAN -> HAPUS
                showDeleteDialog(product)
            }
        )

        // GANTI KE GRID LAYOUT (2 KOLOM) AGAR RAPI SEPERTI KASIR
        rvProductList.layoutManager = GridLayoutManager(this, 2)
        rvProductList.adapter = adapter

        // --- 1. OBSERVE DATA ---
        viewModel.allProducts.observe(this) { products ->
            fullList = products

            val query = svSearch.query.toString()
            if (query.isNotEmpty()) {
                filterList(query)
            } else {
                adapter.submitList(products)
            }
        }

        // --- 2. LOGIKA SEARCH ---
        svSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        // --- 3. TOMBOL TAMBAH BARANG ---
        btnAdd.setOnClickListener {
            startActivity(Intent(this, ProductEntryActivity::class.java))
        }
    }

    private fun filterList(query: String?) {
        if (query != null) {
            val filtered = fullList.filter {
                it.name.lowercase().contains(query.lowercase())
            }
            adapter.submitList(filtered)
        }
    }

    // --- DIALOG EDIT ---
    private fun showEditDialog(product: Product) {
        val options = arrayOf("✏️ Edit Detail Barang", "📦 Hapus Barang")
        AlertDialog.Builder(this)
            .setTitle("Menu: ${product.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Buka halaman Edit
                        val intent = Intent(this, ProductEntryActivity::class.java)
                        intent.putExtra("PRODUCT_TO_EDIT", product) // Pastikan kuncinya sama dengan ProductEntryActivity
                        startActivity(intent)
                    }
                    1 -> {
                        showDeleteDialog(product)
                    }
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