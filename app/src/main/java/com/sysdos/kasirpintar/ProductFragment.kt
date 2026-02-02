package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
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

        // Adapter
        adapter = ProductAdapter(
            onItemClick = { showMenuDialog(it) },
            onItemLongClick = { showMenuDialog(it) }
        )

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

    // 🔥 1. MENU DIALOG UTAMA (DITAMBAHKAN OPSI RETUR)
    private fun showMenuDialog(product: Product) {
        val options = arrayOf("✏️ Edit Barang", "📦 Retur / Stok Keluar", "🗑️ Hapus Barang")

        AlertDialog.Builder(requireContext())
            .setTitle(product.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // EDIT
                        val intent = Intent(context, ProductEntryActivity::class.java)
                        intent.putExtra("PRODUCT_TO_EDIT", product)
                        startActivity(intent)
                    }
                    1 -> { // 🔥 RETUR (FITUR BARU)
                        showReturnDialog(product)
                    }
                    2 -> { // HAPUS
                        confirmDeleteProduct(product)
                    }
                }
            }
            .show()
    }

    // 🔥 2. DIALOG INPUT RETUR
    private fun showReturnDialog(product: Product) {
        val context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        // Input Jumlah
        val etQty = EditText(context)
        etQty.hint = "Jumlah Retur (ex: 5)"
        etQty.inputType = InputType.TYPE_CLASS_NUMBER
        layout.addView(etQty)

        // 🔥 Input Nomor Retur
        val etReturnNumber = EditText(context)
        etReturnNumber.hint = "Nomor Retur (Opsional)"
        etReturnNumber.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        layout.addView(etReturnNumber)

        // Input Alasan
        val etReason = EditText(context)
        etReason.hint = "Alasan (ex: Busuk / Expired)"
        layout.addView(etReason)

        AlertDialog.Builder(context)
            .setTitle("📦 Retur Barang")
            .setMessage("Produk: ${product.name}\nStok Saat Ini: ${product.stock}")
            .setView(layout)
            .setPositiveButton("PROSES RETUR") { _, _ ->
                val qtyStr = etQty.text.toString()
                val reason = etReason.text.toString()
                val returNo = etReturnNumber.text.toString()

                if (qtyStr.isNotEmpty()) {
                    val qty = qtyStr.toInt()

                    // Cek stok cukup gak?
                    if (qty > product.stock) {
                        Toast.makeText(context, "❌ Stok tidak cukup untuk diretur!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Panggil ViewModel (Fungsi yang baru kita perbaiki tadi)
                        viewModel.returnStockToSupplier(
                            product = product,
                            qtyToReturn = qty,
                            reason = reason.ifEmpty { "Rusak/Expired" },
                            supplierName = product.supplier ?: "Supplier Umum",
                            returnNumber = returNo // 🔥 KIRIM NOMOR RETUR
                        )
                        Toast.makeText(context, "✅ Retur Berhasil Dicatat!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Jumlah wajib diisi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
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