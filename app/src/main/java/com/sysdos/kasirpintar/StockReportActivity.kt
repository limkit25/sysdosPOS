package com.sysdos.kasirpintar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.StockReportAdapter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockReportActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: StockReportAdapter
    private var fullList: List<Product> = emptyList()
    private var filteredList: List<Product> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_report)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // SETUP VIEWS
        val tvTotalAsset = findViewById<TextView>(R.id.tvTotalAsset)
        val tvTotalQty = findViewById<TextView>(R.id.tvTotalQty)
        val spnFilter = findViewById<Spinner>(R.id.spnStockFilter)
        val btnDownload = findViewById<Button>(R.id.btnDownloadReport)
        val rvList = findViewById<RecyclerView>(R.id.rvStockList)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // SETUP RECYCLERVIEW
        adapter = StockReportAdapter()
        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        // SETUP SPINNER
        val filters = arrayOf("Semua Barang", "⚠️ Stok Menipis (<5)", "Stok Habis (0)", "Aset Tertinggi")
        val spinAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filters)
        spnFilter.adapter = spinAdapter

        // OBSERVE DATA
        viewModel.allProducts.observe(this) { products ->
            fullList = products
            applyFilter(spnFilter.selectedItemPosition)
        }

        // LISTENER FILTER
        spnFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                applyFilter(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // LISTENER TOMBOL
        btnBack.setOnClickListener { finish() }
        btnDownload.setOnClickListener { exportToCSV() }

        // FUNGSI UPDATE UI RINGKASAN
        fun updateSummary() {
            var totalAsset = 0.0
            var totalQty = 0

            for (p in fullList) { // Hitung dari SEMUA data, bukan yang difilter
                totalAsset += (p.stock * p.costPrice)
                totalQty += p.stock
            }

            tvTotalAsset.text = String.format(Locale("id", "ID"), "Rp %,d", totalAsset.toLong())
            tvTotalQty.text = "$totalQty Unit"
        }

        // AGAR SUMMARY TERUPDATE SAAT DATA MASUK
        viewModel.allProducts.observe(this) {
            fullList = it
            updateSummary()
            applyFilter(spnFilter.selectedItemPosition)
        }
    }

    private fun applyFilter(mode: Int) {
        filteredList = when (mode) {
            0 -> fullList // Semua
            1 -> fullList.filter { it.stock < 5 && it.stock > 0 } // Menipis
            2 -> fullList.filter { it.stock == 0 } // Habis
            3 -> fullList.sortedByDescending { it.stock * it.costPrice } // Aset Tertinggi
            else -> fullList
        }
        adapter.submitList(filteredList)
    }

    private fun exportToCSV() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "Data kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "Laporan_Stok_${System.currentTimeMillis()}.csv"
            val file = File(cacheDir, fileName)
            val writer = FileWriter(file)

            writer.append("Nama Barang,Kategori,Stok Fisik,Harga Modal,Nilai Aset\n")

            for (p in filteredList) {
                val asset = (p.stock * p.costPrice).toLong()
                writer.append("${p.name},${p.category},${p.stock},${p.costPrice.toLong()},${asset}\n")
            }

            writer.flush()
            writer.close()

            // Share File
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/csv"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Laporan Stok Barang")
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Buka Excel..."))

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}