package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        val btnAdd = findViewById<ImageView>(R.id.btnAddProduct)

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

        rvProductList.layoutManager = LinearLayoutManager(this)
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

        // --- 3. TOMBOL TAMBAH BARANG (FIXED) ---
        // Sekarang mengarah ke ProductEntryActivity milik Bapak
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

    // --- DIALOG EDIT (FIXED) ---
    private fun showEditDialog(product: Product) {
        val options = arrayOf("✏️ Edit Detail Barang", "📦 Update Stok Cepat")
        AlertDialog.Builder(this)
            .setTitle("Menu: ${product.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Buka halaman Edit (ProductEntryActivity)
                        val intent = Intent(this, ProductEntryActivity::class.java)

                        // Kirim Data Barang (Sesuai kode Bapak: "PRODUCT_TO_EDIT")
                        // Pastikan Product implements Parcelable ya Pak
                        intent.putExtra("PRODUCT_TO_EDIT", product)
                        startActivity(intent)
                    }
                    1 -> {
                        Toast.makeText(this, "Silakan edit lewat menu Detail dulu ya", Toast.LENGTH_SHORT).show()
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