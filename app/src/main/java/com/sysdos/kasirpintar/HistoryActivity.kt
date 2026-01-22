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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.core.view.GravityCompat

class HistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: TransactionAdapter
    private var fullTransactionList: List<Transaction> = emptyList()
    private var listSemuaPegawai: List<com.sysdos.kasirpintar.data.model.User> = emptyList()

    // Tombol UI
    private lateinit var btnToday: Button
    private lateinit var btnWeek: Button
    private lateinit var btnMonth: Button
    private lateinit var btnAll: Button
    private lateinit var btnExport: ImageButton

    private lateinit var cardPiutang: androidx.cardview.widget.CardView
    private lateinit var tvTotalPiutang: TextView

    // 🔥 1. VARIABEL MENU SAMPING
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navView: com.google.android.material.navigation.NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // =============================================================
        // 🔥 2. SETUP MENU SAMPING (DRAWER)
        // =============================================================
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        val btnMenu = findViewById<View>(R.id.btnMenuDrawer) // Sesuai ID di XML baru

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Setup Header Menu
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")

        if (navView.headerCount > 0) {
            val header = navView.getHeaderView(0)
            header.findViewById<TextView>(R.id.tvHeaderName).text = realName
            header.findViewById<TextView>(R.id.tvHeaderRole).text = "Role: ${role?.uppercase()}"
        }

        // Logika Navigasi Pindah Halaman
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, DashboardActivity::class.java)); finish() }
                R.id.nav_kasir -> { startActivity(Intent(this, MainActivity::class.java)); finish() }
                R.id.nav_stok -> { startActivity(Intent(this, ProductListActivity::class.java)); finish() }
                R.id.nav_laporan -> { startActivity(Intent(this, SalesReportActivity::class.java)); finish() }
                R.id.nav_user -> { startActivity(Intent(this, UserListActivity::class.java)); finish() }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // =============================================================
        // 🔥 3. INISIALISASI VIEWS (KODINGAN LAMA ANDA)
        // =============================================================

        // Setup RecyclerView
        val rvHistory = findViewById<RecyclerView>(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter()
        rvHistory.adapter = adapter

        // Setup Filter UI
        btnToday = findViewById(R.id.btnFilterToday)
        btnWeek = findViewById(R.id.btnFilterWeek)
        btnMonth = findViewById(R.id.btnFilterMonth)
        btnAll = findViewById(R.id.btnFilterAll)
        btnExport = findViewById(R.id.btnExport)

        cardPiutang = findViewById(R.id.cardSummaryPiutang)
        tvTotalPiutang = findViewById(R.id.tvTotalPiutangDashboard)

        // ❌ HAPUS BARIS INI: findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        // Karena tombol btnBack sudah tidak ada di XML (diganti btnMenuDrawer)

        // Logic Export
        if (role == "kasir") {
            btnExport.visibility = View.GONE
        } else {
            btnExport.visibility = View.VISIBLE
            btnExport.setOnClickListener { showExportDateDialog() }
        }

        // Observe Data
        viewModel.allTransactions.observe(this) { transactions ->
            fullTransactionList = transactions
            calculateTotalPiutang()
            filterList("TODAY")
            updateFilterUI(btnToday)
        }

        viewModel.allUsers.observe(this) { users ->
            listSemuaPegawai = users
        }

        // Search View Logic
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

        // Filter Click Listeners
        btnToday.setOnClickListener { updateFilterUI(btnToday); filterList("TODAY") }
        btnWeek.setOnClickListener { updateFilterUI(btnWeek); filterList("WEEK") }
        btnMonth.setOnClickListener { updateFilterUI(btnMonth); filterList("MONTH") }
        btnAll.setOnClickListener { updateFilterUI(btnAll); filterList("ALL") }

        // Fitur Klik Kartu Piutang
        cardPiutang.setOnClickListener {
            updateFilterUI(null)
            val debtList = fullTransactionList.filter { trx ->
                val isPiutang = trx.paymentMethod.lowercase().contains("piutang")
                val isLunas = trx.note.contains("LUNAS", ignoreCase = true)
                isPiutang && !isLunas
            }.sortedBy { it.timestamp }

            if (debtList.isEmpty()) {
                Toast.makeText(this, "Tidak ada piutang yang belum lunas! 🎉", Toast.LENGTH_SHORT).show()
            }
            adapter.submitList(debtList)
        }

        // Klik Detail & Void
        adapter.setOnItemClickListener { showDetailDialog(it) }
        adapter.setOnReprintClickListener { doReprint(it) }
        adapter.setOnItemLongClickListener { trx ->
            AlertDialog.Builder(this)
                .setTitle("⚠️ Batalkan Transaksi?")
                .setMessage("ID: #${trx.id}\n\nStok barang akan dikembalikan dan transaksi dihapus.")
                .setPositiveButton("YA, VOID") { _, _ ->
                    // 1. Proses Lokal (Room & Stok)
                    viewModel.voidTransaction(trx) {
                        Toast.makeText(this, "Transaksi Dibatalkan di HP!", Toast.LENGTH_SHORT).show()
                        calculateTotalPiutang()

                        // 🔥 2. PROSES SERVER (Kirim data VOID ke Golang)
                        syncUpdateToServer(trx.id, "VOID", "Dibatalkan oleh Kasir")
                    }
                }
                .setNegativeButton("Batal", null).show()
        }
    }

    // 🔥 4. TAMBAH LOGIKA BACK BUTTON HP
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    // 🔥 FUNGSI HITUNG PIUTANG 🔥
    private fun calculateTotalPiutang() {
        var totalDebt = 0.0
        for (trx in fullTransactionList) {
            val isPiutang = trx.paymentMethod.lowercase().contains("piutang")
            val isLunas = trx.note.contains("LUNAS", ignoreCase = true)

            // Kalau Piutang DAN Belum Lunas -> Tambahkan ke Tagihan
            if (isPiutang && !isLunas) {
                totalDebt += trx.totalAmount
            }
        }
        tvTotalPiutang.text = formatRupiah(totalDebt)
    }

    // Update Filter UI (Modifikasi sedikit untuk handle null)
    // HAPUS SEMUA VERSI updateFilterUI YANG ADA, GANTI DENGAN INI:
    private fun updateFilterUI(activeBtn: Button?) {
        val allBtns = listOf(btnToday, btnWeek, btnMonth, btnAll)
        for (btn in allBtns) {
            btn.setBackgroundColor(android.graphics.Color.WHITE)
            btn.setTextColor(android.graphics.Color.GRAY)
        }

        // Kalau activeBtn null (saat klik kartu piutang), semua tombol jadi putih (netral)
        // Kalau activeBtn ada isinya, warnai tombol tersebut jadi biru
        activeBtn?.let {
            it.setBackgroundColor(android.graphics.Color.parseColor("#1976D2"))
            it.setTextColor(android.graphics.Color.WHITE)
        }
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


    // ... (kode onCreate dan listener lain biarkan sama) ...

    // --- FITUR EXPORT (DENGAN PILIHAN TANGGAL) ---
    private fun showExportDateDialog() {
        // Load layout popup tanggal
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_date, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Export Laporan Transaksi")
            .setView(dialogView)
            .create()

        val etStartDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etStartDate)
        val etEndDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEndDate)
        val btnProcess = dialogView.findViewById<Button>(R.id.btnProcessExport)

        // Setup Kalender & Format Tanggal
        val calendar = Calendar.getInstance()
        val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

        // Default: Hari Ini
        etStartDate.setText(displayFormat.format(Date()))
        etEndDate.setText(displayFormat.format(Date()))

        // Variabel penampung Millis (Jam 00:00 s/d 23:59)
        var startMillis: Long
        var endMillis: Long

        // Init default millis hari ini
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val todayEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }
        startMillis = todayStart.timeInMillis
        endMillis = todayEnd.timeInMillis

        // Listener Klik Tanggal Awal
        etStartDate.setOnClickListener {
            android.app.DatePickerDialog(this, { _, year, month, day ->
                val cal = Calendar.getInstance()
                cal.set(year, month, day, 0, 0, 0)
                startMillis = cal.timeInMillis
                etStartDate.setText(displayFormat.format(cal.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Listener Klik Tanggal Akhir
        etEndDate.setOnClickListener {
            android.app.DatePickerDialog(this, { _, year, month, day ->
                val cal = Calendar.getInstance()
                cal.set(year, month, day, 23, 59, 59)
                endMillis = cal.timeInMillis
                etEndDate.setText(displayFormat.format(cal.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Tombol Proses Export
        btnProcess.setOnClickListener {
            if (startMillis > endMillis) {
                Toast.makeText(this, "❌ Tanggal Awal tidak boleh lebih besar dari Akhir", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            processExport(startMillis, endMillis)
        }

        dialog.show()
    }

    private fun processExport(start: Long, end: Long) {
        Toast.makeText(this, "Sedang menyiapkan data...", Toast.LENGTH_SHORT).show()

        // Ambil User ID dari Session
        val prefs = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        // Ambil User ID yang login, kalau admin pakai ID admin
        val currentUserId = 1 // Default Admin (Sesuaikan jika Mas Heru simpan ID di session)

        // Panggil Database di Background (Coroutine)
        lifecycleScope.launch(Dispatchers.IO) {

            val db = com.sysdos.kasirpintar.data.AppDatabase.getDatabase(this@HistoryActivity)

            // Ini akan aman karena berjalan di dalam coroutine (launch)
            val filteredData = db.transactionDao().getTransactionsByDateRange(currentUserId, start, end)

            withContext(Dispatchers.Main) {
                if (filteredData.isEmpty()) {
                    Toast.makeText(this@HistoryActivity, "Tidak ada data di tanggal tersebut", Toast.LENGTH_SHORT).show()
                } else {
                    generateCSV(filteredData)
                }
            }
        }
    }

    private fun generateCSV(dataList: List<Transaction>) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val file = File(cacheDir, "Laporan_Transaksi_$timeStamp.csv")
            val writer = FileWriter(file)

            // 🔥 1. UPDATE HEADER: Tambahkan "Diskon" dan "Pajak"
            writer.append("ID Transaksi,Tanggal,Jam,Detail Menu (Item & Topping),Total Belanja,Diskon,Pajak,Metode Bayar,Keuntungan,Catatan\n")

            for (t in dataList) {
                val d = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(t.timestamp))
                val tm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.timestamp))

                // --- Logika Membedah Menu (Sama seperti sebelumnya) ---
                val summaryParts = t.itemsSummary.split(" || ")
                val rawItemsStr = summaryParts[0]
                val rawItems = rawItemsStr.split(";")
                val detailBuilder = StringBuilder()

                for (itemStr in rawItems) {
                    val parts = itemStr.split("|")
                    if (parts.size >= 2) {
                        val namaMenu = parts[0]
                        val qty = parts[1]
                        if (detailBuilder.isNotEmpty()) detailBuilder.append(" + ")
                        detailBuilder.append("$namaMenu (x$qty)")
                    }
                }

                // Bersihkan karakter koma agar CSV rapi
                val cleanDetail = detailBuilder.toString().replace(",", " &")
                val cleanNote = t.note.replace(",", " ").replace("\n", " ")

                // -----------------------------------------------------

                // 🔥 2. UPDATE ISI BARIS: Masukkan t.discount dan t.tax
                // Urutannya: ID, Tgl, Jam, Detail, Total, DISKON, PAJAK, Metode, Profit, Note
                writer.append("#${t.id},$d,$tm,\"$cleanDetail\",${t.totalAmount},${t.discount},${t.tax},${t.paymentMethod},${t.profit},\"$cleanNote\"\n")
            }

            writer.flush()
            writer.close()

            // Share File
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Kirim Laporan via..."))

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    // --- DIALOG DETAIL ---
    private fun showDetailDialog(trx: Transaction) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_transaction_detail, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialogView.findViewById<View>(R.id.btnCloseDetail)
        val btnReprint = dialogView.findViewById<Button>(R.id.btnReprint)
        val llItems = dialogView.findViewById<LinearLayout>(R.id.llDetailItems)

        // TAMPILKAN DATA (SAMA SEPERTI SEBELUMNYA)
        dialogView.findViewById<TextView>(R.id.tvDetailId).text = "#TRX-${trx.id}"
        dialogView.findViewById<TextView>(R.id.tvDetailDate).text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(trx.timestamp))
        dialogView.findViewById<TextView>(R.id.tvDetailTotal).text = formatRupiah(trx.totalAmount)

        // Cek apakah ada status LUNAS di note
        val isLunas = trx.note.contains("LUNAS", ignoreCase = true)
        val statusBayar = if (trx.paymentMethod.lowercase().contains("piutang")) {
            if (isLunas) "PIUTANG (LUNAS ✅)" else "PIUTANG (BELUM LUNAS ⏳)"
        } else {
            if (trx.paymentMethod == "Tunai") "Tunai (Bayar: ${formatRupiah(trx.cashReceived)})" else "Metode: ${trx.paymentMethod}"
        }
        dialogView.findViewById<TextView>(R.id.tvDetailMethod).text = statusBayar

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

        // Render Items (SAMA)
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

        // ====================================================================
        // 🔥 LOGIKA BARU: TOMBOL PELUNASAN PIUTANG 🔥
        // ====================================================================
        val isPiutang = trx.paymentMethod.lowercase().contains("piutang")

        if (isPiutang && !isLunas) {
            // Ubah tombol jadi opsi pelunasan
            btnReprint.text = "💰 LUNASI / REPRINT"
            btnReprint.setBackgroundColor(Color.parseColor("#FF9800")) // Warna Oranye biar beda (Opsional)

            btnReprint.setOnClickListener {
                // Munculkan Pilihan
                val options = arrayOf("✅ Tandai LUNAS (Terima Uang)", "🖨️ Reprint Struk Saja")
                AlertDialog.Builder(this)
                    .setTitle("Kelola Piutang")
                    .setItems(options) { _, which ->
                        if (which == 0) {
                            markAsPaid(trx, dialog) // Panggil fungsi pelunasan
                        } else {
                            doReprint(trx) // Print biasa
                        }
                    }
                    .show()
            }
        } else {
            // Transaksi Biasa (Tunai/Qris/Sudah Lunas)
            btnReprint.text = "🖨️ Reprint Struk"
            btnReprint.setBackgroundColor(Color.parseColor("#6200EE")) // Balikin warna ungu default (sesuaikan tema)
            btnReprint.setOnClickListener { doReprint(trx) }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // 🔥 PERBAIKAN: Ganti Thread dengan lifecycleScope
    private fun markAsPaid(trx: Transaction, dialog: AlertDialog) {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Pelunasan")
            .setMessage("Terima pembayaran sebesar ${formatRupiah(trx.totalAmount)} sekarang?\n\nStatus transaksi akan berubah menjadi LUNAS.")
            .setPositiveButton("YA, TERIMA") { _, _ ->

                // GUNAKAN COROUTINE, BUKAN THREAD BIASA
                lifecycleScope.launch(Dispatchers.IO) {

                    // 1. Update Note dengan timestamp pelunasan
                    val tglLunas = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                    val newNote = if (trx.note.isEmpty()) "LUNAS: $tglLunas" else "${trx.note} | LUNAS: $tglLunas"

                    val updatedTrx = trx.copy(note = newNote)

                    // 2. Simpan ke Database
                    val db = com.sysdos.kasirpintar.data.AppDatabase.getDatabase(this@HistoryActivity)
                    db.transactionDao().update(updatedTrx)

                    // 3. Update UI di Main Thread
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@HistoryActivity, "✅ Hutang Lunas!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        // Refresh Data (Opsional, karena LiveData akan auto-update)
                        syncUpdateToServer(trx.id, "SUCCESS", updatedTrx.note)
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
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

                // 🔥 3. GANTI LOGIKA NAMA KASIR DISINI 🔥
                // Cari user yang ID-nya sama dengan ID pembuat transaksi (trx.userId)
                val pegawaiAsli = listSemuaPegawai.find { it.id == trx.userId }

                // Ambil namanya. Kalau user sudah dihapus, tampilkan ID-nya saja.
                val namaKasirStruk = pegawaiAsli?.name ?: "Kasir #${trx.userId}"

                p.append("Kasir: $namaKasirStruk\n")

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

                    if (parts.size == 4) {
                        val fullName = parts[0] // Nama Barang Lengkap

                        // 🔥 UPDATE LOGIKA VARIAN KE BAWAH 🔥
                        if (fullName.contains(" - ")) {
                            val nameParts = fullName.split(" - ", limit = 2)
                            p.append("${nameParts[0]}\n")      // Nama Produk
                            p.append("  (${nameParts[1]})\n")  // Varian di bawah (dikurung & spasi)
                        } else {
                            p.append("$fullName\n")
                        }

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
    // 🔥 Fungsi Tambahan untuk Kirim Update ke Golang
    private fun syncUpdateToServer(androidId: Int, newStatus: String, newNote: String) {
        val req = com.sysdos.kasirpintar.api.StatusUpdateRequest(
            android_id = androidId,
            status = newStatus,
            note = newNote
        )

        // Ambil Client Lokal (Pastikan IP Server sudah di-set saat Login)
        com.sysdos.kasirpintar.api.ApiClient.getLocalClient(this)
            .updateTransactionStatus(req)
            .enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                override fun onResponse(call: retrofit2.Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {
                    if (response.isSuccessful) {
                        android.util.Log.d("SYNC", "Server Updated: $newStatus")
                    } else {
                        android.util.Log.e("SYNC", "Gagal Update Server: ${response.code()}")
                    }
                }
                override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) {
                    android.util.Log.e("SYNC", "Error Koneksi: ${t.message}")
                }
            })
    }
}