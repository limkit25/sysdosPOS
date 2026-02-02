package com.sysdos.kasirpintar.viewmodel

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.R
import com.sysdos.kasirpintar.data.model.StockLog
import java.text.SimpleDateFormat
import java.util.*

class LogReportAdapter(private val onItemClick: (StockLog) -> Unit) : RecyclerView.Adapter<LogReportAdapter.LogViewHolder>() {

    private var list = listOf<StockLog>()

    fun submitList(newList: List<StockLog>) {
        list = newList
        notifyDataSetChanged()
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(android.R.id.text1)
        val tvDetail: TextView = itemView.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        // Kita pakai layout bawaan Android biar cepat (Simple List Item 2)
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = list[position]

        // Format Tanggal
        val dateStr = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(log.timestamp))

        // Tampilan Atas (Nama Barang & Qty)
        holder.tvDate.text = "${log.productName}"
        holder.tvDate.setTypeface(null, android.graphics.Typeface.BOLD)

        // Tampilan Bawah (Detail Alasan)
        val status = if(log.type == "VOID") "❌ DIBATALKAN" else "📦 DIRETUR"
        holder.tvDetail.text = "$dateStr | Qty: ${log.quantity} | $status\nKet: ${log.supplierName}"

        // Warna text
        if (log.type == "VOID") {
            holder.tvDetail.setTextColor(Color.RED)
        } else {
            holder.tvDetail.setTextColor(Color.parseColor("#FF9800")) // Orange
        }

        // 🔥 KLIK ITEM -> PANGGIL CALLBACK
        holder.itemView.setOnClickListener { onItemClick(log) }
    }

    override fun getItemCount() = list.size
}