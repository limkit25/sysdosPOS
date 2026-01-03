package com.sysdos.kasirpintar

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.sysdos.kasirpintar.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment(R.layout.fragment_tab_history) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = AppDatabase.getDatabase(requireContext())
        val lv = view.findViewById<ListView>(R.id.lvHistory)
        val tvTotal = view.findViewById<TextView>(R.id.tvTotalExpense)

        db.stockLogDao().getAllPurchases().observe(viewLifecycleOwner) { logs ->
            val display = logs.map {
                val d = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(it.timestamp))
                "📅 $d | ${it.supplierName}\n📦 ${it.productName} (+${it.quantity})\n💰 -Rp ${it.totalCost.toInt()}"
            }
            lv.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, display)

            val total = logs.sumOf { it.totalCost }
            tvTotal.text = "Rp ${total.toLong()}"
        }
    }
}