package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.StockReportAdapter // Pastikan adapter ini ada
import java.io.File
import java.io.FileWriter
import java.util.Locale

class ReportFragment : Fragment(R.layout.fragment_tab_report) {

    private lateinit var adapter: StockReportAdapter
    private var fullList: List<Product> = emptyList()
    private var filteredList: List<Product> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewModel = ViewModelProvider(requireActivity())[ProductViewModel::class.java]

        // INIT VIEW
        val tvAsset = view.findViewById<TextView>(R.id.tvTotalAsset)
        val tvQty = view.findViewById<TextView>(R.id.tvTotalQty)
        val spnFilter = view.findViewById<Spinner>(R.id.spnStockFilter)
        val btnDownload = view.findViewById<ImageButton>(R.id.btnDownloadReport)
        val rv = view.findViewById<RecyclerView>(R.id.rvReportList)

        // SETUP RECYCLER
        adapter = StockReportAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // SETUP FILTER (Sama seperti activity lama)
        val options = arrayOf("Semua Barang", "⚠️ Stok Menipis (<5)", "Stok Habis (0)", "Aset Tertinggi")
        val spinAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        spnFilter.adapter = spinAdapter

        // OBSERVE DATA
        viewModel.allProducts.observe(viewLifecycleOwner) { products ->
            fullList = products

            // Hitung Ringkasan
            var totalAsset = 0.0
            var totalQty = 0
            for (p in fullList) {
                totalAsset += (p.stock * p.costPrice)
                totalQty += p.stock
            }
            tvAsset.text = String.format(Locale("id", "ID"), "Rp %,d", totalAsset.toLong())
            tvQty.text = "$totalQty Unit Tersimpan"

            // Terapkan Filter Default
            applyFilter(spnFilter.selectedItemPosition)
        }

        // LISTENER FILTER
        spnFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                applyFilter(pos)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // LISTENER DOWNLOAD
        btnDownload.setOnClickListener {
            exportToCSV()
        }
    }

    private fun applyFilter(mode: Int) {
        filteredList = when (mode) {
            0 -> fullList
            1 -> fullList.filter { it.stock < 5 && it.stock > 0 }
            2 -> fullList.filter { it.stock == 0 }
            3 -> fullList.sortedByDescending { it.stock * it.costPrice }
            else -> fullList
        }
        adapter.submitList(filteredList)
    }

    // FUNGSI EXPORT CSV (Pindahan dari Activity lama)
    private fun exportToCSV() {
        if (filteredList.isEmpty()) {
            Toast.makeText(requireContext(), "Data kosong!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val context = requireContext()
            val fileName = "Laporan_Gudang_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            val writer = FileWriter(file)

            writer.append("Nama Barang,Kategori,Stok,Harga Modal,Nilai Aset\n")
            for (p in filteredList) {
                val asset = (p.stock * p.costPrice).toLong()
                writer.append("${p.name},${p.category},${p.stock},${p.costPrice.toLong()},${asset}\n")
            }
            writer.flush()
            writer.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/csv"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Laporan Stok")
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Bagikan Laporan..."))

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}