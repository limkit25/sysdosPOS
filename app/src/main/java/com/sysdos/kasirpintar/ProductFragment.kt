package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import androidx.appcompat.app.AlertDialog // Pastikan Import

class ProductFragment : Fragment(R.layout.fragment_tab_product) {
    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: ProductAdapter
    private var fullList: List<Product> = listOf()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[ProductViewModel::class.java]

        val rv = view.findViewById<RecyclerView>(R.id.rvProductList)
        val fab = view.findViewById<FloatingActionButton>(R.id.btnAddProduct)
        val sv = view.findViewById<SearchView>(R.id.svProduct)

        adapter = ProductAdapter(
            onItemClick = { showMenuDialog(it) }, // Panggil fungsi lokal
            onItemLongClick = {}
        )
        rv.layoutManager = GridLayoutManager(context, 2)
        rv.adapter = adapter

        viewModel.allProducts.observe(viewLifecycleOwner) {
            fullList = it
            adapter.submitList(it)
        }

        fab.setOnClickListener { startActivity(Intent(context, ProductEntryActivity::class.java)) }

        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = fullList.filter {
                    it.name.contains(newText ?: "", true) || it.barcode?.contains(newText ?: "") == true
                }
                adapter.submitList(filtered)
                return true
            }
        })
    }

    // Copy logika Dialog Menu dari activity sebelumnya ke sini
    private fun showMenuDialog(product: Product) {
        // Tambahkan Opsi ke-3: Hapus
        val options = arrayOf("✏️ Edit Barang", "🚛 Restock (Barang Masuk)", "🗑️ Hapus Barang")

        AlertDialog.Builder(requireContext())
            .setTitle(product.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // EDIT
                        val intent = Intent(context, ProductEntryActivity::class.java)
                        intent.putExtra("PRODUCT_TO_EDIT", product)
                        startActivity(intent)
                    }
                    1 -> { // RESTOCK
                        (activity as? ProductListActivity)?.showRestockDialog(product)
                    }
                    2 -> { // HAPUS (LOGIKA BARU)
                        confirmDeleteProduct(product)
                    }
                }
            }
            .show()
    }

    // Fungsi Konfirmasi Hapus
    private fun confirmDeleteProduct(product: Product) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus ${product.name}?")
            .setMessage("Data yang dihapus tidak bisa dikembalikan.")
            .setPositiveButton("HAPUS") { _, _ ->
                viewModel.delete(product) // Pastikan di ViewModel ada fungsi delete(product)
                android.widget.Toast.makeText(context, "Produk Dihapus", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}