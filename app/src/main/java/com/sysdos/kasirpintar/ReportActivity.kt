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

    // UI Elements
    private lateinit var tvOmzet: TextView
    private lateinit var tvProfit: TextView
    private lateinit var tvTotalTrx: TextView
    private lateinit var spnFilter: Spinner
    private lateinit var btnExport: Button

    // UI Elements Arus Kas
    private lateinit var cardCashFlow: CardView
    private lateinit var tvModalReport: TextView
    private lateinit var tvTotalCashReport: TextView

    // --- 1. VARIABEL ADMIN (PENTING) ---
    private var isAdmin = false

    // Data Penampung
    private var allDataTransactions: List<Transaction> = emptyList()
    private var filteredTransactions: List<Transaction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // HUBUNGKAN VIEW
        tvOmzet = findViewById(R.id.tvTotalOmzet)
        tvProfit = findViewById(R.id.tvTotalProfit)
        tvTotalTrx = findViewById(R.id.tvTotalTrx)
        spnFilter = findViewById(R.id.spnFilter)
        btnExport = findViewById(R.id.btnExport)

        cardCashFlow = findViewById(R.id.cardCashFlow)
        tvModalReport = findViewById(R.id.tvModalReport)
        tvTotalCashReport = findViewById(R.id.tvTotalCashReport)

        val cardProfit = findViewById<androidx.cardview.widget.CardView>(R.id.cardProfit)
        val cardTrx = findViewById<androidx.cardview.widget.CardView>(R.id.cardTotalTrx)

        // --- 2. CEK ROLE USER & SET VARIABEL ADMIN ---
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val role = session.getString("role", "kasir")?.lowercase() // lowercase biar aman (Admin/admin sama saja)

        // [PERBAIKAN] Izinkan jika role adalah 'admin' ATAU 'manager'
        isAdmin = (role == "admin" || role == "manager")

        // Kalau BUKAN Admin, sembunyikan elemen sensitif
        if (!isAdmin) {
            cardProfit.visibility = View.GONE
            cardCashFlow.visibility = View.GONE
            btnExport.visibility = View.GONE
        }

        // KLIK KARTU -> HISTORY
        cardTrx.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // SETUP SPINNER
        val options = arrayOf("Hari Ini", "Bulan Ini", "Semua Waktu")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnFilter.adapter = adapter

        // OBSERVE DATA
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

        // TOMBOL DOWNLOAD (CSV/EXCEL)
        btnExport.setOnClickListener {
            exportToCSV()
        }

        // SETUP TOMBOL EDIT MODAL
        val btnSetModal = findViewById<Button>(R.id.btnSetModal)
        btnSetModal.setOnClickListener {
            showInputModalDialog()
        }

        // SETUP TOMBOL RIWAYAT SHIFT
        val btnHistory = findViewById<Button>(R.id.btnShiftHistory)
        btnHistory.setOnClickListener {
            startActivity(Intent(this, ShiftHistoryActivity::class.java))
        }
    }

    // --- FUNGSI POPUP INPUT MODAL ---
    private fun showInputModalDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Masukkan jumlah uang (contoh: 200000)"

        val prefs = getSharedPreferences("daily_finance", Context.MODE_PRIVATE)
        val oldModal = prefs.getFloat("modal_awal", 0f)
        if (oldModal > 0) input.setText(oldModal.toInt().toString())

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set Modal Awal")
            .setMessage("Masukkan jumlah uang tunai yang ada di laci sebelum mulai jualan.")
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

        filteredTransactions = when (filterMode) {
            0 -> allDataTransactions.filter { sdfDate.format(Date(it.timestamp)) == todayStr }
            1 -> allDataTransactions.filter { sdfMonth.format(Date(it.timestamp)) == monthStr }
            else -> allDataTransactions
        }

        var totalOmzet = 0.0
        var totalProfit = 0.0
        for (trx in filteredTransactions) {
            totalOmzet += trx.totalAmount
            totalProfit += trx.profit
        }

        tvOmzet.text = formatRupiah(totalOmzet)
        tvProfit.text = formatRupiah(totalProfit)
        tvTotalTrx.text = "${filteredTransactions.size} Transaksi"

        // --- 3. PROTEKSI LOGIKA TAMPILAN ARUS KAS ---
        // Hanya muncul jika filter "Hari Ini" DAN User adalah ADMIN
        if (filterMode == 0 && isAdmin) {
            cardCashFlow.visibility = View.VISIBLE
            val prefs = getSharedPreferences("daily_finance", Context.MODE_PRIVATE)
            val modal = prefs.getFloat("modal_awal", 0f).toDouble()
            val uangLaci = modal + totalOmzet
            tvModalReport.text = formatRupiah(modal)
            tvTotalCashReport.text = formatRupiah(uangLaci)
        } else {
            // Kalau bukan hari ini, ATAU bukan Admin -> Sembunyikan
            cardCashFlow.visibility = View.GONE
        }
    }

    private fun exportToCSV() {
        if (filteredTransactions.isEmpty()) {
            Toast.makeText(this, "Data kosong, tidak ada yang bisa di-download.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "Laporan_Transaksi_${System.currentTimeMillis()}.csv"
            val file = File(cacheDir, fileName)
            val writer = FileWriter(file)

            writer.append("ID Transaksi,Tanggal,Jam,Metode Bayar,Subtotal,Diskon,Pajak,TOTAL BAYAR,Laba (Profit),Detail Barang\n")

            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            for (trx in filteredTransactions) {
                val dateStr = sdfDate.format(Date(trx.timestamp))
                val timeStr = sdfTime.format(Date(trx.timestamp))

                val rawItems = trx.itemsSummary.split(";")
                val readableItems = rawItems.joinToString("; ") { itemStr ->
                    val parts = itemStr.split("|")
                    if (parts.size == 4) {
                        "${parts[0]} (x${parts[1]})"
                    } else {
                        itemStr
                    }
                }

                val cleanDetail = readableItems.replace(",", ".").replace("\n", " ")

                writer.append("${trx.id},")
                writer.append("$dateStr,")
                writer.append("$timeStr,")
                writer.append("${trx.paymentMethod},")
                writer.append("${trx.subtotal.toLong()},")
                writer.append("${trx.discount.toLong()},")
                writer.append("${trx.tax.toLong()},")
                writer.append("${trx.totalAmount.toLong()},")
                writer.append("${trx.profit.toLong()},")
                writer.append("\"$cleanDetail\"\n")
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
        intent.putExtra(Intent.EXTRA_SUBJECT, "Laporan Penjualan")
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Buka Excel dengan..."))
    }

    private fun formatRupiah(amount: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", amount.toLong()).replace(',', '.')
    }
}