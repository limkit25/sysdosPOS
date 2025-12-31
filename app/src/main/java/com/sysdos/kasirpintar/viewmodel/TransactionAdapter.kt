package com.sysdos.kasirpintar.viewmodel

import android.R.attr.onClick
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.R
import com.sysdos.kasirpintar.data.model.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    // UNTUK LIHAT DETAIL
    private val onReprintClick: (Transaction) -> Unit // UNTUK PRINT ULANG
) : ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(DiffCallback()) {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvId: TextView = itemView.findViewById(R.id.tvTrxId)
        val tvDate: TextView = itemView.findViewById(R.id.tvTrxDate)
        val tvTotal: TextView = itemView.findViewById(R.id.tvTrxTotal)
        val btnPrint: Button = itemView.findViewById(R.id.btnReprint)

        fun bind(trx: Transaction, onClick: Int, onReprint: (Transaction) -> Unit) {
            tvId.text = "ID: #${trx.id}"
            val dateStr = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(trx.timestamp))
            tvDate.text = dateStr
            tvTotal.text = String.format(Locale("id", "ID"), "Rp %,d", trx.totalAmount.toLong())

            // KLIK TOMBOL PRINTER -> REPRINT
            btnPrint.setOnClickListener { onReprint(trx) }

            // KLIK KARTU -> LIHAT DETAIL
            itemView.setOnClickListener { onClick(trx) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onReprintClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(old: Transaction, new: Transaction) = old.id == new.id
        override fun areContentsTheSame(old: Transaction, new: Transaction) = old == new
    }
}

private fun TransactionAdapter.TransactionViewHolder.onClick(trx: Transaction) {}
