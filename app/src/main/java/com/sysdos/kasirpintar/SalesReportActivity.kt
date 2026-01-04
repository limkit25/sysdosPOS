package com.sysdos.kasirpintar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
    private var fullList: List<Transaction> = emptyList() // Simpan data untuk Print

    // UI Chart & Summary
    private lateinit var barChart: BarChart
    private lateinit var tvRevenue: TextView
    private lateinit var tvProfit: TextView
    private lateinit var cardProfit: CardView
    private lateinit var btnToggleProfit: ImageView

    // UI Navigasi (Pengganti List Produk)
    private lateinit var btnOpenTopProducts: Button
    private lateinit var btnOpenHistory: Button

    // Variable Sensor Laba
    private var actualProfitValue: Double = 0.0
    private var isProfitVisible: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_report)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 1. INIT VIEW
        tvRevenue = findViewById(R.id.tvTotalRevenue)
        tvProfit = findViewById(R.id.tvTotalProfit)
        cardProfit = findViewById(R.id.cardProfit)
        barChart = findViewById(R.id.chartRevenue)
        btnToggleProfit = findViewById(R.id.btnToggleProfit)

        // Tombol Navigasi
        btnOpenTopProducts = findViewById(R.id.btnOpenTopProducts)
        btnOpenHistory = findViewById(R.id.btnOpenHistory)

        // 2. LISTENERS UTAMA
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnPrintDaily).setOnClickListener { printDailyRecap() }

        // 🔥 PINDAH HALAMAN (INTENT) 🔥
        btnOpenTopProducts.setOnClickListener {
            startActivity(Intent(this, TopProductsActivity::class.java))
        }

        btnOpenHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Toggle Sensor Laba
        btnToggleProfit.setOnClickListener {
            isProfitVisible = !isProfitVisible
            updateProfitDisplay()
        }

        setupChartConfig()

        // 3. PRIVASI KASIR (Sembunyikan Laba)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        if (session.getString("role", "kasir") == "kasir") {
            cardProfit.visibility = View.GONE
        }

        // 4. OBSERVE DATA
        viewModel.allTransactions.observe(this) { transactions ->
            fullList = transactions
            updateSummaryAndChart(transactions)
            // Tidak ada logika hitung produk disini lagi, sudah dipindah!
        }
    }

    // --- LOGIKA CHART & TOTAL ---
    private fun updateSummaryAndChart(list: List<Transaction>) {
        var totalOmzet = 0.0
        var totalLaba = 0.0

        for (trx in list) {
            totalOmzet += trx.totalAmount
            totalLaba += trx.profit
        }

        tvRevenue.text = formatRupiah(totalOmzet)
        actualProfitValue = totalLaba
        updateProfitDisplay()

        updateChartData(list)
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

    private fun updateChartData(transactions: List<Transaction>) {
        val salesMap = LinkedHashMap<String, Double>()
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val calendar = Calendar.getInstance()

        // 7 Hari Terakhir
        calendar.add(Calendar.DAY_OF_YEAR, -6)

        for (i in 0..6) {
            val dateKey = sdf.format(calendar.time)
            salesMap[dateKey] = 0.0
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        for (trx in transactions) {
            val dateKey = sdf.format(Date(trx.timestamp))
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

        barChart.data = BarData(dataSet)
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.notifyDataSetChanged()
        barChart.invalidate()
    }

    // --- LOGIKA PRINT REKAP HARIAN ---
    private fun printDailyRecap() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)

        val todayTrx = fullList.filter { it.timestamp >= calendar.timeInMillis }

        if (todayTrx.isEmpty()) {
            Toast.makeText(this, "Belum ada transaksi hari ini!", Toast.LENGTH_SHORT).show()
            return
        }

        var tTunai=0.0; var tQRIS=0.0; var tTrf=0.0; var tDebit=0.0
        for (t in todayTrx) {
            when(t.paymentMethod) {
                "Tunai"->tTunai+=t.totalAmount
                "QRIS"->tQRIS+=t.totalAmount
                "Transfer"->tTrf+=t.totalAmount
                "Debit"->tDebit+=t.totalAmount
            }
        }
        val tOmzet = tTunai+tQRIS+tTrf+tDebit

        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val mac = prefs.getString("printer_mac", "")
        if (mac.isNullOrEmpty()) {
            Toast.makeText(this, "Printer belum diatur!", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@Thread

                val socket = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket.connect()
                val os = socket.outputStream
                val p = StringBuilder()

                p.append("\u001B\u0061\u0001\u001B\u0045\u0001${prefs.getString("name","Toko")}\u001B\u0045\u0000\n")
                p.append("REKAP HARIAN\n--------------------------------\n")
                p.append("\u001B\u0061\u0000Tgl: ${SimpleDateFormat("dd/MM/yy HH:mm").format(Date())}\n")
                p.append("Total Trx: ${todayTrx.size}\n--------------------------------\n")

                fun r(l:String, v:Double){
                    val vs=formatRupiah(v).replace("Rp ","")
                    val s=32-l.length-vs.length
                    p.append("$l${" ".repeat(if(s>0)s else 1)}$vs\n")
                }

                r("Tunai", tTunai); r("QRIS", tQRIS); r("Transfer", tTrf); r("Debit", tDebit)
                p.append("--------------------------------\n\u001B\u0045\u0001")
                r("TOTAL OMZET", tOmzet)
                p.append("\u001B\u0045\u0000\u001B\u0061\u0001--------------------------------\nDicetak oleh Admin\n\n\n")

                os.write(p.toString().toByteArray()); os.flush(); Thread.sleep(2000); socket.close()
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