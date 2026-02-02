package com.sysdos.kasirpintar

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.*

class SalesReportActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private var fullList: List<Transaction> = emptyList()

    // UI Chart & Summary
    private lateinit var barChart: BarChart
    private lateinit var tvRevenue: TextView
    private lateinit var tvProfit: TextView
    private lateinit var tvPiutang: TextView
    private lateinit var cardProfit: CardView
    private lateinit var cardPiutang: CardView // 🔥 Tambahan Binding
    private lateinit var btnToggleProfit: ImageView

    // UI Navigasi
    private lateinit var btnOpenHistory: Button

    // Variable Sensor Laba
    private var actualProfitValue: Double = 0.0
    private var isProfitVisible: Boolean = false
    // Drawer Removed


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_report)

        // =============================================================
        // 🔥 1. SETUP MENU SAMPING (DRAWER) -> REMOVED IN FAVOR OF BOTTOM NAV
        // =============================================================

        // Setup Header & Session (KEEP THIS FOR REUSE IF NEEDED, OR DELETE IF UNUSED)
        val session = getSharedPreferences("session_kasir", android.content.Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")



        // 🔥 SETUP BOTTOM NAV
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java] // Init viewModel dulu sebelum dipake
        com.sysdos.kasirpintar.utils.BottomNavHelper.setup(this, bottomNav, viewModel)

        // =============================================================
        // 🔥 2. LOGIKA LAPORAN (Kodingan Lama)
        // =============================================================
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // INIT VIEW
        tvRevenue = findViewById(R.id.tvTotalRevenue)
        tvProfit = findViewById(R.id.tvTotalProfit)
        tvPiutang = findViewById(R.id.tvTotalPiutang)

        cardProfit = findViewById(R.id.cardProfit)
        cardPiutang = findViewById(R.id.cardPiutang)

        barChart = findViewById(R.id.chartRevenue)
        btnToggleProfit = findViewById(R.id.btnToggleProfit)

        btnOpenHistory = findViewById(R.id.btnOpenHistory)

        // LISTENERS
        // ❌ HAPUS BARIS btnBack INI KARENA SUDAH DIGANTI btnMenuDrawer
        // findViewById<View>(R.id.btnBack).setOnClickListener { finish() }



        findViewById<android.widget.ImageButton>(R.id.btnPrintDaily).setOnClickListener { printDailyRecap() }



        btnOpenHistory.setOnClickListener {
            startActivity(android.content.Intent(this, HistoryActivity::class.java))
        }

        btnToggleProfit.setOnClickListener {
            isProfitVisible = !isProfitVisible
            updateProfitDisplay()
        }

        cardPiutang.setOnClickListener {
            showDebtDetails()
        }

        setupChartConfig()

        // CHART TOGGLE INIT
        btn7Days = findViewById(R.id.btnChart7Days)
        btn30Days = findViewById(R.id.btnChart30Days)
        
        btn7Days.setOnClickListener {
            chartDuration = 7
            updateToggleUI()
            updateChartData(fullList)
        }
        
        btn30Days.setOnClickListener {
            chartDuration = 30
            updateToggleUI()
            updateChartData(fullList)
        }
        updateToggleUI() // Set initial state

        // 3. PRIVASI KASIR (Sembunyikan Laba)
        // Kita pakai variabel 'role' yang sudah diambil di atas supaya tidak double
        // 3. PRIVASI KASIR (Sembunyikan Laba)
        if (role == "kasir") {
            cardProfit.visibility = android.view.View.GONE
            tvProfit.text = "Rp ***" // Default Text
            btnToggleProfit.visibility = android.view.View.GONE // Hilangkan tombol intip
        }

        // 4. OBSERVE DATA
        viewModel.allTransactions.observe(this) { transactions ->
            fullList = transactions
            updateSummaryAndChart(transactions)
        }
    }
    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun updateSummaryAndChart(list: List<Transaction>) {
        var totalOmzet = 0.0
        var totalLaba = 0.0
        var totalPiutang = 0.0

        for (trx in list) {
            totalOmzet += trx.totalAmount
            totalLaba += trx.profit

            if (trx.paymentMethod.equals("PIUTANG", ignoreCase = true)) {
                totalPiutang += trx.totalAmount
            }
        }

        tvRevenue.text = formatRupiah(totalOmzet)
        tvPiutang.text = formatRupiah(totalPiutang)

        actualProfitValue = totalLaba
        updateProfitDisplay()

        updateChartData(list)
    }

    // 🔥 POPUP RINCIAN PIUTANG
    private fun showDebtDetails() {
        // 1. Ambil hanya data PIUTANG & Urutkan dari yang terbaru
        val debtList = fullList.filter {
            it.paymentMethod.equals("PIUTANG", ignoreCase = true)
        }.sortedByDescending { it.timestamp }

        if (debtList.isEmpty()) {
            Toast.makeText(this, "Alhamdulillah, tidak ada yang hutang! 🤲", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Format Data untuk Ditampilkan (Nama, Tanggal, Nominal)
        val listItems = debtList.map { trx ->
            val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(trx.timestamp))

            // Coba ambil nama dari Catatan (karena kita simpan format " | An: Budi")
            var namaPelanggan = "Tanpa Nama"
            if (trx.note.contains("An:")) {
                namaPelanggan = trx.note.substringAfter("An:").trim()
            } else if (trx.note.isNotEmpty()) {
                namaPelanggan = trx.note // Pakai catatan full kalau format beda
            }

            // Format Tampilan Baris
            "👤 $namaPelanggan\n📅 $date  •  💰 ${formatRupiah(trx.totalAmount)}"
        }.toTypedArray()

        // 3. Tampilkan Alert Dialog List
        AlertDialog.Builder(this)
            .setTitle("📋 Daftar Piutang (${debtList.size})")
            .setItems(listItems) { _, which ->
                // Nanti bisa diklik untuk pelunasan (Next Project)
                val selectedTrx = debtList[which]
                Toast.makeText(this, "ID Trx: ${selectedTrx.id}", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("TUTUP", null)
            .show()
    }

    private fun updateProfitDisplay() {
        if (isProfitVisible) {
            tvProfit.text = formatRupiah(actualProfitValue)
            btnToggleProfit.setImageResource(android.R.drawable.ic_menu_view)
        } else {
            tvProfit.text = "Rp *******"
            btnToggleProfit.setImageResource(android.R.drawable.ic_secure)
        }
    }

    private fun setupChartConfig() {
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.setDrawGridLines(false)
        barChart.xAxis.textColor = Color.BLACK
        barChart.axisLeft.textColor = Color.BLACK
        barChart.axisLeft.axisMinimum = 0f
    }

    // UI Chart Toggle
    private lateinit var btn7Days: TextView
    private lateinit var btn30Days: TextView
    private var chartDuration = 7



    private fun updateToggleUI() {
        if (chartDuration == 7) {
            btn7Days.setBackgroundResource(R.drawable.bg_toggle_selected)
            btn7Days.setTextColor(Color.WHITE)
            btn30Days.background = null
            btn30Days.setTextColor(Color.parseColor("#757575"))
        } else {
            btn30Days.setBackgroundResource(R.drawable.bg_toggle_selected)
            btn30Days.setTextColor(Color.WHITE)
            btn7Days.background = null
            btn7Days.setTextColor(Color.parseColor("#757575"))
        }
    }

    private fun updateChartData(transactions: List<Transaction>) {
        val salesMap = LinkedHashMap<String, Double>()
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val calendar = Calendar.getInstance()

        // Mundur ke belakang sebanyak (chartDuration - 1) hari
        calendar.add(Calendar.DAY_OF_YEAR, -(chartDuration - 1))

        // Siapkan map kosong utk setiap tanggal
        for (i in 0 until chartDuration) {
            val dateKey = sdf.format(calendar.time)
            salesMap[dateKey] = 0.0
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Isi data transaksi
        val formatCheck = SimpleDateFormat("dd/MM", Locale.getDefault()) // Pastikan format sama
        for (trx in transactions) {
            val dateKey = formatCheck.format(Date(trx.timestamp))
            if (salesMap.containsKey(dateKey)) {
                salesMap[dateKey] = salesMap[dateKey]!! + trx.totalAmount
            }
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        var index = 0f

        for ((date, amount) in salesMap) {
            entries.add(BarEntry(index, amount.toFloat()))
            labels.add(date)
            index++
        }

        val dataSet = BarDataSet(entries, "Omzet")
        dataSet.color = Color.parseColor("#1976D2")
        dataSet.valueTextSize = 10f

        barChart.data = BarData(dataSet)
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        
        // 🔥 OPTIMASI TAMPILAN 30 HARI
        if (chartDuration > 7) {
            // Agar tidak desak-desakan
            barChart.setVisibleXRangeMaximum(7f) // Tampilkan max 7 bar, sisanya scroll
            barChart.moveViewToX(entries.size.toFloat()) // Scroll ke paling kanan (hari ini)
        } else {
            barChart.fitScreen() // Reset zoom
        }
        
        barChart.notifyDataSetChanged()
        barChart.invalidate()
    }

    private fun printDailyRecap() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val todayTrx = fullList.filter { it.timestamp >= calendar.timeInMillis }

        if (todayTrx.isEmpty()) {
            Toast.makeText(this, "Belum ada transaksi hari ini!", Toast.LENGTH_SHORT).show()
            return
        }

        var tTunai = 0.0
        var tQRIS = 0.0
        var tTrf = 0.0
        var tDebit = 0.0
        var tPiutang = 0.0

        for (t in todayTrx) {
            when (t.paymentMethod.uppercase()) {
                "TUNAI" -> tTunai += t.totalAmount
                "QRIS" -> tQRIS += t.totalAmount
                "TRANSFER" -> tTrf += t.totalAmount
                "DEBIT" -> tDebit += t.totalAmount
                "PIUTANG" -> tPiutang += t.totalAmount
                else -> tTunai += t.totalAmount
            }
        }
        val tOmzet = tTunai + tQRIS + tTrf + tDebit + tPiutang

        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val mac = prefs.getString("printer_mac", "")
        if (mac.isNullOrEmpty()) {
            Toast.makeText(this, "Printer belum diatur!", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@Thread

                val socket = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)
                    .createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket.connect()
                val os = socket.outputStream
                val p = StringBuilder()

                p.append("\u001B\u0061\u0001\u001B\u0045\u0001${prefs.getString("name", "Toko")}\u001B\u0045\u0000\n")
                p.append("REKAP HARIAN\n--------------------------------\n")
                p.append("\u001B\u0061\u0000Tgl: ${SimpleDateFormat("dd/MM/yy HH:mm").format(Date())}\n")
                p.append("Total Trx: ${todayTrx.size}\n--------------------------------\n")

                fun r(l: String, v: Double) {
                    val vs = formatRupiah(v).replace("Rp ", "")
                    val s = 32 - l.length - vs.length
                    p.append("$l${" ".repeat(if (s > 0) s else 1)}$vs\n")
                }

                r("Tunai", tTunai)
                r("QRIS", tQRIS)
                r("Transfer", tTrf)
                r("Debit", tDebit)
                if (tPiutang > 0) r("PIUTANG (BON)", tPiutang) // Cetak Bon juga

                p.append("--------------------------------\n\u001B\u0045\u0001")
                r("TOTAL OMZET", tOmzet)
                p.append("\u001B\u0045\u0000\u001B\u0061\u0001--------------------------------\nDicetak oleh Admin\n\n\n")

                os.write(p.toString().toByteArray())
                os.flush()
                Thread.sleep(2000)
                socket.close()
                runOnUiThread { Toast.makeText(this, "Rekap Tercetak!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Gagal Print", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun formatRupiah(number: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')
    }
}