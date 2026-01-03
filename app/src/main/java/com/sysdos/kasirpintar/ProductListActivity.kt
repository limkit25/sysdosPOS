package com.sysdos.kasirpintar

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
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
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import android.widget.Spinner
import android.widget.ArrayAdapter

class ProductListActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list) // Pastikan XML-nya sudah yang versi TAB

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Tombol Kembali
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        // KONFIGURASI ADAPTER TAB (JADI 4 TAB)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 4 // <--- Ubah jadi 4
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> ProductFragment()   // 1. Produk
                    1 -> SupplierFragment()  // 2. Supplier
                    2 -> ReportFragment()    // 3. LAPORAN STOK (BARU)
                    3 -> HistoryFragment()   // 4. Riwayat
                    else -> ProductFragment()
                }
            }
        }

        // JUDUL TAB
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "PRODUK"
                1 -> "SUPPLIER"
                2 -> "ASET/STOK" // Nama Tab Pendek agar muat
                3 -> "RIWAYAT"
                else -> ""
            }
        }.attach()
        val targetTab = intent.getIntExtra("OPEN_TAB_INDEX", -1)
        if (targetTab != -1) {
            // Geser ViewPager ke tab yang diminta (Index 2 = Laporan)
            viewPager.setCurrentItem(targetTab, false)
        }
    }

    // FUNGSI RESTOCK (Dipanggil dari Tab Product)
    fun showRestockDialog(product: Product) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        // 1. SPINNER SUPPLIER
        val spnSupplier = Spinner(this)
        var supplierList: List<com.sysdos.kasirpintar.data.model.Supplier> = emptyList()

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

        layout.addView(spnSupplier)
        layout.addView(etQty)
        layout.addView(etCost)

        AlertDialog.Builder(this)
            .setTitle("Restock: ${product.name}")
            .setView(layout)
            .setPositiveButton("SIMPAN") { _, _ ->
                val qtyStr = etQty.text.toString()
                if (qtyStr.isNotEmpty()) {
                    val qtyIn = qtyStr.toInt()
                    val newCost = etCost.text.toString().toDoubleOrNull() ?: product.costPrice

                    // Ambil Nama Supplier
                    val selectedPos = spnSupplier.selectedItemPosition
                    val supplierName = if (selectedPos > 0 && supplierList.isNotEmpty()) {
                        supplierList[selectedPos - 1].name
                    } else {
                        product.supplier ?: "-"
                    }

                    // Update Produk
                    val newProduct = product.copy(stock = product.stock + qtyIn, costPrice = newCost, supplier = supplierName)
                    viewModel.update(newProduct)

                    // Simpan History
                    val log = StockLog(
                        timestamp = System.currentTimeMillis(),
                        productName = product.name,
                        supplierName = supplierName,
                        quantity = qtyIn,
                        costPrice = newCost,
                        totalCost = qtyIn * newCost,
                        type = "IN"
                    )
                    viewModel.recordPurchase(log)

                    Toast.makeText(this, "Stok Masuk!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}