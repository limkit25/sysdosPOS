package com.sysdos.kasirpintar

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.model.StockLog
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PurchaseHistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_history)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val tvTotal = findViewById<TextView>(R.id.tvTotalExpense)
        val listView = findViewById<ListView>(R.id.lvHistory)

        btnBack.setOnClickListener { finish() }

        // 1. OBSERVE DATA RIWAYAT
        // Kita perlu tambah fungsi getPurchaseHistory di ViewModel dulu (Lihat langkah 3)
        // Tapi untuk sekarang kita pakai Logika Manual mengambil dari DAO lewat ViewModel yang ada
        // (Asumsi Bapak sudah update AppDatabase & StockLogDao di langkah sebelumnya)

        // UNTUK SEMENTARA KITA GUNAKAN `viewModel.allProducts` sebagai pemicu,
        // TAPI SEBAIKNYA update ViewModel agar punya `allPurchaseLogs`.

        // Agar rapi, saya buatkan adapter sederhana di sini
        val database = com.sysdos.kasirpintar.data.AppDatabase.getDatabase(this)

        database.stockLogDao().getAllPurchases().observe(this) { logs ->
            // TAMPILKAN LIST
            val displayList = logs.map { log ->
                val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(log.timestamp))
                val totalRp = String.format(Locale("id", "ID"), "%,d", log.totalCost.toLong())

                "📅 $date\n" +
                        "📦 ${log.productName} (+${log.quantity})\n" +
                        "🏢 Supplier: ${log.supplierName}\n" +
                        "💰 Keluar: Rp $totalRp"
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
            listView.adapter = adapter

            // HITUNG TOTAL
            val totalExpense = logs.sumOf { it.totalCost }
            tvTotal.text = String.format(Locale("id", "ID"), "Rp %,d", totalExpense.toLong())
        }
    }
}