package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout // Tambahan
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

    // UI Elements
    private lateinit var tvOmzet: TextView
    private lateinit var tvProfit: TextView
    private lateinit var tvTotalTrx: TextView
    private lateinit var tvDateDisplay: TextView
    private lateinit var spnFilter: Spinner
    private lateinit var btnExport: Button

    private lateinit var cardCashFlow: CardView
    private lateinit var tvModalReport: TextView
    private lateinit var tvTotalCashReport: TextView

    // Elemen yang akan disembunyikan dari Kasir
    private lateinit var cardProfit: CardView
    private lateinit var layoutAdminButtons: LinearLayout // Wadah tombol Modal & Shift

    private var isAdmin = false
    private var allDataTransactions: List<Transaction> = emptyList()
    private var filteredTransactions: List<Transaction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // --- 1. HUBUNGKAN VIEW ---
        tvOmzet = findViewById(R.id.tvOmzetNew)
        tvProfit = findViewById(R.id.tvProfitNew)
        tvTotalTrx = findViewById(R.id.tvTotalTrxNew)
        tvDateDisplay = findViewById(R.id.tvDateDisplay)
        spnFilter = findViewById(R.id.spnFilter)
        btnExport = findViewById(R.id.btnExport)

        cardCashFlow = findViewById(R.id.cardCashFlow)
        tvModalReport = findViewById(R.id.tvModalReport)
        tvTotalCashReport = findViewById(R.id.tvTotalCashReport)

        // Elemen Sensitif
        cardProfit = findViewById(R.id.cardProfitNew)
        layoutAdminButtons = findViewById(R.id.layoutAdminButtons) // ID Baru Wadah Tombol

        // Kartu Transaksi (Untuk diklik)
        val cardTrx = findViewById<CardView>(R.id.cardTotalTrxNew)

        // Tombol Back
        val btnBack = findViewById<ImageButton>(R.id.btnBackReport)
        btnBack.setOnClickListener { finish() }

        // --- 2. CEK ROLE USER & KEAMANAN ---
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val role = session.getString("role", "kasir")?.lowercase()

        isAdmin = (role == "admin" || role == "manager")

        // [PENTING] Sembunyikan Fitur Admin dari Kasir
        if (!isAdmin) {
            cardProfit.visibility = View.GONE        // Sembunyikan Profit
            cardCashFlow.visibility = View.GONE      // Sembunyikan Arus Kas
            btnExport.visibility = View.GONE         // Sembunyikan Download Excel
            layoutAdminButtons.visibility = View.GONE // Sembunyikan Tombol Set Modal & Shift
        }

        // --- 3. KLIK KARTU TRANSAKSI -> LIHAT DETAIL ---
        cardTrx.setOnClickListener {
            // Membuka halaman HistoryActivity (Daftar Riwayat Transaksi)
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Setup Spinner
        val options = arrayOf("Hari Ini", "Bulan Ini", "Semua Waktu")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnFilter.adapter = adapter

        // Observe Data
        viewModel.allTransactions.observe(this) { list ->
            allDataTransactions = list
            calculateReport(spnFilter.selectedItemPosition)
        }

        spnFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculateReport(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Tombol-tombol Admin
        btnExport.setOnClickListener { exportToCSV() }

        findViewById<Button>(R.id.btnSetModal).setOnClickListener { showInputModalDialog() }

        findViewById<Button>(R.id.btnShiftHistory).setOnClickListener {
            startActivity(Intent(this, ShiftHistoryActivity::class.java))
        }
    }

    private fun showInputModalDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Contoh: 200000"

        // ... (Kode Dialog Modal sama seperti sebelumnya) ...

        val prefs = getSharedPreferences("daily_finance", Context.MODE_PRIVATE)
        val oldModal = prefs.getFloat("modal_awal", 0f)
        if (oldModal > 0) input.setText(oldModal.toInt().toString())

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set Modal Awal")
            .setMessage("Masukkan uang tunai di laci sebelum jualan.")
            .setView(input)
            .setPositiveButton("SIMPAN") { _, _ ->
                val str = input.text.toString()
                if (str.isNotEmpty()) {
                    val newVal = str.toFloat()
                    prefs.edit().putFloat("modal_awal", newVal).apply()
                    calculateReport(spnFilter.selectedItemPosition)
                    Toast.makeText(this, "Modal tersimpan!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .create()

        // Style margin input
        val margin = (16 * resources.displayMetrics.density).toInt()
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = margin
        params.rightMargin = margin
        input.layoutParams = params
        val container = android.widget.FrameLayout(this)
        container.addView(input)
        dialog.setView(container)
        dialog.show()
    }

    private fun calculateReport(filterMode: Int) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val now = Date()
        val todayStr = sdfDate.format(now)
        val monthStr = sdfMonth.format(now)
        val displayDateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        val todayDisplay = displayDateFormatter.format(now)

        filteredTransactions = when (filterMode) {
            0 -> {
                tvDateDisplay.text = "Laporan Hari Ini ($todayDisplay)"
                allDataTransactions.filter { sdfDate.format(Date(it.timestamp)) == todayStr }
            }
            1 -> {
                tvDateDisplay.text = "Laporan Bulan Ini"
                allDataTransactions.filter { sdfMonth.format(Date(it.timestamp)) == monthStr }
            }
            else -> {
                tvDateDisplay.text = "Semua Riwayat Transaksi"
                allDataTransactions
            }
        }

        var totalOmzet = 0.0
        var totalProfit = 0.0
        for (trx in filteredTransactions) {
            totalOmzet += trx.totalAmount
            totalProfit += trx.profit
        }

        tvOmzet.text = formatRupiah(totalOmzet)
        tvProfit.text = formatRupiah(totalProfit)
        tvTotalTrx.text = "${filteredTransactions.size}"

        if (filterMode == 0 && isAdmin) {
            cardCashFlow.visibility = View.VISIBLE
            val prefs = getSharedPreferences("daily_finance", Context.MODE_PRIVATE)
            val modal = prefs.getFloat("modal_awal", 0f).toDouble()
            val uangLaci = modal + totalOmzet
            tvModalReport.text = formatRupiah(modal)
            tvTotalCashReport.text = formatRupiah(uangLaci)
        } else {
            cardCashFlow.visibility = View.GONE
        }
    }

    private fun exportToCSV() {
        if (filteredTransactions.isEmpty()) {
            Toast.makeText(this, "Data kosong.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val fileName = "Laporan_${System.currentTimeMillis()}.csv"
            val file = File(cacheDir, fileName)
            val writer = FileWriter(file)
            writer.append("ID,Tanggal,Jam,Metode,Subtotal,Diskon,Pajak,TOTAL,Profit,Items\n")
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            for (trx in filteredTransactions) {
                val dateStr = sdfDate.format(Date(trx.timestamp))
                val timeStr = sdfTime.format(Date(trx.timestamp))
                val rawItems = trx.itemsSummary.split(";")
                val readableItems = rawItems.joinToString("; ") { itemStr ->
                    val parts = itemStr.split("|")
                    if (parts.size == 4) "${parts[0]} (x${parts[1]})" else itemStr
                }
                val cleanDetail = readableItems.replace(",", ".").replace("\n", " ")
                writer.append("${trx.id},${dateStr},${timeStr},${trx.paymentMethod},${trx.subtotal.toLong()},${trx.discount.toLong()},${trx.tax.toLong()},${trx.totalAmount.toLong()},${trx.profit.toLong()},\"$cleanDetail\"\n")
            }
            writer.flush()
            writer.close()
            shareFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_SUBJECT, "Laporan Kasir")
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Buka Excel..."))
    }

    private fun formatRupiah(amount: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", amount.toLong()).replace(',', '.')
    }
}