package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    // UI Elements Existing
    private lateinit var tvOmzet: TextView
    private lateinit var tvProfit: TextView
    private lateinit var tvTotalTrx: TextView
    private lateinit var spnFilter: Spinner
    private lateinit var btnExport: Button

    // --- [INJECT] UI Elements Baru (Arus Kas) ---
    private lateinit var cardCashFlow: CardView
    private lateinit var tvModalReport: TextView
    private lateinit var tvTotalCashReport: TextView

    // Data Penampung
    private var allDataTransactions: List<Transaction> = emptyList()
    private var filteredTransactions: List<Transaction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // --- 1. HUBUNGKAN VIEW (BINDING) ---
        tvOmzet = findViewById(R.id.tvTotalOmzet)
        tvProfit = findViewById(R.id.tvTotalProfit)
        tvTotalTrx = findViewById(R.id.tvTotalTrx)
        spnFilter = findViewById(R.id.spnFilter)
        btnExport = findViewById(R.id.btnExport)

        // View Bagian Arus Kas (Modal & Uang Laci)
        cardCashFlow = findViewById(R.id.cardCashFlow)
        tvModalReport = findViewById(R.id.tvModalReport)
        tvTotalCashReport = findViewById(R.id.tvTotalCashReport)

        // Kartu-kartu untuk logika Hide/Show
        // Pastikan di XML sudah ada ID: android:id="@+id/cardProfit" pada CardView Profit
        val cardProfit = findViewById<androidx.cardview.widget.CardView>(R.id.cardProfit)
        val cardTrx = findViewById<androidx.cardview.widget.CardView>(R.id.cardTotalTrx)

        // --- 2. CEK ROLE USER (SENSOR UNTUK KASIR) ---
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val role = session.getString("role", "kasir")

        if (role == "kasir") {
            // KASIR LOGIN: Sembunyikan Rahasia Dapur
            cardProfit.visibility = View.GONE       // Sembunyikan Laba Bersih
            cardCashFlow.visibility = View.GONE     // Sembunyikan Modal Awal
            btnExport.visibility = View.GONE        // Sembunyikan Tombol Download

            // Ubah judul kolom biar lebih relevan buat kasir
            // (Opsional, pastikan ID TextView label omzet ada jika mau diubah)
        }
        // Kalau ADMIN/MANAGER/OWNER, semua tetap VISIBLE (default)

        // --- 3. KLIK KARTU TRANSAKSI KE HISTORY ---
        cardTrx.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // --- 4. SETUP SPINNER (FILTER WAKTU) ---
        val options = arrayOf("Hari Ini", "Bulan Ini", "Semua Waktu")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnFilter.adapter = adapter

        // --- 5. OBSERVE DATA DARI DATABASE ---
        viewModel.allTransactions.observe(this) { list ->
            allDataTransactions = list
            // Hitung ulang saat data masuk
            calculateReport(spnFilter.selectedItemPosition)
        }

        // --- 6. LISTENER SAAT SPINNER DIGANTI ---
        spnFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Panggil fungsi hitung ulang
                calculateReport(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- 7. TOMBOL DOWNLOAD ---
        btnExport.setOnClickListener {
            exportToCSV()
        }
    }

    // --- FUNGSI HITUNG LAPORAN (SUDAH DI-INJECT) ---
    private fun calculateReport(filterMode: Int) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val now = Date()

        val todayStr = sdfDate.format(now)
        val monthStr = sdfMonth.format(now)

        // Filter Data Sesuai Pilihan
        filteredTransactions = when (filterMode) {
            0 -> allDataTransactions.filter { sdfDate.format(Date(it.timestamp)) == todayStr } // Hari Ini
            1 -> allDataTransactions.filter { sdfMonth.format(Date(it.timestamp)) == monthStr } // Bulan Ini
            else -> allDataTransactions // Semua
        }

        // Hitung Total Omzet & Profit
        var totalOmzet = 0.0
        var totalProfit = 0.0

        for (trx in filteredTransactions) {
            totalOmzet += trx.totalAmount
            totalProfit += trx.profit
        }

        // Tampilkan ke Layar (Omzet & Profit)
        tvOmzet.text = formatRupiah(totalOmzet)
        tvProfit.text = formatRupiah(totalProfit)
        tvTotalTrx.text = "${filteredTransactions.size} Transaksi"

        // --- [INJECT] LOGIKA MODAL & ARUS KAS ---
        // Hanya muncul jika Filter = 0 (Hari Ini)
        if (filterMode == 0) {
            cardCashFlow.visibility = View.VISIBLE

            // Ambil Modal dari SharedPreferences
            val prefs = getSharedPreferences("daily_finance", Context.MODE_PRIVATE)
            val modal = prefs.getFloat("modal_awal", 0f).toDouble()

            // Hitung Uang Laci (Modal + Omzet Hari Ini)
            val uangLaci = modal + totalOmzet

            tvModalReport.text = formatRupiah(modal)
            tvTotalCashReport.text = formatRupiah(uangLaci)
        } else {
            // Sembunyikan kalau Bulan Ini / Semua
            cardCashFlow.visibility = View.GONE
        }
    }

    // --- FUNGSI EXPORT CSV (TIDAK BERUBAH) ---
    private fun exportToCSV() {
        if (filteredTransactions.isEmpty()) {
            Toast.makeText(this, "Data kosong, tidak ada yang bisa di-download.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "Laporan_Keuangan_${System.currentTimeMillis()}.csv"
            val file = File(cacheDir, fileName)
            val writer = FileWriter(file)

            writer.append("ID Transaksi,Tanggal,Jam,Metode Bayar,Total Omzet,Laba Bersih,Detail Barang\n")

            val sdf = SimpleDateFormat("yyyy-MM-dd,HH:mm:ss", Locale.getDefault())

            for (trx in filteredTransactions) {
                val dateSplit = sdf.format(Date(trx.timestamp)).split(",")
                val cleanSummary = trx.itemsSummary.replace("\n", " | ")

                writer.append("${trx.id},")
                writer.append("${dateSplit[0]},")
                writer.append("${dateSplit[1]},")
                writer.append("${trx.paymentMethod},")
                writer.append("${trx.totalAmount},")
                writer.append("${trx.profit},")
                writer.append("\"$cleanSummary\"\n")
            }

            writer.flush()
            writer.close()
            shareFile(file)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal membuat laporan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_SUBJECT, "Laporan Keuangan Kasir Pintar")
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Kirim Laporan Ke..."))
    }

    private fun formatRupiah(amount: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", amount.toLong()).replace(',', '.')
    }
}