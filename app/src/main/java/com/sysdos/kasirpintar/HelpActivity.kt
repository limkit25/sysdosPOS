package com.sysdos.kasirpintar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvHelpTopics)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = HelpAdapter(getHelpData())
    }

    private fun getHelpData(): List<HelpTopic> {
        return listOf(
            HelpTopic(
                "Cara Menambah Produk",
                """
                1. Buka Menu 'Produk' di bagian bawah.
                2. Tekan tombol (+) di pojok kanan bawah.
                3. Isi Nama Produk, Harga Jual, dan Stok awal.
                4. Jika ada Barcode, Anda bisa scan langsung.
                5. Tekan tombol 'SIMPAN' di bagian bawah.
                """.trimIndent()
            ),
            HelpTopic(
                "Cara Menambah Varian (Warna/Ukuran)",
                """
                1. Saat menambah produk, centang kotak 'Punya Varian?'.
                2. Kolom harga jual utama akan terkunci (karena harga ikut varian).
                3. Klik tombol 'TAMBAH VARIAN'.
                4. Isi Nama Varian (misal: "Pedas", "Manis", "Size L").
                5. Isi Harga Khusus untuk varian tersebut.
                6. Ulangi untuk varian lainnya, lalu SIMPAN.
                """.trimIndent()
            ),
            HelpTopic(
                "Cara Belanja Stok (Restock)",
                """
                1. Buka Menu 'Pembelian' di bagian bawah.
                2. Cari barang yang ingin dibeli (Ketik nama atau Scan).
                3. Masukkan Jumlah barang yang dibeli dari supplier.
                4. Masukkan Harga Beli (Modal) terbaru jika berubah.
                5. Klik 'Simpan'. Stok otomatis bertambah & tercatat di Laporan Belanja.
                """.trimIndent()
            ),
            HelpTopic(
                "Cara Import Produk dari Excel (CSV)",
                """
                Fitur ini memudahkan Anda memasukkan banyak barang sekaligus.
                1. Buka Menu 'Produk' -> Klik tombol Import (Ikon Panah/CSV).
                2. Pilih 'Download Template CSV'. 
                3. File template akan tersimpan di folder Download HP Anda.
                4. Buka file tersebut (bisa kirim ke Laptop/PC dulu).
                5. Isi data sesuai kolom:
                   - Nama Induk: Kosongkan untuk produk biasa.
                   - Nama Induk: Isi dengan nama produk utama jika baris itu adalah Varian.
                6. Save file sebagai CSV.
                7. Kembali ke Aplikasi, pilih menu Import -> 'Upload File CSV'.
                """.trimIndent()
            ),
            HelpTopic(
                "Cara Transaksi Kasir",
                """
                1. Buka Menu 'Kasir' (Home).
                2. Pilih barang dengan klik foto atau scan barcode.
                3. Atur jumlah (Qty) jika membeli lebih dari satu.
                4. Klik tombol 'BAYAR' di bawah.
                5. Masukkan uang yang diterima dari pelanggan.
                6. Klik 'Proses Transaksi'. Struk akan tercetak (jika printer terhubung).
                """.trimIndent()
            )
        )
    }

    data class HelpTopic(val title: String, val content: String, var isExpanded: Boolean = false)

    inner class HelpAdapter(private val list: List<HelpTopic>) : RecyclerView.Adapter<HelpAdapter.HelpViewHolder>() {

        inner class HelpViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvContent: TextView = itemView.findViewById(R.id.tvContent)
            val ivArrow: ImageView = itemView.findViewById(R.id.ivArrow)
            val layoutContent: LinearLayout = itemView.findViewById(R.id.layoutContent)
            val layoutHeader: LinearLayout = itemView.findViewById(R.id.layoutHeader)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HelpViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_help_topic, parent, false)
            return HelpViewHolder(view)
        }

        override fun onBindViewHolder(holder: HelpViewHolder, position: Int) {
            val item = list[position]
            holder.tvTitle.text = item.title
            holder.tvContent.text = item.content

            val isExpanded = item.isExpanded
            holder.layoutContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.ivArrow.rotation = if (isExpanded) 180f else 0f

            holder.layoutHeader.setOnClickListener {
                item.isExpanded = !item.isExpanded
                notifyItemChanged(position)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
