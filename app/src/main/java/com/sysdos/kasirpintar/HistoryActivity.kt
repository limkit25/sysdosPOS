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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.TransactionAdapter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class HistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: TransactionAdapter

    // Simpan semua data di sini untuk keperluan filter
    private var fullTransactionList: List<Transaction> = emptyList()

    // Tombol Filter UI
    private lateinit var btnToday: Button
    private lateinit var btnWeek: Button
    private lateinit var btnMonth: Button
    private lateinit var btnAll: Button

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

        // 3. OBSERVE DATA & SIMPAN KE FULL LIST
        viewModel.allTransactions.observe(this) { transactions ->
            fullTransactionList = transactions
            filterList("TODAY") // Default tampilkan hari ini dulu
        }

        // 4. SETUP SEARCH VIEW (Pencarian Manual)
        val svSearch = findViewById<androidx.appcompat.widget.SearchView>(R.id.svSearchHistory)
        svSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    // Filter berdasarkan ID
                    val filtered = fullTransactionList.filter {
                        it.id.toString().contains(newText)
                    }
                    adapter.submitList(filtered)
                } else {
                    // Kalau kosong balik ke filter terakhir (Misal: Hari ini)
                    filterList("TODAY")
                }
                return true
            }
        })

        // 5. SETUP TOMBOL FILTER
        btnToday = findViewById(R.id.btnFilterToday)
        btnWeek = findViewById(R.id.btnFilterWeek)
        btnMonth = findViewById(R.id.btnFilterMonth)
        btnAll = findViewById(R.id.btnFilterAll)

        btnToday.setOnClickListener { updateFilterUI(btnToday); filterList("TODAY") }
        btnWeek.setOnClickListener { updateFilterUI(btnWeek); filterList("WEEK") }
        btnMonth.setOnClickListener { updateFilterUI(btnMonth); filterList("MONTH") }
        btnAll.setOnClickListener { updateFilterUI(btnAll); filterList("ALL") }

        // 6. SETUP KLIK DETAIL & REPRINT
        adapter.setOnItemClickListener { showDetailDialog(it) }
        adapter.setOnReprintClickListener { doReprint(it) }
    }

    // --- LOGIKA FILTER PINTAR ---
    private fun filterList(range: String) {
        val calendar = Calendar.getInstance()
        // Reset Jam ke 00:00:00 biar hitungannya pas mulai pagi
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val todayStart = calendar.timeInMillis

        val filteredList = when (range) {
            "TODAY" -> {
                fullTransactionList.filter { it.timestamp >= todayStart }
            }
            "WEEK" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7) // Mundur 7 hari
                val weekStart = calendar.timeInMillis
                fullTransactionList.filter { it.timestamp >= weekStart }
            }
            "MONTH" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30) // Mundur 30 hari
                val monthStart = calendar.timeInMillis
                fullTransactionList.filter { it.timestamp >= monthStart }
            }
            else -> fullTransactionList // "ALL"
        }

        if (filteredList.isEmpty()) {
            Toast.makeText(this, "Tidak ada data transaksi", Toast.LENGTH_SHORT).show()
        }
        adapter.submitList(filteredList)
    }

    private fun updateFilterUI(activeBtn: Button) {
        // Reset semua tombol jadi abu-abu
        val inactiveColor = Color.parseColor("#E0E0E0")
        val inactiveText = Color.parseColor("#333333")

        listOf(btnToday, btnWeek, btnMonth, btnAll).forEach {
            it.background.setTint(inactiveColor)
            it.setTextColor(inactiveText)
        }

        // Set tombol aktif jadi Biru
        val activeColor = Color.parseColor("#1976D2")
        activeBtn.background.setTint(activeColor)
        activeBtn.setTextColor(Color.WHITE)
    }

    // --- (BAGIAN BAWAH SAMA SEPERTI SEBELUMNYA) ---
    // --- FUNGSI TAMPILKAN DETAIL & REPRINT ---

    private fun showDetailDialog(trx: Transaction) {
        // ... (KODE SAMA PERSIS DENGAN SEBELUMNYA, TIDAK PERLU DIUBAH) ...
        // Agar tidak kepanjangan, silakan paste kode showDetailDialog yang tadi di sini
        // Kalau Bapak mau saya tulis ulang full lagi, kabari ya!

        // --- CONTOH PASTE KODE DIALOG DISINI ---
        val dialogView = layoutInflater.inflate(R.layout.dialog_transaction_detail, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialogView.findViewById<View>(R.id.btnCloseDetail)
        val btnReprint = dialogView.findViewById<Button>(R.id.btnReprint)
        val llItems = dialogView.findViewById<LinearLayout>(R.id.llDetailItems)

        val tvId = dialogView.findViewById<TextView>(R.id.tvDetailId)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvDetailDate)
        val tvSub = dialogView.findViewById<TextView>(R.id.tvDetailSubtotal)
        val tvDisc = dialogView.findViewById<TextView>(R.id.tvDetailDisc)
        val tvTax = dialogView.findViewById<TextView>(R.id.tvDetailTax)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvDetailTotal)
        val tvMethod = dialogView.findViewById<TextView>(R.id.tvDetailMethod)

        tvId.text = "#TRX-${trx.id}"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        tvDate.text = sdf.format(Date(trx.timestamp))

        llItems.removeAllViews()
        val rawItems = trx.itemsSummary.split(";")

        for (itemStr in rawItems) {
            val parts = itemStr.split("|")
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            }
            if (parts.size == 4) {
                val name = parts[0]; val qty = parts[1]; val price = parts[2].toDoubleOrNull() ?: 0.0; val total = parts[3].toDoubleOrNull() ?: 0.0
                val layoutLeft = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                layoutLeft.addView(TextView(this).apply { text = name; setTypeface(null, Typeface.BOLD); setTextColor(Color.BLACK); textSize = 14f })
                layoutLeft.addView(TextView(this).apply { text = "$qty x ${formatRupiah(price)}"; setTextColor(Color.GRAY); textSize = 12f })
                rowLayout.addView(layoutLeft)
                rowLayout.addView(TextView(this).apply { text = formatRupiah(total); setTypeface(null, Typeface.BOLD); setTextColor(Color.BLACK); textSize = 14f; gravity = Gravity.END })
            } else {
                rowLayout.addView(TextView(this).apply { text = itemStr; setTextColor(Color.BLACK); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            }
            llItems.addView(rowLayout)
        }
        tvSub.text = formatRupiah(trx.subtotal); tvTotal.text = formatRupiah(trx.totalAmount); tvMethod.text = "Metode: ${trx.paymentMethod}"

        val rowDisc = dialogView.findViewById<View>(R.id.rowDetailDisc); val rowTax = dialogView.findViewById<View>(R.id.rowDetailTax)
        if (trx.discount > 0) { tvDisc.text = "-${formatRupiah(trx.discount)}"; rowDisc.visibility = View.VISIBLE } else { rowDisc.visibility = View.GONE }
        if (trx.tax > 0) { tvTax.text = "+${formatRupiah(trx.tax)}"; rowTax.visibility = View.VISIBLE } else { rowTax.visibility = View.GONE }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnReprint.setOnClickListener { doReprint(trx) }
        dialog.show()
    }

    private fun doReprint(trx: Transaction) {
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val targetMac = prefs.getString("printer_mac", "")
        if (targetMac.isNullOrEmpty()) { Toast.makeText(this, "Printer belum diatur!", Toast.LENGTH_SHORT).show(); return }

        Thread {
            var socket: BluetoothSocket? = null
            var isConnected = false
            var attempt = 0
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
                }
            } else { if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery() }

            while (attempt < 3 && !isConnected) {
                try {
                    attempt++
                    val device = bluetoothAdapter.getRemoteDevice(targetMac)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@Thread
                    }
                    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket?.connect(); isConnected = true
                } catch (e: Exception) { try { socket?.close() } catch (x: Exception) {}; if (attempt < 3) Thread.sleep(1000) }
            }
            if (!isConnected) { runOnUiThread { Toast.makeText(this, "Gagal Konek Printer", Toast.LENGTH_SHORT).show() }; return@Thread }

            try {
                val outputStream = socket!!.outputStream
                val storeName = prefs.getString("name", "Toko Saya"); val storeAddress = prefs.getString("address", "Indonesia"); val storePhone = prefs.getString("phone", ""); val kasirName = session.getString("username", "Admin")
                val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()); val dateStr = sdf.format(Date(trx.timestamp))
                val p = StringBuilder()
                p.append("\u001B\u0061\u0001\u001B\u0045\u0001$storeName\n\u001B\u0045\u0000$storeAddress\n")
                if (!storePhone.isNullOrEmpty()) p.append("Telp: $storePhone\n"); p.append("--------------------------------\n\u001B\u0045\u0001[ COPY / REPRINT ]\n\u001B\u0045\u0000--------------------------------\n\u001B\u0061\u0000ID   : #${trx.id}\nTgl  : $dateStr\nKasir: $kasirName\n--------------------------------\n")

                fun row(k: String, v: Double, m: Boolean = false): String {
                    val f = String.format(Locale("id","ID"), "Rp %,d", v.toLong()).replace(',', '.'); val r = if(m) "-$f" else f; val max = 32; val maxK = max - r.length - 1; val kTrim = if (k.length > maxK) k.substring(0, maxK) else k; val sp = " ".repeat(if(max - kTrim.length - r.length > 0) max - kTrim.length - r.length else 1)
                    return "$kTrim$sp$r\n"
                }

                val items = trx.itemsSummary.split(";")
                for (i in items) { val pt = i.split("|"); if (pt.size == 4) { p.append("${pt[0]}\n"); p.append(row("  ${pt[1]} x ${String.format(Locale("id","ID"), "%,d", pt[2].toDouble().toLong()).replace(',', '.')}", pt[3].toDouble())) } else { p.append("$i\n") } }
                p.append("--------------------------------\n"); p.append(row("Subtotal", trx.subtotal)); if(trx.discount>0) p.append(row("Diskon", trx.discount, true)); if(trx.tax>0) p.append(row("Pajak", trx.tax))
                p.append("--------------------------------\n\u001B\u0045\u0001${row("TOTAL TAGIHAN", trx.totalAmount)}\u001B\u0045\u0000")
                if(trx.paymentMethod.contains("Tunai")) { p.append(row("Tunai", trx.cashReceived)); p.append(row("Kembali", trx.changeAmount)) } else { p.append("Metode: ${trx.paymentMethod}\n") }
                p.append("\u001B\u0061\u0001--------------------------------\n${prefs.getString("email", "Terima Kasih!")}\n\n\n\n")

                outputStream.write(p.toString().toByteArray()); outputStream.flush(); Thread.sleep(1500); socket.close()
                runOnUiThread { Toast.makeText(this, "Reprint Sukses! ✅", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { try { socket?.close() } catch (e: Exception) {}; e.printStackTrace() }
        }.start()
    }

    private fun formatRupiah(number: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", number.toLong())
    }
}