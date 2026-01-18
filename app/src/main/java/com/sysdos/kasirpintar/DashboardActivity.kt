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
        UpdateManager(this).checkForUpdate()

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
        val realName = session.getString("fullname", username)
        val role = session.getString("role", "kasir")

        tvGreeting.text = "Halo, $realName"
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

        cardPOS.setOnClickListener {
            val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)

            // 1. AMBIL KEDUA STATUS
            val isFullVersion = licensePrefs.getBoolean("is_full_version", false)
            val isExpired = licensePrefs.getBoolean("is_expired", false)

            // 2. LOGIKA CERDAS:
            // Kita hanya blokir jika: (Sudah Expired) DAN (BUKAN Full Version)
            // Jadi kalau Full Version = True, biarpun isExpired = True, dia tetap LOLOS.

            if (isExpired && !isFullVersion) {
                // ⛔ BLOKIR (Hanya untuk User Gratisan yang habis masa trial)
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
                // ✅ LOLOS (Premium atau Masih Trial)
                checkModalBeforePOS()
            }
        }

        cardProduct.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java)) }
        cardPurchase.setOnClickListener { startActivity(Intent(this, PurchaseActivity::class.java)) }
        // 🔥 GANTI CARD REPORT BIAR BISA PILIH JENIS LAPORAN 🔥
        cardReport.setOnClickListener {
            val options = arrayOf("📊 Laporan Penjualan (Omzet)", "⚠️ Riwayat Void & Retur")

            AlertDialog.Builder(this)
                .setTitle("Pilih Jenis Laporan")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // Masuk ke Laporan Omzet (Yang Lama)
                            startActivity(Intent(this, SalesReportActivity::class.java))
                        }
                        1 -> {
                            // Masuk ke Laporan Void & Retur (Yang Baru)
                            startActivity(Intent(this, LogReportActivity::class.java))
                        }
                    }
                }
                .show()
        }
        cardUser.setOnClickListener { startActivity(Intent(this, UserListActivity::class.java)) }
        cardStore.setOnClickListener { startActivity(Intent(this, StoreSettingsActivity::class.java).apply { putExtra("TARGET", "STORE") }) }
        cardPrinter.setOnClickListener { startActivity(Intent(this, StoreSettingsActivity::class.java).apply { putExtra("TARGET", "PRINTER") }) }
        cardShift?.setOnClickListener { startActivity(Intent(this, ShiftHistoryActivity::class.java)) }
        cardLowStockInfo.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java).apply { putExtra("OPEN_TAB_INDEX", 2) }) }

        // 🔥 3. KLIK CARD ABOUT -> PINDAH HALAMAN
        cardAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // 🔥 UPDATE: MENU LOGOUT DINAMIS (ADMIN/MANAGER)
        btnLogout.setOnClickListener {
            val userName = session.getString("username", "User") ?: "User"

            // 1. Cek Status Shift Dulu
            val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
            val isShiftOpen = shiftPrefs.getBoolean("IS_OPEN_GLOBAL_SESSION", false)

            // 2. Cek Status Lisensi (Untuk Opsi Reset)
            val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
            val isFullVersion = licensePrefs.getBoolean("is_full_version", false)

            if (role == "kasir") {
                // === KHUSUS KASIR (TETAP SAMA) ===
                // Kasir biasanya wajib tutup shift, tapi kita cek juga biar rapi
                val options = if (isShiftOpen) {
                    arrayOf("💰 Tutup Kasir & Cetak Laporan", "🚪 Log Out Biasa")
                } else {
                    arrayOf("🚪 Log Out Biasa") // Kalau udh tutup, cuma bisa logout
                }

                AlertDialog.Builder(this)
                    .setTitle("Menu Keluar (Kasir)")
                    .setItems(options) { _, which ->
                        val selected = options[which]
                        if (selected.contains("Tutup Kasir")) {
                            showCloseSessionDialog(session)
                        } else {
                            performLogout(session, false)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()

            } else {
                // === KHUSUS ADMIN & MANAGER (YANG DIMINTA MAS HERU) ===

                // Gunakan List yang bisa berubah-ubah isinya
                val menuList = mutableListOf<String>()

                // 🔥 LOGIKA PINTAR:
                // Cuma tambahkan menu "Tutup Shift" kalau shiftnya MEMANG LAGI BUKA.
                if (isShiftOpen) {
                    menuList.add("💰 Tutup Shift & Cetak")
                }

                // Menu Logout selalu ada
                menuList.add("🚪 Log Out Biasa")

                // Menu Reset (Khusus Trial)
                if (!isFullVersion) {
                    menuList.add("🗑️ Log Out & HAPUS DATA (Reset)")
                }

                // Ubah jadi Array biar bisa masuk ke Dialog
                val optionsArray = menuList.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Menu Keluar ($role)")
                    .setItems(optionsArray) { _, which ->
                        val selectedMenu = optionsArray[which]

                        // Kita pakai pengecekan TEXT (String) karena urutan nomornya bisa berubah
                        when (selectedMenu) {
                            "💰 Tutup Shift & Cetak" -> showCloseSessionDialog(session)
                            "🚪 Log Out Biasa" -> performLogout(session, false)
                            "🗑️ Log Out & HAPUS DATA (Reset)" -> showResetConfirmation(session)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
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
    // =================================================================
    // 👮‍♂️ FITUR SATPAM: CEK DEVELOPER MODE
    // =================================================================

    override fun onResume() {
        super.onResume()
        cekKeamananHP()
    }

    private fun cekKeamananHP() {
        try {
            // 1. Cek Apakah Opsi Pengembang (Developer Options) Aktif?
            val isDevMode = android.provider.Settings.Global.getInt(
                contentResolver,
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            ) != 0

            // 2. Cek Apakah USB Debugging (ADB) Aktif? (Opsional, tapi disarankan)
            val isAdb = android.provider.Settings.Global.getInt(
                contentResolver,
                android.provider.Settings.Global.ADB_ENABLED, 0
            ) != 0

            // Jika SALAH SATU aktif, langsung blokir!
            if (isDevMode || isAdb) {
                tampilkanPeringatanKeamanan()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tampilkanPeringatanKeamanan() {
        // Cek apakah dialog sudah muncul (biar gak numpuk)
        if (!isFinishing) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⛔ AKSES DITOLAK (KEAMANAN)")
                .setMessage("Terdeteksi 'Developer Options' atau 'USB Debugging' sedang menyala di HP ini.\n\nDemi keamanan data dan transaksi, fitur tersebut WAJIB dimatikan.\n\nSilakan matikan di Pengaturan HP Anda lalu buka kembali aplikasi ini.")
                .setCancelable(false) // Gabisa dicancel/klik luar
                .setPositiveButton("KELUAR SEKARANG") { _, _ ->
                    finishAffinity() // Tutup Semua Halaman
                    System.exit(0)   // Matikan Proses Aplikasi
                }
                .show()
        }
    }

    // --- FUNGSI BUKA SHIFT ---
    private fun checkModalBeforePOS() {
        val prefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "default") ?: "default"

        // 🔥 GANTI DI SINI:
        // Jangan pakai nama user. Pakai nama kunci "GLOBAL" yang tetap.
        // Jadi siapapun yang login (Admin/Kasir), kuncinya SAMA.

        val GLOBAL_KEY = "IS_OPEN_GLOBAL_SESSION"

        if (!prefs.getBoolean(GLOBAL_KEY, false)) {
            // Kalau Belum Buka -> Minta Modal
            // Pastikan fungsi showInputModalDialogForPOS juga menyimpan ke GLOBAL_KEY ya!
            showInputModalDialogForPOS(prefs, username)
        } else {
            // Sudah Buka -> Langsung Masuk
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

                    val editor = prefs.edit()

                    // 🔥 PERBAIKAN DISINI: GUNAKAN KUNCI GLOBAL 🔥
                    // Supaya Admin yg buka, Kasir juga kebagian status "Buka"-nya.

                    editor.putBoolean("IS_OPEN_GLOBAL_SESSION", true)
                    editor.putFloat("MODAL_AWAL_GLOBAL", modal.toFloat())
                    editor.putLong("START_TIME_GLOBAL", System.currentTimeMillis())

                    // Hapus data sampah lama (opsional)
                    // editor.remove("IS_OPEN_$username")

                    editor.commit()

                    // Simpan ke Database
                    viewModel.openShift(username, modal)

                    Toast.makeText(this, "Shift Toko Dibuka!", Toast.LENGTH_SHORT).show()
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

        // 1. AMBIL WAKTU & DATA (SAMA)
        val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val shiftStartTime = shiftPrefs.getLong("START_TIME_GLOBAL", 0L)
        val modalAwal = shiftPrefs.getFloat("MODAL_AWAL_GLOBAL", 0f).toDouble()
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        val filterTime = if (shiftStartTime > 0) shiftStartTime else startOfDay

        val sourceData = this.allTrx
        val myTrx = sourceData.filter { it.timestamp >= filterTime }

        // 2. HITUNG RINCIAN
        var totalTunai = 0.0
        var totalQris = 0.0
        var totalDebit = 0.0
        var totalTransfer = 0.0

        // PISAHKAN PIUTANG (Agar Laporan Akurat)
        var piutangLunas = 0.0       // Uang Masuk Laci (Pelunasan)
        var piutangBelumLunas = 0.0  // Uang Belum Masuk (Hutang)

        for (trx in myTrx) {
            val method = trx.paymentMethod.lowercase()
            // Cek status lunas dari Note
            val isLunas = trx.note.contains("LUNAS", ignoreCase = true)

            when {
                method.contains("tunai") || method.contains("cash") -> totalTunai += trx.totalAmount
                method.contains("qris") -> totalQris += trx.totalAmount
                method.contains("debit") -> totalDebit += trx.totalAmount
                method.contains("transfer") -> totalTransfer += trx.totalAmount

                // 🔥 LOGIKA BARU: PISAHKAN LUNAS VS BELUM
                method.contains("piutang") || method.contains("tempo") -> {
                    if (isLunas) {
                        piutangLunas += trx.totalAmount
                    } else {
                        piutangBelumLunas += trx.totalAmount
                    }
                }

                else -> totalTunai += trx.totalAmount
            }
        }

        // 🔥 PERBAIKAN RUMUS:
        // Total Omzet = Semua transaksi (Tunai + Transfer + Hutang Lunas + Hutang Belum)
        val totalOmzet = totalTunai + totalQris + totalDebit + totalTransfer + piutangLunas + piutangBelumLunas

        // Uang Fisik Harusnya = Modal + Penjualan Tunai + Pelunasan Hutang
        val expectedCash = modalAwal + totalTunai + piutangLunas

        fun fmt(d: Double): String {
            return java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID")).format(d)
        }
        val dateStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(filterTime))

        // 🔥 PERBAIKAN TAMPILAN PESAN:
        val pesanLaporan = """
            Halo, $userName!
            Shift Mulai: $dateStr
            (Ditemukan ${myTrx.size} Transaksi)

            💵 TUNAI      : ${fmt(totalTunai)}
            💰 PIUTANG CAIR: ${fmt(piutangLunas)} (Masuk Laci)
            📱 QRIS       : ${fmt(totalQris)}
            💳 DEBIT      : ${fmt(totalDebit)}
            🏦 TRANSFER   : ${fmt(totalTransfer)}
            ⏳ PIUTANG GANTUNG: ${fmt(piutangBelumLunas)} (Belum Lunas)
            -------------------------------------
            💎 TOTAL OMZET : ${fmt(totalOmzet)}
            
            Laci Kasir Harusnya: ${fmt(expectedCash)}
            (Modal + Tunai + Pelunasan)
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("💰 Ringkasan Tutup Shift")
            .setMessage(pesanLaporan)
            .setPositiveButton("LANJUT HITUNG") { _, _ ->
                showInputCashDialog(
                    expectedCash = expectedCash, // 🔥 Pakai rumus yang sudah ditambah pelunasan
                    totalOmzet = totalOmzet,
                    userName = userName,
                    startTime = filterTime,
                    modal = modalAwal,
                    rincianTunai = totalTunai,
                    rincianQris = totalQris,
                    rincianTrf = totalTransfer,
                    rincianDebit = totalDebit,
                    rincianPiutang = piutangBelumLunas // Yang dikirim ke printer sebaiknya Sisa Hutang saja
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
        rincianDebit: Double,
        rincianPiutang: Double // 🔥 TAMBAHKAN PARAMETER INI
    ) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Total uang fisik di laci"

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

                // 2. Cetak Struk
                printShiftReport(
                    kasirName = userName,
                    startTime = startTime,
                    modal = modal,
                    tunai = rincianTunai,
                    qris = rincianQris,
                    trf = rincianTrf,
                    debit = rincianDebit,
                    piutang = rincianPiutang,
                    expected = expectedCash,
                    actual = actualCash
                )

                Toast.makeText(this, "Shift Ditutup & Laporan Dicetak!", Toast.LENGTH_SHORT).show()

                // 🔥 3. PERBAIKAN: HAPUS KUNCI GLOBAL 🔥
                val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
                shiftPrefs.edit().apply {
                    remove("IS_OPEN_GLOBAL_SESSION")
                    remove("MODAL_AWAL_GLOBAL")
                    remove("START_TIME_GLOBAL")
                    apply()
                }

                // 4. LOGOUT
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
        // 1. Hapus Sesi Login (Wajib agar user keluar)
        session.edit().clear().apply()

        // ❌ HAPUS KODE LAMA YANG INI:
        // val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        // shiftPrefs.edit().clear().apply()
        // (Kode di atas lah penyebab kenapa modal hilang terus)

        // ✅ GANTI DENGAN LOGIKA BARU:
        if (resetData) {
            // Hanya hapus data Shift jika user memilih "HAPUS DATA / RESET"
            val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
            shiftPrefs.edit().clear().apply()

            Toast.makeText(this, "Sedang menghapus data...", Toast.LENGTH_SHORT).show()
            viewModel.logoutAndReset { signOutGoogleAndExit() }
        } else {
            // Kalau Log Out Biasa, data shift JANGAN dihapus.
            // Biarkan tetap tersimpan di HP.
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
        tunai: Double, qris: Double, trf: Double, debit: Double, piutang: Double, // 🔥 TAMBAH PARAMETER PIUTANG
        expected: Double, actual: Double
    ) {
        val storePrefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val targetMac = storePrefs.getString("printer_mac", "")
        if (targetMac.isNullOrEmpty()) return

        Thread {
            try {
                // ... (Koneksi Bluetooth Sama) ...
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@Thread
                val device = bluetoothAdapter.getRemoteDevice(targetMac)
                val socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket.connect()
                val os = socket.outputStream

                // ... (Header Toko Sama) ...
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
                row("Piutang", piutang) // 🔥 CETAK PIUTANG DISINI
                p.append("--------------------------------\n")

                // Total Omzet
                val totalOmzet = tunai + qris + trf + debit + piutang // 🔥 TAMBAH PIUTANG
                p.append("\u001B\u0045\u0001")
                row("TOTAL OMZET", totalOmzet)
                p.append("\u001B\u0045\u0000")
                p.append("--------------------------------\n\n")

                // ... (Rekonsiliasi Kas Sama Saja) ...
                p.append("REKONSILIASI KAS (FISIK):\n")
                row("Modal Awal", modal)
                row("Tunai Masuk", tunai)
                // Uang Piutang TIDAK masuk rekonsiliasi kas (karena uangnya belum diterima)
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