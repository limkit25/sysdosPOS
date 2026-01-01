package com.sysdos.kasirpintar.viewmodel

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

class TransactionAdapter : ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(DiffCallback()) {

    // Variabel Penampung Aksi Klik
    private var onItemClick: ((Transaction) -> Unit)? = null
    private var onReprintClick: ((Transaction) -> Unit)? = null

    // Fungsi Setter untuk Klik Baris (Detail)
    fun setOnItemClickListener(listener: (Transaction) -> Unit) {
        onItemClick = listener
    }

    // Fungsi Setter untuk Klik Tombol Print (Reprint Langsung)
    fun setOnReprintClickListener(listener: (Transaction) -> Unit) {
        onReprintClick = listener
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvId: TextView = itemView.findViewById(R.id.tvTrxId)
        val tvDate: TextView = itemView.findViewById(R.id.tvTrxDate)
        val tvTotal: TextView = itemView.findViewById(R.id.tvTrxTotal)
        val btnPrint: Button = itemView.findViewById(R.id.btnReprint)

        fun bind(
            trx: Transaction,
            itemClickListener: ((Transaction) -> Unit)?,
            reprintClickListener: ((Transaction) -> Unit)?
        ) {
            tvId.text = "#TRX-${trx.id}"

            val dateStr = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(trx.timestamp))
            tvDate.text = dateStr

            val totalStr = String.format(Locale("id", "ID"), "Rp %,d", trx.totalAmount.toLong())
            tvTotal.text = totalStr

            // LOGIKA KLIK TOMBOL PRINTER (Kecil di kanan)
            btnPrint.setOnClickListener {
                reprintClickListener?.invoke(trx)
            }

            // LOGIKA KLIK BARIS (Lihat Detail)
            itemView.setOnClickListener {
                itemClickListener?.invoke(trx)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onReprintClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(old: Transaction, new: Transaction) = old.id == new.id
        override fun areContentsTheSame(old: Transaction, new: Transaction) = old == new
    }
}