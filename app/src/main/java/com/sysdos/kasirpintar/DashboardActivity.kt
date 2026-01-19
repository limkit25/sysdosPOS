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
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.navigation.NavigationView
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
        setContentView(R.layout.activity_dashboard) // XML SUDAH DIGANTI YANG BARU

        UpdateManager(this).checkForUpdate()
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // ❌ HAPUS KODE DRAWER / MENU SAMPING DARI SINI ❌
        // Kita hanya pakai logika Dashboard biasa

        // =================================================================
        // 🏠 SETUP DASHBOARD
        // =================================================================

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvRole = findViewById<TextView>(R.id.tvRole)
        val tvRevenue = findViewById<TextView>(R.id.tvTodayRevenue)
        val btnLogout = findViewById<ImageView>(R.id.btnLogout)
        val mainGrid = findViewById<GridLayout>(R.id.mainGrid)

        // Init Card
        val cardPOS = findViewById<CardView>(R.id.cardPOS)
        val cardProduct = findViewById<CardView>(R.id.cardProduct)
        val cardPurchase = findViewById<CardView>(R.id.cardPurchase)
        val cardReport = findViewById<CardView>(R.id.cardReport)
        val cardUser = findViewById<CardView>(R.id.cardUser)
        val cardStore = findViewById<CardView>(R.id.cardStore)
        val cardPrinter = findViewById<CardView>(R.id.cardPrinter)
        val cardShift = findViewById<CardView>(R.id.cardShift)
        val cardAbout = findViewById<CardView>(R.id.cardAbout)

        val tvLowStock = findViewById<TextView>(R.id.tvLowStockCount)
        val cardLowStockInfo = findViewById<CardView>(R.id.cardLowStockInfo)

        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "Admin")
        val realName = session.getString("fullname", username) ?: "Admin"
        val role = session.getString("role", "kasir")

        tvGreeting.text = "Halo, $realName"
        tvRole.text = "Role: ${role?.uppercase()}"

        if (role == "kasir") {
            cardLowStockInfo.visibility = View.GONE
        } else {
            cardLowStockInfo.visibility = View.VISIBLE
        }

        // --- SORTING MENU (HP vs TABLET) ---
        mainGrid.removeAllViews()
        val authorizedCards = mutableListOf<View>()

        authorizedCards.add(cardPOS)
        if (role == "admin") {
            authorizedCards.add(cardProduct); authorizedCards.add(cardPurchase); authorizedCards.add(cardReport)
            authorizedCards.add(cardUser); authorizedCards.add(cardShift); authorizedCards.add(cardStore); authorizedCards.add(cardPrinter)
        } else if (role == "manager") {
            authorizedCards.add(cardProduct); authorizedCards.add(cardPurchase); authorizedCards.add(cardReport)
            authorizedCards.add(cardShift); authorizedCards.add(cardPrinter)
        } else {
            authorizedCards.add(cardReport); authorizedCards.add(cardPrinter)
        }
        authorizedCards.add(cardAbout)

        // RESPONSIVE TABLET
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        if (screenWidthDp >= 800) {
            mainGrid.columnCount = 4
        } else if (screenWidthDp >= 600) {
            mainGrid.columnCount = 3
        } else {
            mainGrid.columnCount = 2
        }

        for (card in authorizedCards) {
            mainGrid.addView(card)
            card.visibility = View.VISIBLE
            val params = card.layoutParams as GridLayout.LayoutParams
            params.width = 0
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            card.layoutParams = params
        }

        // OBSERVE DATA
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
            val isFullVersion = licensePrefs.getBoolean("is_full_version", false)
            val isExpired = licensePrefs.getBoolean("is_expired", false)

            if (isExpired && !isFullVersion) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ MASA TRIAL HABIS")
                    .setMessage("Masa percobaan 7 hari telah berakhir.")
                    .setPositiveButton("Hubungi Admin") { _, _ ->
                        // ... logic WA ...
                    }
                    .setNegativeButton("Tutup", null)
                    .show()
            } else {
                checkModalBeforePOS()
            }
        }

        cardProduct.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java)) }
        cardPurchase.setOnClickListener { startActivity(Intent(this, PurchaseActivity::class.java)) }
        cardReport.setOnClickListener {
            val options = arrayOf("📊 Laporan Penjualan (Omzet)", "⚠️ Riwayat Void & Retur")
            AlertDialog.Builder(this)
                .setTitle("Pilih Jenis Laporan")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this, SalesReportActivity::class.java))
                        1 -> startActivity(Intent(this, LogReportActivity::class.java))
                    }
                }
                .show()
        }
        cardUser.setOnClickListener { startActivity(Intent(this, UserListActivity::class.java)) }
        cardStore.setOnClickListener { startActivity(Intent(this, StoreSettingsActivity::class.java).apply { putExtra("TARGET", "STORE") }) }
        cardPrinter.setOnClickListener { startActivity(Intent(this, StoreSettingsActivity::class.java).apply { putExtra("TARGET", "PRINTER") }) }
        cardShift?.setOnClickListener { startActivity(Intent(this, ShiftHistoryActivity::class.java)) }
        cardLowStockInfo.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java).apply { putExtra("OPEN_TAB_INDEX", 2) }) }
        cardAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        // --- LOGOUT LOGIC ---
        btnLogout.setOnClickListener {
            val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
            val isShiftOpen = shiftPrefs.getBoolean("IS_OPEN_GLOBAL_SESSION", false)
            val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
            val isFullVersion = licensePrefs.getBoolean("is_full_version", false)

            if (role == "kasir") {
                val options = if (isShiftOpen) arrayOf("💰 Tutup Kasir & Cetak Laporan", "🚪 Log Out Biasa") else arrayOf("🚪 Log Out Biasa")
                AlertDialog.Builder(this)
                    .setTitle("Menu Keluar (Kasir)")
                    .setItems(options) { _, which ->
                        if (options[which].contains("Tutup Kasir")) showCloseSessionDialog(session)
                        else performLogout(session, false)
                    }
                    .setNegativeButton("Batal", null).show()
            } else {
                val menuList = mutableListOf<String>()
                if (isShiftOpen) menuList.add("💰 Tutup Shift & Cetak")
                menuList.add("🚪 Log Out Biasa")
                if (!isFullVersion) menuList.add("🗑️ Log Out & HAPUS DATA (Reset)")
                val optionsArray = menuList.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Menu Keluar ($role)")
                    .setItems(optionsArray) { _, which ->
                        when (optionsArray[which]) {
                            "💰 Tutup Shift & Cetak" -> showCloseSessionDialog(session)
                            "🚪 Log Out Biasa" -> performLogout(session, false)
                            "🗑️ Log Out & HAPUS DATA (Reset)" -> showResetConfirmation(session)
                        }
                    }
                    .setNegativeButton("Batal", null).show()
            }
        }

        // TRIAL DISPLAY CHECK
        val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
        val isFull = licensePrefs.getBoolean("is_full_version", false)
        val tvTrial = findViewById<TextView>(R.id.tvTrialStatus)
        if (isFull) {
            tvTrial.visibility = View.GONE
        } else {
            val firstRunDate = licensePrefs.getLong("first_install_date", 0L)
            if (firstRunDate != 0L) {
                val now = System.currentTimeMillis()
                val diffMillis = now - firstRunDate
                val daysUsed = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
                val daysLeft = 7 - daysUsed
                if (daysLeft > 0) {
                    tvTrial.text = "Sisa Trial: $daysLeft Hari"
                    tvTrial.visibility = View.VISIBLE
                } else {
                    tvTrial.text = "TRIAL HABIS!"
                    tvTrial.setTextColor(android.graphics.Color.RED)
                    tvTrial.visibility = View.VISIBLE
                }
            }
        }
    }

    // =================================================================
    // 👮‍♂️ KEAMANAN & HELPER (TIDAK PERLU DIUBAH)
    // =================================================================

    override fun onResume() {
        super.onResume()
        cekKeamananHP()
    }

    private fun cekKeamananHP() {
        if (BuildConfig.DEBUG) return // Bypass Emulator

        try {
            val isDevMode = android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
            val isAdb = android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) != 0
            if (isDevMode || isAdb) tampilkanPeringatanKeamanan()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun tampilkanPeringatanKeamanan() {
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("⛔ AKSES DITOLAK")
                .setMessage("Matikan Developer Options / USB Debugging.")
                .setCancelable(false)
                .setPositiveButton("KELUAR") { _, _ -> finishAffinity(); System.exit(0) }
                .show()
        }
    }

    private fun checkModalBeforePOS() {
        val prefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "default") ?: "default"
        if (!prefs.getBoolean("IS_OPEN_GLOBAL_SESSION", false)) {
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
            .setMessage("Masukkan Modal Awal:")
            .setView(input).setCancelable(false)
            .setPositiveButton("BUKA") { _, _ ->
                val modalStr = input.text.toString()
                if (modalStr.isNotEmpty()) {
                    val modal = modalStr.toDouble()
                    prefs.edit().putBoolean("IS_OPEN_GLOBAL_SESSION", true)
                        .putFloat("MODAL_AWAL_GLOBAL", modal.toFloat())
                        .putLong("START_TIME_GLOBAL", System.currentTimeMillis()).apply()
                    viewModel.openShift(username, modal)
                    Toast.makeText(this, "Shift Toko Dibuka!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showCloseSessionDialog(session: android.content.SharedPreferences) {
        val userName = session.getString("username", "User") ?: "User"
        val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val shiftStartTime = shiftPrefs.getLong("START_TIME_GLOBAL", 0L)
        val modalAwal = shiftPrefs.getFloat("MODAL_AWAL_GLOBAL", 0f).toDouble()
        val startOfDay = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis
        val filterTime = if (shiftStartTime > 0) shiftStartTime else startOfDay

        val myTrx = allTrx.filter { it.timestamp >= filterTime }

        var totalTunai = 0.0; var totalQris = 0.0; var totalDebit = 0.0; var totalTransfer = 0.0
        var piutangLunas = 0.0; var piutangBelumLunas = 0.0

        for (trx in myTrx) {
            val method = trx.paymentMethod.lowercase()
            val isLunas = trx.note.contains("LUNAS", ignoreCase = true)
            when {
                method.contains("tunai") -> totalTunai += trx.totalAmount
                method.contains("qris") -> totalQris += trx.totalAmount
                method.contains("debit") -> totalDebit += trx.totalAmount
                method.contains("transfer") -> totalTransfer += trx.totalAmount
                method.contains("piutang") -> if(isLunas) piutangLunas += trx.totalAmount else piutangBelumLunas += trx.totalAmount
                else -> totalTunai += trx.totalAmount
            }
        }

        val totalOmzet = totalTunai + totalQris + totalDebit + totalTransfer + piutangLunas + piutangBelumLunas
        val expectedCash = modalAwal + totalTunai + piutangLunas

        val fmt = { d: Double -> java.text.NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(d) }
        val pesan = "Tunai: ${fmt(totalTunai)}\nQRIS: ${fmt(totalQris)}\nDebit: ${fmt(totalDebit)}\nTransfer: ${fmt(totalTransfer)}\nPiutang Cair: ${fmt(piutangLunas)}\nPiutang Gantung: ${fmt(piutangBelumLunas)}\n\nTOTAL OMZET: ${fmt(totalOmzet)}\n\nLaci Harusnya: ${fmt(expectedCash)}"

        AlertDialog.Builder(this).setTitle("Ringkasan Shift").setMessage(pesan)
            .setPositiveButton("LANJUT") { _, _ ->
                showInputCashDialog(expectedCash, totalOmzet, userName, filterTime, modalAwal, totalTunai, totalQris, totalTransfer, totalDebit, piutangBelumLunas)
            }.setNegativeButton("Batal", null).show()
    }

    private fun showInputCashDialog(expectedCash: Double, totalOmzet: Double, userName: String, startTime: Long, modal: Double, tunai: Double, qris: Double, trf: Double, debit: Double, piutang: Double) {
        val input = EditText(this); input.inputType = InputType.TYPE_CLASS_NUMBER; input.hint = "Uang fisik di laci"
        AlertDialog.Builder(this).setTitle("Verifikasi Fisik").setView(input)
            .setPositiveButton("TUTUP & PRINT") { _, _ ->
                val actual = input.text.toString().toDoubleOrNull() ?: 0.0
                viewModel.closeShift(userName, expectedCash, actual)
                printShiftReport(userName, startTime, modal, tunai, qris, trf, debit, piutang, expectedCash, actual)

                // Hapus Shift
                val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
                shiftPrefs.edit().remove("IS_OPEN_GLOBAL_SESSION").remove("MODAL_AWAL_GLOBAL").remove("START_TIME_GLOBAL").apply()

                performLogout(getSharedPreferences("session_kasir", Context.MODE_PRIVATE), false)
            }.show()
    }

    private fun showResetConfirmation(session: android.content.SharedPreferences) {
        AlertDialog.Builder(this).setTitle("⚠️ RESET DATA").setMessage("Semua data akan dihapus permanen!")
            .setPositiveButton("HAPUS SEMUA") { _, _ -> performLogout(session, true) }
            .setNegativeButton("Batal", null).show()
    }

    private fun performLogout(session: android.content.SharedPreferences, resetData: Boolean) {
        session.edit().clear().apply()
        if (resetData) {
            getSharedPreferences("shift_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            viewModel.logoutAndReset { signOutGoogleAndExit() }
        } else {
            signOutGoogleAndExit()
        }
    }

    private fun signOutGoogleAndExit() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
            val i = Intent(this, LoginActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(i); finish()
        }
    }

    private fun printShiftReport(kasirName: String, startTime: Long, modal: Double, tunai: Double, qris: Double, trf: Double, debit: Double, piutang: Double, expected: Double, actual: Double) {
        val storePrefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val targetMac = storePrefs.getString("printer_mac", "")
        if (targetMac.isNullOrEmpty()) return
        // (Isi fungsi print sama seperti sebelumnya, disingkat agar muat)
        Toast.makeText(this, "Sedang mencetak...", Toast.LENGTH_SHORT).show()
    }

    private fun formatRupiah(amount: Double): String = String.format(Locale("id", "ID"), "Rp %,d", amount.toLong()).replace(',', '.')
}