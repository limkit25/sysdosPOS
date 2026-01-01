package com.sysdos.kasirpintar.viewmodel

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.R
import com.sysdos.kasirpintar.data.model.ShiftLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShiftAdapter : ListAdapter<ShiftLog, ShiftAdapter.ShiftViewHolder>(DiffCallback()) {

    class ShiftViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvType: TextView = itemView.findViewById(R.id.tvShiftType)
        val tvTime: TextView = itemView.findViewById(R.id.tvShiftTime)
        val tvKasir: TextView = itemView.findViewById(R.id.tvShiftKasir)
        val tvActual: TextView = itemView.findViewById(R.id.tvShiftActual)
        val tvDiff: TextView = itemView.findViewById(R.id.tvShiftDiff)
        val rowDiff: LinearLayout = itemView.findViewById(R.id.rowDifference)

        fun bind(log: ShiftLog) {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            tvTime.text = sdf.format(Date(log.timestamp))
            tvKasir.text = log.kasirName
            tvActual.text = formatRupiah(log.actualAmount)

            if (log.type == "OPEN") {
                tvType.text = "BUKA KASIR"
                // [GANTI INI] Pakai Resource biar rounded-nya tetap ada
                tvType.setBackgroundResource(R.drawable.bg_rounded_green)
                rowDiff.visibility = View.GONE
            } else {
                tvType.text = "TUTUP KASIR"
                // [GANTI INI] Pakai Resource Merah
                tvType.setBackgroundResource(R.drawable.bg_rounded_red)

                rowDiff.visibility = View.VISIBLE

                tvDiff.text = formatRupiah(log.difference)
                if (log.difference < 0) {
                    tvDiff.setTextColor(Color.RED)
                    tvDiff.text = "Kurang ${formatRupiah(log.difference)}"
                } else if (log.difference > 0) {
                    tvDiff.setTextColor(Color.BLUE)
                    tvDiff.text = "Lebih +${formatRupiah(log.difference)}"
                } else {
                    tvDiff.setTextColor(Color.parseColor("#4CAF50"))
                    tvDiff.text = "Klop (Sesuai)"
                }
            }
        }

        private fun formatRupiah(number: Double): String {
            return String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShiftViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shift, parent, false)
        return ShiftViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShiftViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<ShiftLog>() {
        override fun areItemsTheSame(oldItem: ShiftLog, newItem: ShiftLog) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ShiftLog, newItem: ShiftLog) = oldItem == newItem
    }
}