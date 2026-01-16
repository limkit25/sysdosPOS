package com.sysdos.kasirpintar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class DashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private var allTrx: List<Transaction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvRole = findViewById<TextView>(R.id.tvRole)
        val tvRevenue = findViewById<TextView>(R.id.tvTodayRevenue)
        val btnLogout = findViewById<ImageView>(R.id.btnLogout)
        val mainGrid = findViewById<GridLayout>(R.id.mainGrid)

        // AMBIL KARTU MENU
        val cardPOS = findViewById<CardView>(R.id.cardPOS)
        val cardProduct = findViewById<CardView>(R.id.cardProduct)
        val cardPurchase = findViewById<CardView>(R.id.cardPurchase)
        val cardReport = findViewById<CardView>(R.id.cardReport)
        val cardUser = findViewById<CardView>(R.id.cardUser)
        val cardStore = findViewById<CardView>(R.id.cardStore)
        val cardPrinter = findViewById<CardView>(R.id.cardPrinter)
        val cardShift = findViewById<CardView>(R.id.cardShift)
        // 🔥 1. DEFINISI CARD ABOUT BARU
        val cardAbout = findViewById<CardView>(R.id.cardAbout)

        val tvLowStock = findViewById<TextView>(R.id.tvLowStockCount)
        val cardLowStockInfo = findViewById<CardView>(R.id.cardLowStockInfo)

        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "Admin")
        val role = session.getString("role", "kasir")

        tvGreeting.text = "Halo, $username"
        tvRole.text = "Role: ${role?.uppercase()}"

        if (role == "kasir") {
            cardLowStockInfo.visibility = View.GONE
        } else {
            cardLowStockInfo.visibility = View.VISIBLE
        }

        // --- SORTING MENU BERDASARKAN ROLE ---
        mainGrid.removeAllViews()
        val authorizedCards = mutableListOf<View>()

        // SEMUA BISA AKSES KASIR
        authorizedCards.add(cardPOS)
        // 🔥 2. SEMUA BISA AKSES ABOUT (Tambahkan disini)


        if (role == "admin") {
            authorizedCards.add(cardProduct)
            authorizedCards.add(cardPurchase)
            authorizedCards.add(cardReport)
            authorizedCards.add(cardUser)
            authorizedCards.add(cardShift)
            authorizedCards.add(cardStore)
            authorizedCards.add(cardPrinter)
        } else if (role == "manager") {
            authorizedCards.add(cardProduct)
            authorizedCards.add(cardPurchase)
            authorizedCards.add(cardReport)
            authorizedCards.add(cardShift)
            authorizedCards.add(cardPrinter)
        } else {
            authorizedCards.add(cardReport)
            authorizedCards.add(cardPrinter)
        }
        authorizedCards.add(cardAbout)
        for (card in authorizedCards) {
            mainGrid.addView(card)
            card.visibility = View.VISIBLE
        }

        // --- OBSERVE DATA ---
        viewModel.allTransactions.observe(this) { transactions ->
            allTrx = transactions
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date())
            var totalHariIni = 0.0
            for (trx in transactions) {
                val trxDateStr = sdf.format(Date(trx.timestamp))
                if (trxDateStr == todayStr) totalHariIni += trx.totalAmount
            }
            tvRevenue.text = formatRupiah(totalHariIni)
        }

        val imgAlert = findViewById<ImageView>(R.id.imgAlertStock)
        viewModel.allProducts.observe(this) { products ->
            val lowStockCount = products.count { it.stock < 5 }
            if (lowStockCount > 0) {
                tvLowStock.text = "$lowStockCount Item"
                tvLowStock.setTextColor(android.graphics.Color.RED)
                imgAlert.visibility = View.VISIBLE
                imgAlert.setColorFilter(android.graphics.Color.RED)
            } else {
                tvLowStock.text = "Aman"
                tvLowStock.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                imgAlert.visibility = View.VISIBLE
                imgAlert.setImageResource(android.R.drawable.checkbox_on_background)
                imgAlert.setColorFilter(android.graphics.Color.parseColor("#2E7D32"))
            }
        }

        // --- CLICK LISTENERS ---
        cardPOS.setOnClickListener {
            val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
            val isExpired = licensePrefs.getBoolean("is_expired", false)

            if (isExpired) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ MASA TRIAL HABIS")
                    .setMessage("Masa percobaan 7 hari telah berakhir.\n\nSilakan hubungi Admin Sysdos untuk upgrade ke Full Version.")
                    .setPositiveButton("Hubungi Admin") { _, _ ->
                        try {
                            val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/628179842043"))
                            startActivity(i)
                        } catch (e: Exception) {
                            Toast.makeText(this, "WhatsApp tidak terinstall", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Tutup", null)
                    .show()
            } else {
                checkModalBeforePOS()
            }
        }

        cardProduct.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java)) }
        cardPurchase.setOnClickListener { startActivity(Intent(this, PurchaseActivity::class.java)) }
        cardReport.setOnClickListener { startActivity(Intent(this, SalesReportActivity::class.java)) }
        cardUser.setOnClickListener { startActivity(Intent(this, UserListActivity::class.java)) }
        cardStore.setOnClickListener { startActivity(Intent(this, StoreSettingsActivity::class.java).apply { putExtra("TARGET", "STORE") }) }
        cardPrinter.setOnClickListener { startActivity(Intent(this, StoreSettingsActivity::class.java).apply { putExtra("TARGET", "PRINTER") }) }
        cardShift?.setOnClickListener { startActivity(Intent(this, ShiftHistoryActivity::class.java)) }
        cardLowStockInfo.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java).apply { putExtra("OPEN_TAB_INDEX", 2) }) }

        // 🔥 3. KLIK CARD ABOUT -> PINDAH HALAMAN
        cardAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // 🔥 FITUR LOGOUT (UPDATE: ADMIN/MANAGER BISA TUTUP SHIFT) 🔥
        btnLogout.setOnClickListener {
            // 1. Cek dulu status Lisensi
            val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
            val isFullVersion = licensePrefs.getBoolean("is_full_version", false)

            if (role == "kasir") {
                // === KASIR (TETAP SEPERTI BIASA) ===
                val options = arrayOf("💰 Tutup Kasir & Cetak Laporan", "🚪 Log Out Biasa")
                AlertDialog.Builder(this)
                    .setTitle("Menu Keluar (Kasir)")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> showCloseSessionDialog(session)
                            1 -> performLogout(session, false)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            } else {
                // === ADMIN / MANAGER (SEKARANG BISA TUTUP SHIFT JUGA) ===

                if (isFullVersion) {
                    // ✅ JIKA SUDAH PREMIUM:
                    // Tambahkan opsi Tutup Shift di sini
                    val options = arrayOf("💰 Tutup Shift & Cetak", "🚪 Log Out Biasa")

                    AlertDialog.Builder(this)
                        .setTitle("Menu Keluar ($role)") // Judul menyesuaikan role
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> showCloseSessionDialog(session) // Admin bisa tutup shift
                                1 -> performLogout(session, false)
                            }
                        }
                        .setNegativeButton("Batal", null)
                        .show()

                } else {
                    // ⏳ JIKA MASIH TRIAL:
                    // Ada 3 Opsi: Tutup Shift, Logout Biasa, Reset Data
                    val options = arrayOf("💰 Tutup Shift & Cetak", "🚪 Log Out Saja", "🗑️ Log Out & HAPUS SEMUA DATA (Reset)")

                    AlertDialog.Builder(this)
                        .setTitle("Menu Keluar (Mode Trial)")
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> showCloseSessionDialog(session) // Admin bisa tutup shift
                                1 -> performLogout(session, false)
                                2 -> showResetConfirmation(session) // Hati-hati, hapus data
                            }
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
        }

        // TRIAL CHECK
        val tvTrial = findViewById<TextView>(R.id.tvTrialStatus)
        val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
        val isFull = prefs.getBoolean("is_full_version", false)
        val serverMsg = prefs.getString("license_msg", "")

        if (isFull) {
            tvTrial.visibility = View.GONE
        } else if (!serverMsg.isNullOrEmpty()) {
            tvTrial.text = serverMsg
            tvTrial.visibility = View.VISIBLE
            if (prefs.getBoolean("is_expired", false)) {
                tvTrial.setTextColor(android.graphics.Color.RED)
            }
        } else {
            val firstRunDate = prefs.getLong("first_install_date", 0L)
            if (firstRunDate != 0L) {
                val now = System.currentTimeMillis()
                val diffMillis = now - firstRunDate
                val daysUsed = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
                val maxTrial = 7
                val daysLeft = maxTrial - daysUsed
                if (daysLeft > 0) {
                    tvTrial.text = "Sisa Trial: $daysLeft Hari"
                    tvTrial.visibility = View.VISIBLE
                } else {
                    tvTrial.text = "TRIAL HABIS!"
                    tvTrial.setTextColor(android.graphics.Color.RED)
                    tvTrial.visibility = View.VISIBLE
                }
            } else {
                tvTrial.visibility = View.GONE
            }
        }
    }

    // --- FUNGSI BUKA SHIFT ---
    private fun checkModalBeforePOS() {
        val prefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "default") ?: "default"

        if (!prefs.getBoolean("IS_OPEN_$username", false)) {
            showInputModalDialogForPOS(prefs, username)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun showInputModalDialogForPOS(prefs: android.content.SharedPreferences, username: String) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Contoh: 200000"

        AlertDialog.Builder(this)
            .setTitle("🏪 Buka Shift Baru")
            .setMessage("Halo $username! Masukkan Modal Awal:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("BUKA") { _, _ ->
                val modalStr = input.text.toString()
                if (modalStr.isNotEmpty()) {
                    val modal = modalStr.toDouble()
                    prefs.edit().apply {
                        putBoolean("IS_OPEN_$username", true)
                        putFloat("MODAL_AWAL_$username", modal.toFloat())
                        putFloat("CASH_SALES_TODAY_$username", 0f)
                        putLong("START_TIME_$username", System.currentTimeMillis())
                        apply()
                    }
                    viewModel.openShift(username, modal)
                    Toast.makeText(this, "Shift Dibuka!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // 🔥 UPDATE: FITUR TUTUP SHIFT DENGAN RINCIAN PEMBAYARAN 🔥
    // 🔥 UPDATE: AMBIL WAKTU MULAI SHIFT YANG BENAR 🔥
    private fun showCloseSessionDialog(session: android.content.SharedPreferences) {
        val userName = session.getString("username", "User") ?: "User"

        // 1. AMBIL WAKTU MULAI SHIFT
        val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        // Ambil waktu start. Jika 0, set ke awal hari ini (jam 00:00)
        val shiftStartTime = shiftPrefs.getLong("START_TIME_$userName", 0L)

        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis

        // Logika: Kalau user lupa buka shift (0), kita ambil transaksi dari pagi ini saja.
        val filterTime = if (shiftStartTime > 0) shiftStartTime else startOfDay

        val modalAwal = shiftPrefs.getFloat("MODAL_AWAL_$userName", 0f).toDouble()

        // 2. 🔥 PERBAIKAN UTAMA: GUNAKAN VARIABEL GLOBAL allTrx 🔥
        // (Variabel ini otomatis terisi oleh Observer di onCreate, jadi pasti ada datanya)
        val sourceData = this.allTrx

        // 3. 🔥 PERBAIKAN FILTER: HAPUS FILTER USER ID 🔥
        // Biarkan semua transaksi di HP ini masuk laporan, biar tidak ada yang hilang.
        val myTrx = sourceData.filter { it.timestamp >= filterTime }

        // --- DEBUGGING (OPSIONAL: NANTI MUNCUL TOAST BIAR KETAHUAN) ---
        // Toast.makeText(this, "Total Trx: ${sourceData.size}, Setelah Filter Waktu: ${myTrx.size}", Toast.LENGTH_LONG).show()

        // 4. Hitung Rincian
        var totalTunai = 0.0
        var totalQris = 0.0
        var totalDebit = 0.0
        var totalTransfer = 0.0

        for (trx in myTrx) {
            // Gunakan lowercase biar aman dari typo (Tunai/tunai/TUNAI)
            when (trx.paymentMethod.lowercase()) {
                "tunai", "cash" -> totalTunai += trx.totalAmount
                "qris" -> totalQris += trx.totalAmount
                "debit" -> totalDebit += trx.totalAmount
                "transfer" -> totalTransfer += trx.totalAmount
                else -> totalTunai += trx.totalAmount // Default ke Tunai
            }
        }

        val totalOmzet = totalTunai + totalQris + totalDebit + totalTransfer

        fun fmt(d: Double): String {
            return java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID")).format(d)
        }
        val dateStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(filterTime))

        val pesanLaporan = """
            Halo, $userName!
            Shift Mulai: $dateStr
            (Ditemukan ${myTrx.size} Transaksi)

            💵 TUNAI      : ${fmt(totalTunai)}
            📱 QRIS       : ${fmt(totalQris)}
            💳 DEBIT      : ${fmt(totalDebit)}
            🏦 TRANSFER   : ${fmt(totalTransfer)}
            -------------------------------------
            💎 TOTAL OMZET : ${fmt(totalOmzet)}
            
            Laci Kasir Harusnya: ${fmt(modalAwal + totalTunai)}
            (Modal ${fmt(modalAwal)} + Tunai ${fmt(totalTunai)})
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("💰 Ringkasan Tutup Shift")
            .setMessage(pesanLaporan)
            .setPositiveButton("LANJUT HITUNG") { _, _ ->
                showInputCashDialog(
                    expectedCash = modalAwal + totalTunai,
                    totalOmzet = totalOmzet,
                    userName = userName,
                    startTime = filterTime,
                    modal = modalAwal,
                    rincianTunai = totalTunai,
                    rincianQris = totalQris,
                    rincianTrf = totalTransfer,
                    rincianDebit = totalDebit
                )
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // 🔥 UPDATE: PANGGIL PRINT SHIFT REPORT DISINI 🔥
    private fun showInputCashDialog(
        expectedCash: Double,
        totalOmzet: Double,
        userName: String,
        startTime: Long,
        modal: Double,
        rincianTunai: Double,
        rincianQris: Double,
        rincianTrf: Double,
        rincianDebit: Double
    ) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Total uang fisik di laci"

        // Format angka biar enak dilihat
        val fmtExpected = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID")).format(expectedCash)

        AlertDialog.Builder(this)
            .setTitle("Verifikasi Fisik Kas")
            .setMessage("Sistem mencatat harusnya ada: $fmtExpected\n(Modal + Penjualan Tunai)\n\nMasukkan jumlah uang fisik riil:")
            .setView(input)
            .setPositiveButton("TUTUP & PRINT") { _, _ ->
                val actualCashStr = input.text.toString()
                val actualCash = if (actualCashStr.isNotEmpty()) actualCashStr.toDouble() else 0.0

                // 1. Simpan Log ke Database
                viewModel.closeShift(userName, expectedCash, actualCash)

                // 2. 🔥 CETAK STRUK LAPORAN SHIFT 🔥
                printShiftReport(
                    kasirName = userName,
                    startTime = startTime,
                    modal = modal,
                    tunai = rincianTunai,
                    qris = rincianQris,
                    trf = rincianTrf,
                    debit = rincianDebit,
                    expected = expectedCash,
                    actual = actualCash
                )

                Toast.makeText(this, "Shift Ditutup & Laporan Dicetak!", Toast.LENGTH_SHORT).show()

                // 3. Logout
                performLogout(getSharedPreferences("session_kasir", Context.MODE_PRIVATE), false)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showResetConfirmation(session: android.content.SharedPreferences) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ PERINGATAN KERAS")
            .setMessage("Anda yakin ingin MENGHAPUS SEMUA DATA?\n\nSemua Produk, Transaksi, dan User akan hilang permanen. Aplikasi akan kembali seperti baru diinstall.\n\nCocok untuk pergantian pengguna Trial.")
            .setPositiveButton("YA, HAPUS SEMUANYA") { _, _ ->
                performLogout(session, true)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performLogout(session: android.content.SharedPreferences, resetData: Boolean) {
        session.edit().clear().apply()
        val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        shiftPrefs.edit().clear().apply()

        if (resetData) {
            Toast.makeText(this, "Sedang menghapus data...", Toast.LENGTH_SHORT).show()
            viewModel.logoutAndReset { signOutGoogleAndExit() }
        } else {
            signOutGoogleAndExit()
        }
    }

    private fun signOutGoogleAndExit() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun printShiftReport(
        kasirName: String, startTime: Long, modal: Double,
        tunai: Double, qris: Double, trf: Double, debit: Double,
        expected: Double, actual: Double
    ) {
        val storePrefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val targetMac = storePrefs.getString("printer_mac", "")
        if (targetMac.isNullOrEmpty()) return

        Thread {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@Thread
                val device = bluetoothAdapter.getRemoteDevice(targetMac)
                val socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket.connect()
                val os = socket.outputStream
                val storeName = storePrefs.getString("name", "Toko Saya")
                val now = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date())
                val startStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startTime))
                val p = StringBuilder()
                p.append("\u001B\u0061\u0001")
                p.append("\u001B\u0045\u0001$storeName\u001B\u0045\u0000\n")
                p.append("--------------------------------\n")
                p.append("LAPORAN TUTUP SHIFT\n")
                p.append("--------------------------------\n")
                p.append("\u001B\u0061\u0000")
                p.append("Kasir  : $kasirName\n")
                p.append("Waktu  : $now\n")
                p.append("Shift  : $startStr s/d Sekarang\n")
                p.append("--------------------------------\n")
                fun row(label: String, value: Double) {
                    val vStr = formatRupiah(value).replace("Rp ", "")
                    val space = 32 - label.length - vStr.length
                    p.append("$label${" ".repeat(if (space > 0) space else 1)}$vStr\n")
                }
                p.append("PENJUALAN:\n")
                row("Tunai", tunai)
                row("QRIS", qris)
                row("Transfer", trf)
                row("Debit", debit)
                p.append("--------------------------------\n")
                val totalOmzet = tunai + qris + trf + debit
                p.append("\u001B\u0045\u0001")
                row("TOTAL OMZET", totalOmzet)
                p.append("\u001B\u0045\u0000")
                p.append("--------------------------------\n\n")
                p.append("REKONSILIASI KAS (FISIK):\n")
                row("Modal Awal", modal)
                row("Tunai Masuk", tunai)
                p.append("---------------- --\n")
                row("Total Hrpn", expected)
                row("Aktual Laci", actual)
                val selisih = actual - expected
                p.append("--------------------------------\n")
                val labelSelisih = if(selisih < 0) "KURANG" else if(selisih > 0) "LEBIH" else "KLOP"
                row("SELISIH ($labelSelisih)", selisih)
                p.append("--------------------------------\n")
                p.append("\u001B\u0061\u0001")
                p.append("\n\n\n")
                os.write(p.toString().toByteArray())
                os.flush()
                Thread.sleep(2000)
                socket.close()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun formatRupiah(amount: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", amount.toLong()).replace(',', '.')
    }
}