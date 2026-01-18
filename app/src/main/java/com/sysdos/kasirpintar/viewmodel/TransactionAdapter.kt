package com.sysdos.kasirpintar.viewmodel

import android.graphics.Color
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
    // 🔥 1. TAMBAHAN VARIABEL LONG CLICK
    private var onItemLongClick: ((Transaction) -> Unit)? = null

    // Fungsi Setter
    fun setOnItemClickListener(listener: (Transaction) -> Unit) {
        onItemClick = listener
    }

    fun setOnReprintClickListener(listener: (Transaction) -> Unit) {
        onReprintClick = listener
    }

    // 🔥 2. FUNGSI SETTER BARU (YANG DICARI ERROR TADI)
    fun setOnItemLongClickListener(listener: (Transaction) -> Unit) {
        onItemLongClick = listener
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvId: TextView = itemView.findViewById(R.id.tvTrxId)
        val tvDate: TextView = itemView.findViewById(R.id.tvTrxDate)
        val tvTotal: TextView = itemView.findViewById(R.id.tvTrxTotal)
        val btnPrint: Button = itemView.findViewById(R.id.btnReprint)

        fun bind(
            trx: Transaction,
            itemClickListener: ((Transaction) -> Unit)?,
            reprintClickListener: ((Transaction) -> Unit)?,
            // 🔥 3. TAMBAHKAN PARAMETER DI BIND
            itemLongClickListener: ((Transaction) -> Unit)?
        ) {
            // FORMAT TANGGAL & HARGA
            val dateStr = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(trx.timestamp))
            tvDate.text = dateStr

            val totalStr = String.format(Locale("id", "ID"), "Rp %,d", trx.totalAmount.toLong())
            tvTotal.text = totalStr

            // LOGIKA WARNA STATUS
            val isPiutang = trx.paymentMethod.lowercase().contains("piutang")
            val isLunas = trx.note.contains("LUNAS", ignoreCase = true)

            if (isPiutang) {
                if (isLunas) {
                    tvId.text = "#TRX-${trx.id} (LUNAS ✅)"
                    tvId.setTextColor(Color.parseColor("#2E7D32"))
                    btnPrint.text = "🖨️"
                    btnPrint.setBackgroundColor(Color.parseColor("#6200EE"))
                } else {
                    tvId.text = "#TRX-${trx.id} (BELUM LUNAS ⏳)"
                    tvId.setTextColor(Color.RED)
                    btnPrint.text = "💰"
                    btnPrint.setBackgroundColor(Color.parseColor("#FF9800"))
                }
            } else {
                tvId.text = "#TRX-${trx.id}"
                tvId.setTextColor(Color.BLACK)
                btnPrint.text = "🖨️"
                btnPrint.setBackgroundColor(Color.parseColor("#6200EE"))
            }

            // KLIK PRINT
            btnPrint.setOnClickListener { reprintClickListener?.invoke(trx) }

            // KLIK BARIS (DETAIL)
            itemView.setOnClickListener { itemClickListener?.invoke(trx) }

            // 🔥 4. PASANG LONG CLICK LISTENER (TEKAN LAMA)
            itemView.setOnLongClickListener {
                itemLongClickListener?.invoke(trx)
                true // Return true artinya event sudah ditangani
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        // 🔥 5. LEMPAR LISTENER KE HOLDER
        holder.bind(getItem(position), onItemClick, onReprintClick, onItemLongClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(old: Transaction, new: Transaction) = old.id == new.id
        override fun areContentsTheSame(old: Transaction, new: Transaction) =
            old == new && old.note == new.note
    }
}