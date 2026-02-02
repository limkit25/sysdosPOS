package com.sysdos.kasirpintar

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.FileProvider
import com.sysdos.kasirpintar.viewmodel.LogReportAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*


class LogReportActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: LogReportAdapter
    private lateinit var btnVoid: Button
    private lateinit var btnRetur: Button
    private lateinit var btnOpname: Button
    private lateinit var rv: RecyclerView
    // Drawer Removed


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_report)

        // === 1. SETUP MENU SAMPING -> REMOVED ===

        // Setup Header & Navigasi (Session Check kept)
        val session = getSharedPreferences("session_kasir", android.content.Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")


        // INIT VIEW
        btnVoid = findViewById(R.id.btnTabVoid)
        btnRetur = findViewById(R.id.btnTabRetur)
        btnOpname = findViewById(R.id.btnTabOpname)
        rv = findViewById(R.id.rvLogReport)

        // INIT DATA
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]
        // INIT DATA

        adapter = LogReportAdapter { log ->
            showDetailDialog(log)
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // DEFAULT LOAD: VOID
        loadData("VOID")

        // LISTENER
        btnVoid.setOnClickListener { loadData("VOID") }
        btnRetur.setOnClickListener { loadData("OUT") }
        btnOpname.setOnClickListener { loadData("OPNAME") }
        
        findViewById<Button>(R.id.btnExportExcel).setOnClickListener {
            showExportDateDialog()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun loadData(type: String) {
        currentType = type // 🔥 Update Active Tab
        
        // Reset Alpha
        btnVoid.alpha = 0.5f
        btnRetur.alpha = 0.5f
        btnOpname.alpha = 0.5f
        
        // Atur Button Aktif
        when(type) {
            "VOID" -> btnVoid.alpha = 1.0f
            "OUT" -> btnRetur.alpha = 1.0f
            "OPNAME" -> btnOpname.alpha = 1.0f
        }

        viewModel.getLogReport(type).observe(this) { logs ->
            adapter.submitList(logs)
        }
    }

    private fun showDetailDialog(log: com.sysdos.kasirpintar.data.model.StockLog) {
        val dateStr = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
        val formatRupiah = { number: Double -> String.format(java.util.Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.') }

        val title = when(log.type) {
            "VOID" -> "❌ DETAIL VOID"
            "OUT" -> "📦 DETAIL RETUR"
            "OPNAME" -> "📝 DETAIL OPNAME"
            else -> "📄 DETAIL LOG"
        }
        
        // 🔥 TAMPILKAN NOMOR RETUR JIKA ADA
        val noReturInfo = if(log.invoiceNumber.isNotEmpty()) "<b>No. Dokumen:</b> ${log.invoiceNumber}<br>" else ""
        
        var cleanNote = log.supplierName
        var stockInfo = ""

        // 🔥 PARSING UTK DIALOG
        if (log.type == "OPNAME" && log.supplierName.contains("|| Awal:")) {
             try {
                val parts = log.supplierName.split(" || ")
                if (parts.isNotEmpty()) cleanNote = parts[0].replace("SO: ", "")
                
                val partAwal = parts.find { it.startsWith("Awal:") }?.replace("Awal:", "") ?: "-"
                val partAkhir = parts.find { it.startsWith("Akhir:") }?.replace("Akhir:", "") ?: "-"
                
                stockInfo = "<br><b>Stok Awal:</b> $partAwal<br><b>Stok Akhir:</b> $partAkhir"
            } catch (e: Exception) {}
        }
        
        val msg = """
            <b>Waktu:</b> $dateStr<br><br>
            $noReturInfo
            <b>Barang:</b><br>${log.productName}<br><br>
            <b>Selisih/Jml:</b> ${log.quantity} Unit<br>
            <b>Nilai:</b> ${formatRupiah(log.totalCost)}
            $stockInfo<br><br>
            <b>Keterangan:</b><br>$cleanNote
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(android.text.Html.fromHtml(msg, android.text.Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("Tutup", null)
            .show()
    }

    // ==========================================
    // 📊 EXPORT / DOWNLOAD LOGIC
    // ==========================================
    private var currentType = "VOID" // Track active tab

    private fun showExportDateDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val tvInfo = TextView(context).apply { text = "Pilih Rentang Tanggal:"; textSize = 16f; setPadding(0,0,0,20) }
        val btnStartDate = Button(context).apply { text = "Tanggal Awal" }
        val btnEndDate = Button(context).apply { text = "Tanggal Akhir" }

        var startMillis = System.currentTimeMillis()
        var endMillis = System.currentTimeMillis()

        val dateSetListener = { btn: Button, isStart: Boolean ->
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(context, { _, y, m, d ->
                cal.set(y, m, d)
                if(isStart) cal.set(Calendar.HOUR_OF_DAY, 0) else cal.set(Calendar.HOUR_OF_DAY, 23)
                
                val ms = cal.timeInMillis
                if(isStart) startMillis = ms else endMillis = ms
                btn.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnStartDate.setOnClickListener { dateSetListener(btnStartDate, true) }
        btnEndDate.setOnClickListener { dateSetListener(btnEndDate, false) }

        layout.addView(tvInfo); layout.addView(btnStartDate); layout.addView(btnEndDate)

        val title = when(currentType) {
            "VOID" -> "Export Laporan VOID"
            "OUT" -> "Export Laporan RETUR"
            "OPNAME" -> "Export Laporan OPNAME"
            else -> "Export Laporan"
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("DOWNLOAD") { _, _ -> processExport(startMillis, endMillis) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun processExport(start: Long, end: Long) {
        Toast.makeText(this, "Sedang menyiapkan data...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val data = viewModel.getLogsByDateRangeAndType(start, end, currentType)
            if (data.isEmpty()) {
                Toast.makeText(this@LogReportActivity, "Tidak ada data pada periode tersebut", Toast.LENGTH_SHORT).show()
            } else {
                generateCSV(data)
            }
        }
    }

    private fun generateCSV(dataList: List<com.sysdos.kasirpintar.data.model.StockLog>) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            
            val typeName = when(currentType) {
                "VOID" -> "VOID"
                "OUT" -> "RETUR"
                "OPNAME" -> "OPNAME"
                else -> "LOG"
            }
            
            val fileName = "Laporan_${typeName}_$timeStamp.csv"

            val file = File(getExternalFilesDir(null), fileName)
            val writer = FileWriter(file)

            // Header CSV (Updated with STOK_AWAL, STOK_AKHIR)
            writer.append("TANGGAL,JAM,NO_DOKUMEN,NAMA_BARANG,JUMLAH,HARGA_SATUAN,TOTAL_NILAI,STOK_AWAL,STOK_AKHIR,KETERANGAN\n")

            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            for (log in dataList) {
                var cleanNote = log.supplierName
                var stokAwal = "-"
                var stokAkhir = "-"

                // 🔥 PARSING LOGIKA STOK OPNAME
                if (log.type == "OPNAME" && log.supplierName.contains("|| Awal:")) {
                    try {
                        // Format: "SO: Catatan || Awal:10 || Akhir:12"
                        val parts = log.supplierName.split(" || ")
                        if (parts.isNotEmpty()) cleanNote = parts[0].replace("SO: ", "")
                        
                        // Cari bagian Awal & Akhir
                        val partAwal = parts.find { it.startsWith("Awal:") }
                        val partAkhir = parts.find { it.startsWith("Akhir:") }

                        if (partAwal != null) stokAwal = partAwal.replace("Awal:", "")
                        if (partAkhir != null) stokAkhir = partAkhir.replace("Akhir:", "")

                    } catch (e: Exception) {
                        // Fallback jika parsing gagal
                    }
                }

                val date = Date(log.timestamp)
                writer.append("\u0022${dateFormat.format(date)}\u0022,")
                writer.append("\u0022${timeFormat.format(date)}\u0022,")
                writer.append("\u0022${log.invoiceNumber}\u0022,")
                writer.append("\u0022${log.productName}\u0022,")
                writer.append("${log.quantity},")
                writer.append("${log.costPrice.toLong()},")
                writer.append("${log.totalCost.toLong()},")
                
                // 🔥 DATA BARU
                writer.append("\u0022$stokAwal\u0022,")
                writer.append("\u0022$stokAkhir\u0022,")
                
                writer.append("\u0022$cleanNote\u0022\n")
            }

            writer.flush()
            writer.close()

            shareFile(file)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal membuat file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            // 🔥 FIX: Authority harus sama dengan di AndroidManifest (tanpa .fileprovider jika di manifest cuma .provider)
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
            intent.type = "text/csv"
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(android.content.Intent.createChooser(intent, "Bagikan Laporan via:"))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membagikan file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}