package com.sysdos.kasirpintar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView // Tambahan Import
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// IMPORT CHART
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.TransactionAdapter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.content.Intent // <--- Wajib ada
import androidx.core.content.FileProvider // <--- Wajib ada
import java.io.File // <--- Wajib ada
import java.io.FileWriter // <--- Wajib ada

class SalesReportActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: TransactionAdapter
    private var fullTransactionList: List<Transaction> = emptyList()

    // CHART
    private lateinit var barChart: BarChart

    // UI COMPONENTS
    private lateinit var btnToday: Button
    private lateinit var btnWeek: Button
    private lateinit var btnMonth: Button
    private lateinit var btnAll: Button
    private lateinit var tvRevenue: TextView
    private lateinit var tvProfit: TextView
    private lateinit var cardProfit: CardView

    // --- 🔥 VARIABEL SENSOR LABA 🔥 ---
    private var actualProfitValue: Double = 0.0
    private var isProfitVisible: Boolean = false // Default Tertutup (Disensor)
    private lateinit var btnToggleProfit: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_report)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // INIT VIEW
        tvRevenue = findViewById(R.id.tvTotalRevenue)
        tvProfit = findViewById(R.id.tvTotalProfit)
        cardProfit = findViewById(R.id.cardProfit)
        barChart = findViewById(R.id.chartRevenue)

        // TOMBOL MATA (SENSOR)
        btnToggleProfit = findViewById(R.id.btnToggleProfit)

        btnToday = findViewById(R.id.btnFilterToday)
        btnWeek = findViewById(R.id.btnFilterWeek)
        btnMonth = findViewById(R.id.btnFilterMonth)
        btnAll = findViewById(R.id.btnFilterAll)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnPrintDaily).setOnClickListener { printDailyRecap() }
        findViewById<ImageButton>(R.id.btnExport).setOnClickListener {
            exportToCSV()
        }

        // --- 🔥 LOGIKA KLIK MATA (BUKA/TUTUP SENSOR) 🔥 ---
        btnToggleProfit.setOnClickListener {
            isProfitVisible = !isProfitVisible // Balik status
            updateProfitDisplay() // Update tampilan teks
        }

        // SETUP CHART
        setupChartConfig()

        // --- CEK ROLE: SEMBUNYIKAN KARTU LABA DARI KASIR ---
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val role = session.getString("role", "kasir")

        if (role == "kasir") {
            cardProfit.visibility = View.GONE
        } else {
            cardProfit.visibility = View.VISIBLE
        }

        // SETUP RECYCLERVIEW
        val rvList = findViewById<RecyclerView>(R.id.rvSalesHistory)
        rvList.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter()
        rvList.adapter = adapter

        // OBSERVE DATA
        viewModel.allTransactions.observe(this) { transactions ->
            fullTransactionList = transactions
            filterList("WEEK") // Default
            updateFilterUI(btnWeek)
        }

        // SEARCH
        val svSearch = findViewById<androidx.appcompat.widget.SearchView>(R.id.svSearchHistory)
        svSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    val filtered = fullTransactionList.filter { it.id.toString().contains(newText) }
                    updateAdapterAndSummary(filtered)
                } else {
                    filterList("WEEK"); updateFilterUI(btnWeek)
                }
                return true
            }
        })

        btnToday.setOnClickListener { updateFilterUI(btnToday); filterList("TODAY") }
        btnWeek.setOnClickListener { updateFilterUI(btnWeek); filterList("WEEK") }
        btnMonth.setOnClickListener { updateFilterUI(btnMonth); filterList("MONTH") }
        btnAll.setOnClickListener { updateFilterUI(btnAll); filterList("ALL") }

        adapter.setOnItemClickListener { showDetailDialog(it) }
        adapter.setOnReprintClickListener { doReprint(it) }
    }

    // --- FUNGSI UPDATE TAMPILAN LABA ---
    private fun updateProfitDisplay() {
        if (isProfitVisible) {
            // TAMPILKAN ANGKA ASLI
            tvProfit.text = formatRupiah(actualProfitValue)
            btnToggleProfit.setImageResource(android.R.drawable.ic_menu_view) // Icon Mata Terbuka
        } else {
            // SENSOR
            tvProfit.text = "Rp *******"
            btnToggleProfit.setImageResource(android.R.drawable.ic_secure) // Icon Gembok/Tertutup
        }
    }

    // --- SETUP CHART ---
    private fun setupChartConfig() {
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.setDrawGridLines(false)
        barChart.axisLeft.setDrawGridLines(true)
        barChart.setNoDataText("Memuat Data...")
        barChart.setFitBars(true)
        barChart.animateY(1000)
    }

    private fun filterList(range: String) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)

        val filteredList = when (range) {
            "TODAY" -> fullTransactionList.filter { it.timestamp >= calendar.timeInMillis }
            "WEEK" -> { calendar.add(Calendar.DAY_OF_YEAR, -6); fullTransactionList.filter { it.timestamp >= calendar.timeInMillis } }
            "MONTH" -> { calendar.add(Calendar.DAY_OF_YEAR, -29); fullTransactionList.filter { it.timestamp >= calendar.timeInMillis } }
            else -> fullTransactionList
        }

        updateAdapterAndSummary(filteredList)
        updateChartData(filteredList, range)
    }

    private fun updateChartData(transactions: List<Transaction>, range: String) {
        val salesMap = mutableMapOf<String, Double>()
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())

        val daysBack = if (range == "WEEK") 6 else if (range == "MONTH") 29 else 0
        for (i in daysBack downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = sdf.format(cal.time)
            salesMap[key] = 0.0
        }

        for (trx in transactions) {
            val key = sdf.format(Date(trx.timestamp))
            if (salesMap.containsKey(key)) {
                salesMap[key] = salesMap[key]!! + trx.totalAmount
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
        dataSet.valueTextColor = Color.BLACK

        val data = BarData(dataSet)
        data.barWidth = 0.6f

        barChart.data = data
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.xAxis.labelCount = labels.size
        barChart.invalidate()
    }

    private fun updateAdapterAndSummary(list: List<Transaction>) {
        adapter.submitList(list)
        var totalOmzet = 0.0; var totalLaba = 0.0
        for (trx in list) { totalOmzet += trx.totalAmount; totalLaba += trx.profit }

        tvRevenue.text = formatRupiah(totalOmzet)

        // 🔥 UPDATE VARIABEL SENSOR LABA 🔥
        actualProfitValue = totalLaba
        updateProfitDisplay() // Panggil fungsi update agar status sensor terjaga
    }

    private fun updateFilterUI(activeBtn: Button) {
        val allBtns = listOf(btnToday, btnWeek, btnMonth, btnAll)
        for (btn in allBtns) {
            btn.setBackgroundColor(Color.TRANSPARENT)
            btn.setTextColor(Color.GRAY)
        }
        activeBtn.setBackgroundColor(Color.parseColor("#E3F2FD"))
        activeBtn.setTextColor(Color.parseColor("#1976D2"))
    }

    // --- DIALOG DETAIL ---
    private fun showDetailDialog(trx: Transaction) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_transaction_detail, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialogView.findViewById<View>(R.id.btnCloseDetail)
        val btnReprint = dialogView.findViewById<Button>(R.id.btnReprint)
        val llItems = dialogView.findViewById<LinearLayout>(R.id.llDetailItems)

        val tvId = dialogView.findViewById<TextView>(R.id.tvDetailId)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvDetailDate)
        val tvMethod = dialogView.findViewById<TextView>(R.id.tvDetailMethod)
        val tvSub = dialogView.findViewById<TextView>(R.id.tvDetailSubtotal)
        val tvDisc = dialogView.findViewById<TextView>(R.id.tvDetailDisc)
        val tvTax = dialogView.findViewById<TextView>(R.id.tvDetailTax)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvDetailTotal)
        val rowDisc = dialogView.findViewById<View>(R.id.rowDetailDisc)
        val rowTax = dialogView.findViewById<View>(R.id.rowDetailTax)

        tvId.text = "#TRX-${trx.id}"
        tvDate.text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(trx.timestamp))

        if (trx.paymentMethod == "Tunai") {
            tvMethod.text = "Tunai (Bayar: ${formatRupiah(trx.cashReceived)})"
        } else {
            tvMethod.text = "Metode: ${trx.paymentMethod}"
        }

        tvSub.text = formatRupiah(trx.subtotal)

        if (trx.discount > 0) {
            tvDisc.text = "-${formatRupiah(trx.discount)}"
            rowDisc?.visibility = View.VISIBLE
        } else { rowDisc?.visibility = View.GONE }

        if (trx.tax > 0) {
            tvTax.text = "+${formatRupiah(trx.tax)}"
            rowTax?.visibility = View.VISIBLE
        } else { rowTax?.visibility = View.GONE }

        tvTotal.text = formatRupiah(trx.totalAmount)

        llItems.removeAllViews()
        val rawItems = trx.itemsSummary.split(";")
        for (itemStr in rawItems) {
            val parts = itemStr.split("|")
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 16) } }
            if (parts.size >= 4) {
                val layoutLeft = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
                layoutLeft.addView(TextView(this).apply { text = parts[0]; setTypeface(null, Typeface.BOLD); setTextColor(Color.BLACK); textSize = 14f })
                layoutLeft.addView(TextView(this).apply { text = "${parts[1]} x ${formatRupiah(parts[2].toDouble())}"; setTextColor(Color.GRAY); textSize = 12f })
                rowLayout.addView(layoutLeft)
                rowLayout.addView(TextView(this).apply { text = formatRupiah(parts[3].toDouble()); setTypeface(null, Typeface.BOLD); setTextColor(Color.BLACK); textSize = 14f; gravity = Gravity.END })
            }
            llItems.addView(rowLayout)
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        btnReprint.setOnClickListener { doReprint(trx) }
        dialog.show()
    }

    private fun printDailyRecap() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val todayTrx = fullTransactionList.filter { it.timestamp >= calendar.timeInMillis }

        if (todayTrx.isEmpty()) { Toast.makeText(this, "Belum ada transaksi hari ini", Toast.LENGTH_SHORT).show(); return }

        var tTunai=0.0; var tQRIS=0.0; var tTrf=0.0; var tDebit=0.0
        for (t in todayTrx) {
            when(t.paymentMethod) { "Tunai"->tTunai+=t.totalAmount; "QRIS"->tQRIS+=t.totalAmount; "Transfer"->tTrf+=t.totalAmount; "Debit"->tDebit+=t.totalAmount }
        }
        val tOmzet = tTunai+tQRIS+tTrf+tDebit
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val mac = prefs.getString("printer_mac", "")
        if (mac.isNullOrEmpty()) { Toast.makeText(this, "Printer belum diatur!", Toast.LENGTH_SHORT).show(); return }

        Thread {
            try {
                val socket = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket.connect()
                val os = socket.outputStream
                val p = StringBuilder()
                p.append("\u001B\u0061\u0001\u001B\u0045\u0001${prefs.getString("name","Toko")}\u001B\u0045\u0000\nREKAP HARIAN (X-REPORT)\n--------------------------------\n")
                p.append("Tgl: ${SimpleDateFormat("dd/MM/yy HH:mm").format(Date())}\n--------------------------------\n")
                fun r(l:String, v:Double){ val vs=formatRupiah(v).replace("Rp ",""); val s=32-l.length-vs.length; p.append("$l${" ".repeat(if(s>0)s else 1)}$vs\n") }
                r("Tunai", tTunai); r("QRIS", tQRIS); r("Transfer", tTrf); r("Debit", tDebit)
                p.append("--------------------------------\n\u001B\u0045\u0001"); r("TOTAL", tOmzet); p.append("\u001B\u0045\u0000--------------------------------\nTrx: ${todayTrx.size}\n\n\n")
                os.write(p.toString().toByteArray()); os.flush(); Thread.sleep(2000); socket.close()
                runOnUiThread { Toast.makeText(this, "Rekap Tercetak!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Gagal Print", Toast.LENGTH_SHORT).show() } }
        }.start()
    }

    private fun doReprint(trx: Transaction) {
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val targetMac = prefs.getString("printer_mac", "")
        if (targetMac.isNullOrEmpty()) { Toast.makeText(this, "Printer belum diatur!", Toast.LENGTH_SHORT).show(); return }

        Thread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@Thread
                val socket = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(targetMac).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket.connect()
                val os = socket.outputStream

                val p = StringBuilder()
                fun row(k: String, v: Double, m: Boolean = false): String {
                    val f = String.format(Locale("id","ID"), "Rp %,d", v.toLong()).replace(',', '.')
                    val r = if(m) "-$f" else f
                    val max = 32; val maxK = max - r.length - 1
                    val kT = if (k.length > maxK) k.substring(0, maxK) else k
                    val sp = " ".repeat(if(max - kT.length - r.length > 0) max - kT.length - r.length else 1)
                    return "$kT$sp$r\n"
                }

                p.append("\u001B\u0061\u0001\u001B\u0045\u0001${prefs.getString("name", "Toko")}\n\u001B\u0045\u0000${prefs.getString("address", "")}\n")
                if(prefs.getString("phone","")!!.isNotEmpty()) p.append("Telp: ${prefs.getString("phone","")}\n")
                p.append("--------------------------------\n\u001B\u0045\u0001[ COPY / REPRINT ]\u001B\u0045\u0000\n--------------------------------\n")
                p.append("\u001B\u0061\u0000ID: #${trx.id}\nTgl: ${SimpleDateFormat("dd/MM/yy HH:mm").format(Date(trx.timestamp))}\nKasir: ${session.getString("username","Admin")}\n--------------------------------\n")

                val items = trx.itemsSummary.split(";")
                for (item in items) {
                    val parts = item.split("|")
                    if (parts.size >= 4) {
                        p.append("${parts[0]}\n")
                        val pr = parts[2].toDoubleOrNull()?:0.0; val tot = parts[3].toDoubleOrNull()?:0.0
                        p.append(row("  ${parts[1]} x ${formatRupiah(pr).replace("Rp ","")}", tot))
                    }
                }
                p.append("--------------------------------\n")
                p.append(row("Subtotal", trx.subtotal))
                if(trx.discount>0) p.append(row("Diskon", trx.discount, true))
                if(trx.tax>0) p.append(row("Pajak", trx.tax))
                p.append("--------------------------------\n\u001B\u0045\u0001${row("TOTAL", trx.totalAmount)}\u001B\u0045\u0000")
                if(trx.paymentMethod.contains("Tunai")) { p.append(row("Tunai", trx.cashReceived)); p.append(row("Kembali", trx.changeAmount)) } else { p.append("Metode: ${trx.paymentMethod}\n") }
                p.append("\u001B\u0061\u0001--------------------------------\n${prefs.getString("email","Terima Kasih!")}\n\n\n")

                os.write(p.toString().toByteArray()); os.flush(); Thread.sleep(1500); socket.close()
                runOnUiThread { Toast.makeText(this, "Reprint Berhasil! ✅", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun formatRupiah(number: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')
    }
    // --- 🔥 FUNGSI EXPORT KE EXCEL (CSV) 🔥 ---
    private fun exportToCSV() {
        // 1. Ambil data yang sedang tampil (sesuai filter)
        val currentList = adapter.currentList

        if (currentList.isEmpty()) {
            Toast.makeText(this, "Tidak ada data untuk diexport!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 2. Buat Nama File (Laporan_Tgl_Jam.csv)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val fileName = "Laporan_Transaksi_$timeStamp.csv"

            // 3. Simpan di Cache HP dulu (Folder sementara)
            val file = File(cacheDir, fileName)
            val writer = FileWriter(file)

            // 4. Tulis Header Kolom (Judul Atas Excel)
            writer.append("ID Transaksi,Tanggal,Jam,Item Barang,Total Bayar,Laba,Metode Pembayaran\n")

            // 5. Tulis Isi Data
            val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

            for (trx in currentList) {
                val date = sdfDate.format(Date(trx.timestamp))
                val time = sdfTime.format(Date(trx.timestamp))
                // Ganti tanda koma di uang jadi titik agar Excel tidak bingung
                val items = trx.itemsSummary.replace(",", ".").replace(";", " + ")

                writer.append("#${trx.id},")
                writer.append("$date,")
                writer.append("$time,")
                writer.append("\"$items\",") // Pakai tanda kutip biar aman
                writer.append("${trx.totalAmount},")
                writer.append("${trx.profit},")
                writer.append("${trx.paymentMethod}\n")
            }

            writer.flush()
            writer.close()

            // 6. BAGIKAN FILE (Share Intent)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/csv"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Laporan Penjualan $timeStamp")
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(Intent.createChooser(intent, "Kirim Laporan Ke..."))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}