package com.sysdos.kasirpintar.viewmodel

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.R
import com.sysdos.kasirpintar.data.model.Product
import java.util.Locale

class StockReportAdapter : RecyclerView.Adapter<StockReportAdapter.ViewHolder>() {

    private var list = listOf<Product>()

    fun submitList(newList: List<Product>) {
        list = newList
        notifyDataSetChanged()
    }

    // Gunakan XML layout yang baru dibuat
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvItemName)
        val tvStock: TextView = view.findViewById(R.id.tvItemStock)
        val tvAsset: TextView = view.findViewById(R.id.tvItemAsset)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflate layout XML item_stock_report
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        // 1. Nama Barang
        holder.tvName.text = item.name

        // 2. Stok (Warna Merah jika Menipis)
        if (item.stock < 5) {
            holder.tvStock.text = "${item.stock} (!)"
            holder.tvStock.setTextColor(Color.RED)
        } else {
            holder.tvStock.text = "${item.stock}"
            holder.tvStock.setTextColor(Color.BLACK)
        }

        // 3. Nilai Aset (Format Rupiah)
        val assetValue = item.stock * item.costPrice
        holder.tvAsset.text = formatRupiah(assetValue)
    }

    override fun getItemCount(): Int = list.size

    private fun formatRupiah(number: Double): String {
        return String.format(Locale("id", "ID"), "%,d", number.toLong()).replace(',', '.')
    }
}