package com.sysdos.kasirpintar

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment(R.layout.fragment_tab_history) {

    private lateinit var viewModel: ProductViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[ProductViewModel::class.java]

        val lvHistory = view.findViewById<ListView>(R.id.lvHistory)
        val tvTotalExpense = view.findViewById<TextView>(R.id.tvTotalExpense)

        // 1. OBSERVE DATA GROUPED (Hanya 1 baris per nota)
        viewModel.purchaseHistory.observe(viewLifecycleOwner) { logs ->
            if (logs.isNullOrEmpty()) {
                // Handle kosong
                lvHistory.adapter = null
                return@observe
            }

            // Tampilkan Ringkasan di List
            val displayList = logs.map { log ->
                val date = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(log.timestamp))
                "🛒 PEMBELIAN: ${log.supplierName}\n📅 $date\n(Klik untuk lihat detail barang)"
            }

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, displayList)
            lvHistory.adapter = adapter

            // KLIK ITEM -> MUNCULKAN DETAIL
            lvHistory.setOnItemClickListener { _, _, position, _ ->
                val selectedLog = logs[position]
                showDetailDialog(selectedLog.purchaseId, selectedLog.supplierName, selectedLog.timestamp)
            }

            // Hitung Total Pengeluaran (Opsional, query sum mungkin lebih akurat tapi ini cukup)
            // Note: Total di sini hanya menghitung 'perwakilan' baris, jadi mungkin tidak akurat untuk total expense global.
            // Sebaiknya buat query khusus @Query("SELECT SUM(totalCost) FROM stock_logs WHERE type='IN'") di DAO untuk total akurat.
            tvTotalExpense.text = "Riwayat Pembelian"
        }
    }

    private fun showDetailDialog(purchaseId: Long, supplier: String, time: Long) {
        // Panggil ViewModel untuk ambil detail items berdasarkan ID
        viewModel.getPurchaseDetails(purchaseId) { details ->

            val sb = StringBuilder()
            var grandTotal = 0.0

            details.forEach { item ->
                val subtotal = item.quantity * item.costPrice
                grandTotal += subtotal

                sb.append("📦 ${item.productName}\n")
                sb.append("   ${item.quantity} x Rp ${item.costPrice.toInt()} = Rp ${subtotal.toInt()}\n")
                sb.append("--------------------------------\n")
            }

            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(time))

            AlertDialog.Builder(requireContext())
                .setTitle("Detail Faktur")
                .setMessage("Supplier: $supplier\nWaktu: $dateStr\n\n$sb\nTOTAL: Rp ${grandTotal.toInt()}")
                .setPositiveButton("Tutup", null)
                .show()
        }
    }
}