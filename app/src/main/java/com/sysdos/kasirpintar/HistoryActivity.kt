package com.sysdos.kasirpintar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.TransactionAdapter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: TransactionAdapter
    private var fullTransactionList: List<Transaction> = emptyList()

    // Tombol UI
    private lateinit var btnToday: Button
    private lateinit var btnWeek: Button
    private lateinit var btnMonth: Button
    private lateinit var btnAll: Button
    private lateinit var btnExport: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // 1. SETUP RECYCLERVIEW
        val rvHistory = findViewById<RecyclerView>(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter()
        rvHistory.adapter = adapter

        // 2. SETUP VIEWMODEL
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 3. SETUP FILTER & EXPORT
        btnToday = findViewById(R.id.btnFilterToday)
        btnWeek = findViewById(R.id.btnFilterWeek)
        btnMonth = findViewById(R.id.btnFilterMonth)
        btnAll = findViewById(R.id.btnFilterAll)
        btnExport = findViewById(R.id.btnExport)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Logic Tombol Export (Hanya untuk Admin)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val role = session.getString("role", "kasir")
        if (role == "kasir") {
            btnExport.visibility = View.GONE
        } else {
            btnExport.visibility = View.VISIBLE
            btnExport.setOnClickListener { exportToCSV() }
        }

        // 4. OBSERVE DATA
        viewModel.allTransactions.observe(this) { transactions ->
            fullTransactionList = transactions
            filterList("TODAY")
            updateFilterUI(btnToday)
        }

        // 5. SEARCH VIEW
        val svSearch = findViewById<androidx.appcompat.widget.SearchView>(R.id.svSearchHistory)
        svSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    val filtered = fullTransactionList.filter { it.id.toString().contains(newText) }
                    adapter.submitList(filtered)
                } else {
                    filterList("TODAY"); updateFilterUI(btnToday)
                }
                return true
            }
        })

        // 6. KLIK FILTER
        btnToday.setOnClickListener { updateFilterUI(btnToday); filterList("TODAY") }
        btnWeek.setOnClickListener { updateFilterUI(btnWeek); filterList("WEEK") }
        btnMonth.setOnClickListener { updateFilterUI(btnMonth); filterList("MONTH") }
        btnAll.setOnClickListener { updateFilterUI(btnAll); filterList("ALL") }

        // 7. KLIK DETAIL & REPRINT
        adapter.setOnItemClickListener { showDetailDialog(it) }
        adapter.setOnReprintClickListener { doReprint(it) }
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
        adapter.submitList(filteredList)
    }

    private fun updateFilterUI(activeBtn: Button) {
        val allBtns = listOf(btnToday, btnWeek, btnMonth, btnAll)
        for (btn in allBtns) {
            btn.setBackgroundColor(Color.WHITE)
            btn.setTextColor(Color.GRAY)
        }
        activeBtn.setBackgroundColor(Color.parseColor("#1976D2")) // Biru
        activeBtn.setTextColor(Color.WHITE)
    }

    // --- FITUR EXPORT ---
    private fun exportToCSV() {
        val currentList = adapter.currentList
        if (currentList.isEmpty()) { Toast.makeText(this, "Data kosong!", Toast.LENGTH_SHORT).show(); return }
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val file = File(cacheDir, "Laporan_$timeStamp.csv")
            val writer = FileWriter(file)
            writer.append("ID,Tanggal,Jam,Total,Metode,Laba\n")
            for(t in currentList) {
                val d = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(t.timestamp))
                val tm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.timestamp))
                writer.append("#${t.id},$d,$tm,${t.totalAmount},${t.paymentMethod},${t.profit}\n")
            }
            writer.flush(); writer.close()
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply { type="text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(Intent.createChooser(intent, "Kirim Excel..."))
        } catch (e: Exception) { Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    // --- DIALOG DETAIL ---
    private fun showDetailDialog(trx: Transaction) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_transaction_detail, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialogView.findViewById<View>(R.id.btnCloseDetail)
        val btnReprint = dialogView.findViewById<Button>(R.id.btnReprint)
        val llItems = dialogView.findViewById<LinearLayout>(R.id.llDetailItems)

        dialogView.findViewById<TextView>(R.id.tvDetailId).text = "#TRX-${trx.id}"
        dialogView.findViewById<TextView>(R.id.tvDetailDate).text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(trx.timestamp))
        dialogView.findViewById<TextView>(R.id.tvDetailTotal).text = formatRupiah(trx.totalAmount)
        dialogView.findViewById<TextView>(R.id.tvDetailMethod).text = if (trx.paymentMethod == "Tunai") "Tunai (Bayar: ${formatRupiah(trx.cashReceived)})" else "Metode: ${trx.paymentMethod}"
        dialogView.findViewById<TextView>(R.id.tvDetailSubtotal).text = formatRupiah(trx.subtotal)

        // Pajak & Diskon
        val rowDisc = dialogView.findViewById<View>(R.id.rowDetailDisc)
        val rowTax = dialogView.findViewById<View>(R.id.rowDetailTax)

        if (trx.discount > 0) {
            dialogView.findViewById<TextView>(R.id.tvDetailDisc).text = "-${formatRupiah(trx.discount)}"
            rowDisc.visibility = View.VISIBLE
        } else rowDisc.visibility = View.GONE

        if (trx.tax > 0) {
            dialogView.findViewById<TextView>(R.id.tvDetailTax).text = "+${formatRupiah(trx.tax)}"
            rowTax.visibility = View.VISIBLE
        } else rowTax.visibility = View.GONE

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

    // --- 🔥 LOGIC REPRINT LENGKAP (FULL VERSION) 🔥 ---
    private fun doReprint(trx: Transaction) {
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val targetMac = prefs.getString("printer_mac", "")

        if (targetMac.isNullOrEmpty()) {
            Toast.makeText(this, "Printer belum diatur! Cek Pengaturan.", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                // Cek Izin Bluetooth (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@Thread

                val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(targetMac)
                val uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                val os = socket.outputStream

                val p = StringBuilder()

                // Helper bikin baris rapi (Kiri - Kanan)
                fun row(k: String, v: Double, m: Boolean = false): String {
                    val f = String.format(Locale("id","ID"), "Rp %,d", v.toLong()).replace(',', '.')
                    val r = if(m) "-$f" else f
                    val max = 32 // Lebar kertas 58mm (32 char)
                    val maxK = max - r.length - 1
                    val kT = if (k.length > maxK) k.substring(0, maxK) else k
                    val sp = " ".repeat(if(max - kT.length - r.length > 0) max - kT.length - r.length else 1)
                    return "$kT$sp$r\n"
                }

                // ==========================================
                // 🔥 LOGIKA PARSING DATA (SAMA DGN PRINT STRUK) 🔥
                // ==========================================

                // 1. Pisahkan Data Barang dan Info Pelanggan
                val summaryParts = trx.itemsSummary.split(" || ")
                val rawItems = summaryParts[0].split(";")
                val infoPelanggan = if (summaryParts.size > 1) summaryParts[1] else ""

                // ==========================================
                // 🖨️ MULAI CETAK HEADER
                // ==========================================

                // 1. HEADER TOKO
                p.append("\u001B\u0061\u0001") // Align Center
                p.append("\u001B\u0045\u0001${prefs.getString("name", "Toko")}\n\u001B\u0045\u0000") // Bold Nama Toko
                p.append("${prefs.getString("address", "Alamat Toko")}\n")
                if(prefs.getString("phone","")!!.isNotEmpty()) p.append("Telp: ${prefs.getString("phone","")}\n")

                // 2. JUDUL REPRINT
                p.append("--------------------------------\n")
                p.append("\u001B\u0045\u0001[ COPY / REPRINT ]\u001B\u0045\u0000\n") // Bold
                p.append("--------------------------------\n")

                // 3. INFO TRANSAKSI
                p.append("\u001B\u0061\u0000") // Align Left
                p.append("ID: #${trx.id}\n")
                p.append("Tgl: ${SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(trx.timestamp))}\n")
                p.append("Kasir: ${session.getString("username","Admin")}\n")

                // 🔥 CETAK MEJA & PELANGGAN DI SINI 🔥
                if (infoPelanggan.isNotEmpty()) {
                    val formattedInfo = infoPelanggan.replace(" | ", "\n").trim()
                    p.append("\u001B\u0045\u0001") // Bold Nyala
                    p.append("$formattedInfo\n")
                    p.append("\u001B\u0045\u0000") // Bold Mati
                }

                p.append("--------------------------------\n")

                // 4. DAFTAR BARANG (Looping yang benar)
                for (itemStr in rawItems) {
                    val parts = itemStr.split("|")
                    // Pastikan format valid: Nama|Qty|Harga|Total
                    if (parts.size == 4) {
                        p.append("${parts[0]}\n") // Nama Barang
                        val pr = parts[2].toDoubleOrNull() ?: 0.0
                        val tot = parts[3].toDoubleOrNull() ?: 0.0
                        // Cetak baris: Qty x Harga .... Total
                        p.append(row("  ${parts[1]} x ${formatRupiah(pr).replace("Rp ","")}", tot))
                    }
                }

                // 5. TOTAL & PEMBAYARAN
                p.append("--------------------------------\n")
                p.append(row("Subtotal", trx.subtotal))

                if(trx.discount > 0) p.append(row("Diskon", trx.discount, true))
                if(trx.tax > 0) p.append(row("Pajak", trx.tax))

                p.append("--------------------------------\n")
                p.append("\u001B\u0045\u0001${row("TOTAL", trx.totalAmount)}\u001B\u0045\u0000") // Bold Total

                if(trx.paymentMethod.contains("Tunai")) {
                    p.append(row("Tunai", trx.cashReceived))
                    p.append(row("Kembali", trx.changeAmount))
                } else {
                    p.append("Metode: ${trx.paymentMethod}\n")
                }

                // 6. FOOTER
                p.append("\u001B\u0061\u0001") // Align Center
                p.append("--------------------------------\n")
                p.append("${prefs.getString("email","Terima Kasih!")}\n")
                p.append("Powered by Sysdos POS\n\n\n") // Feed lines

                // KIRIM KE PRINTER
                os.write(p.toString().toByteArray())
                os.flush()
                Thread.sleep(1500)
                socket.close()

                runOnUiThread { Toast.makeText(this, "Reprint Berhasil! ✅", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, "Gagal Print: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun formatRupiah(number: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')
    }
}