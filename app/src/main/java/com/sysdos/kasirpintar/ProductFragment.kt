package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class ProductFragment : Fragment(R.layout.fragment_tab_product) {
    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: ProductAdapter
    private var fullList: List<Product> = listOf()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[ProductViewModel::class.java]

        val rv = view.findViewById<RecyclerView>(R.id.rvProductList)
        val fab = view.findViewById<FloatingActionButton>(R.id.btnAddProduct)
        val sv = view.findViewById<SearchView>(R.id.svProduct)

        // Adapter simpel, hanya butuh logika klik item & long click (opsional)
        adapter = ProductAdapter(
            onItemClick = { showMenuDialog(it) },
            onItemLongClick = { showMenuDialog(it) }
        )

        // Menggunakan Linear Layout (List ke bawah)
        rv.layoutManager = LinearLayoutManager(context)
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

    // 🔥 MENU DIALOG (RESTOCK SUDAH DIHAPUS) 🔥
    private fun showMenuDialog(product: Product) {
        // Opsi tinggal 2: Edit & Hapus
        val options = arrayOf("✏️ Edit Barang", "🗑️ Hapus Barang")

        AlertDialog.Builder(requireContext())
            .setTitle(product.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // EDIT
                        val intent = Intent(context, ProductEntryActivity::class.java)
                        intent.putExtra("PRODUCT_TO_EDIT", product)
                        startActivity(intent)
                    }
                    1 -> { // HAPUS (Index geser jadi 1)
                        confirmDeleteProduct(product)
                    }
                }
            }
            .show()
    }

    private fun confirmDeleteProduct(product: Product) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus ${product.name}?")
            .setMessage("Data yang dihapus tidak bisa dikembalikan.")
            .setPositiveButton("HAPUS") { _, _ ->
                viewModel.delete(product)
                android.widget.Toast.makeText(context, "Produk Dihapus", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}