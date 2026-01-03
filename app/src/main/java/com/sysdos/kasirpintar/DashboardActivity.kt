package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class DashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private var modalHarian: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]


        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvRole = findViewById<TextView>(R.id.tvRole)
        val tvRevenue = findViewById<TextView>(R.id.tvTodayRevenue)
        val btnLogout = findViewById<ImageView>(R.id.btnLogout)
        val mainGrid = findViewById<GridLayout>(R.id.mainGrid)

        // AMBIL SEMUA KARTU MENU
        val cardPOS = findViewById<CardView>(R.id.cardPOS)
        val cardProduct = findViewById<CardView>(R.id.cardProduct)
        val cardReport = findViewById<CardView>(R.id.cardReport)
        val cardUser = findViewById<CardView>(R.id.cardUser)
        val cardStore = findViewById<CardView>(R.id.cardStore)
        val cardPrinter = findViewById<CardView>(R.id.cardPrinter)

        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "Admin")
        val role = session.getString("role", "kasir")

        tvGreeting.text = "Halo, $username"
        tvRole.text = "Role: ${role?.uppercase()}"

        // --- LOGIKA SORTING (REVISI MANAGER) ---
        // 1. Kosongkan Grid
        mainGrid.removeAllViews()

        // 2. Siapkan List
        val authorizedCards = mutableListOf<View>()

        // POS Selalu Ada untuk Semua Role
        authorizedCards.add(cardPOS)

        // 3. Cek Role
        if (role == "admin") {
            // ADMIN: Semua Menu Ada
            authorizedCards.add(cardProduct)
            authorizedCards.add(cardReport)
            authorizedCards.add(cardUser)
            authorizedCards.add(cardStore)
            authorizedCards.add(cardPrinter)
        }
        else if (role == "manager") {
            // MANAGER: Gudang, Laporan, Toko, Printer (Hanya User yang hilang)
            authorizedCards.add(cardProduct)
            authorizedCards.add(cardReport)
            authorizedCards.add(cardStore)   // [SUDAH DITAMBAHKAN]
            authorizedCards.add(cardPrinter) // [SUDAH DITAMBAHKAN]
        }
        else {
            // KASIR: Hanya Laporan & Printer
            authorizedCards.add(cardReport)
            authorizedCards.add(cardPrinter)
            // Kasir tidak boleh edit Gudang, User, Setting Toko
        }

        // 4. Masukkan ke Grid (Otomatis Rapi)
        for (card in authorizedCards) {
            mainGrid.addView(card)
            card.visibility = View.VISIBLE
        }

        // --- LOGIKA LAINNYA TETAP SAMA ---
        viewModel.allTransactions.observe(this) { transactions ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date())
            var totalHariIni = 0.0
            for (trx in transactions) {
                val trxDateStr = sdf.format(Date(trx.timestamp))
                if (trxDateStr == todayStr) totalHariIni += trx.totalAmount
            }
            tvRevenue.text = formatRupiah(totalHariIni)
        }

        cardPOS.setOnClickListener {
            checkModalBeforePOS() // <--- Panggil fungsi ini, jangan langsung startActivity
        }
        cardProduct.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java)) }
        cardReport.setOnClickListener { startActivity(Intent(this, ReportActivity::class.java)) }
        cardUser.setOnClickListener { startActivity(Intent(this, UserListActivity::class.java)) }

        cardStore.setOnClickListener {
            val intent = Intent(this, StoreSettingsActivity::class.java)
            intent.putExtra("TARGET", "STORE")
            startActivity(intent)
        }
        cardPrinter.setOnClickListener {
            val intent = Intent(this, StoreSettingsActivity::class.java)
            intent.putExtra("TARGET", "PRINTER")
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            if (role == "kasir") {
                val options = arrayOf("💰 Tutup Kasir (Setor Harian)", "Log Out Biasa")
                AlertDialog.Builder(this)
                    .setTitle("Pilih Aksi Keluar")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> showCloseSessionDialog(session)
                            1 -> performLogout(session)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Konfirmasi")
                    .setMessage("Yakin ingin keluar aplikasi?")
                    .setPositiveButton("Ya") { _, _ -> performLogout(session) }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
        val tvLowStock = findViewById<TextView>(R.id.tvLowStockCount)
        val cardLowStockInfo = findViewById<CardView>(R.id.cardLowStockInfo)

        // 1. OBSERVE TRANSAKSI (Untuk Omzet - SUDAH ADA)
        viewModel.allTransactions.observe(this) { transactions ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date())
            var totalHariIni = 0.0
            for (trx in transactions) {
                val trxDateStr = sdf.format(Date(trx.timestamp))
                if (trxDateStr == todayStr) totalHariIni += trx.totalAmount
            }
            tvRevenue.text = formatRupiah(totalHariIni)
        }

        // 2. OBSERVE PRODUK (UNTUK HITUNG STOK MENIPIS - BARU)
        viewModel.allProducts.observe(this) { products ->
            // Hitung barang yang stoknya kurang dari 5 tapi lebih dari 0
            val lowStockCount = products.count { it.stock < 5 }

            tvLowStock.text = "$lowStockCount Item"

            // Ubah warna teks kalau ada isinya biar eye-catching
            if (lowStockCount > 0) {
                tvLowStock.setTextColor(android.graphics.Color.RED)
            } else {
                tvLowStock.setTextColor(android.graphics.Color.parseColor("#E65100")) // Orange Tua
                tvLowStock.text = "Aman"
            }
        }

        // KLIK KARTU STOK MENIPIS -> BUKA GUDANG TAB KE-3
        cardLowStockInfo.setOnClickListener {
            val intent = Intent(this, ProductListActivity::class.java)
            // Kirim kode "2" artinya buka Tab index ke-2 (Laporan/Aset)
            intent.putExtra("OPEN_TAB_INDEX", 2)
            startActivity(intent)
        }
    }
    // --- FUNGSI 1: CEK MODAL SEBELUM MASUK KASIR ---
    private fun checkModalBeforePOS() {
        val prefs = getSharedPreferences("daily_finance", Context.MODE_PRIVATE)

        // Cek Status: Apakah Shift sedang Terbuka?
        val isShiftOpen = prefs.getBoolean("is_shift_open", false)

        if (!isShiftOpen) {
            // JIKA TUTUP -> WAJIB INPUT MODAL BARU (Buka Shift Baru)
            // Walaupun hari yang sama, kalau statusnya tutup, ya harus buka lagi.
            showInputModalDialogForPOS(prefs)
        } else {
            // JIKA BUKA -> LANJUT JUALAN
            // Load modal yang sedang aktif biar gak 0
            modalHarian = prefs.getFloat("modal_awal", 0f).toDouble()
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    // --- FUNGSI 2: POPUP INPUT MODAL (DENGAN LOG KE DATABASE) ---
    private fun showInputModalDialogForPOS(prefs: android.content.SharedPreferences) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Contoh: 200000"

        val todayStr = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🏪 Buka Shift Baru")
            .setMessage("Masukkan Modal Awal untuk shift ini:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("BUKA SHIFT") { _, _ ->
                val modalStr = input.text.toString()
                if (modalStr.isNotEmpty()) {
                    val modal = modalStr.toFloat()

                    prefs.edit().apply {
                        putString("last_date", todayStr)
                        putFloat("modal_awal", modal)
                        putBoolean("is_shift_open", true) // <--- TANDAI SHIFT DIBUKA
                        apply()
                    }
                    modalHarian = modal.toDouble()

                    // Catat ke Database
                    val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                    val user = session.getString("username", "Kasir") ?: "Kasir"
                    viewModel.insertShiftLog("OPEN", user, 0.0, modal.toDouble())

                    android.widget.Toast.makeText(this, "Shift Dibuka!", android.widget.Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    android.widget.Toast.makeText(this, "Wajib isi modal!", android.widget.Toast.LENGTH_SHORT).show()
                    showInputModalDialogForPOS(prefs)
                }
            }
            .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun performLogout(session: android.content.SharedPreferences) {
        session.edit().clear().apply()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showCloseSessionDialog(session: android.content.SharedPreferences) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Total uang fisik di laci"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔒 Tutup Shift & Logout")
            .setMessage("Masukkan total uang tunai yang ada di laci saat ini:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("TUTUP SHIFT") { _, _ ->
                val strFisik = input.text.toString()
                if (strFisik.isNotEmpty()) {
                    val uangFisik = strFisik.toDouble()

                    // Hitung Omzet Sistem (Ambil dari TextView Dashboard)
                    val tvRev = findViewById<android.widget.TextView>(R.id.tvTodayRevenue)
                    val cleanString = tvRev.text.toString().replace("[^0-9]".toRegex(), "")
                    val omzetSistem = if (cleanString.isNotEmpty()) cleanString.toDouble() else 0.0

                    // Total Seharusnya (Modal Shift Ini + Omzet Hari Ini)
                    val totalHarusAda = modalHarian + omzetSistem
                    val selisih = uangFisik - totalHarusAda

                    // Catat ke Database
                    val user = session.getString("username", "Kasir") ?: "Kasir"
                    viewModel.insertShiftLog("CLOSE", user, totalHarusAda, uangFisik)

                    // [PENTING] Reset Status Shift jadi TUTUP
                    val prefs = getSharedPreferences("daily_finance", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putBoolean("is_shift_open", false) // <--- TANDAI SHIFT DITUTUP
                        putFloat("modal_awal", 0f) // Reset modal biar aman
                        apply()
                    }

                    android.widget.Toast.makeText(this, "Shift Berhasil Ditutup.", android.widget.Toast.LENGTH_LONG).show()
                    performLogout(session)
                } else {
                    android.widget.Toast.makeText(this, "Isi jumlah uang!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun checkDailyModal() {
        val prefs = getSharedPreferences("daily_finance", Context.MODE_PRIVATE)
        val todayDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val lastSavedDate = prefs.getString("last_date", "")

        if (lastSavedDate != todayDate) {
            // Cek: Apakah ini akun baru / baru install? (lastSavedDate masih kosong)
            if (lastSavedDate == "") {
                // KASUS 1: BARU PERTAMA KALI -> JANGAN GANGGU DENGAN POPUP
                // Kita set otomatis 0 dulu. Kalau mau isi, bisa lewat menu Laporan nanti.
                prefs.edit().apply {
                    putString("last_date", todayDate)
                    putFloat("modal_awal", 0f)
                    apply()
                }
                modalHarian = 0.0
            } else {
                // KASUS 2: SUDAH LAMA TAPI GANTI HARI (BESOKNYA) -> WAJIB INPUT MODAL BARU
                // Reset data kemarin
                prefs.edit().clear().apply()
                // Tampilkan Popup
                showInputModalDialog(prefs, todayDate)
            }
        } else {
            // HARI YANG SAMA -> AMBIL DATA YG ADA
            modalHarian = prefs.getFloat("modal_awal", 0f).toDouble()
        }
    }

    private fun showInputModalDialog(prefs: android.content.SharedPreferences, todayStr: String) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Contoh: 200000"

        AlertDialog.Builder(this)
            .setTitle("☀️ Buka Toko")
            .setMessage("Masukkan Modal Awal Hari Ini:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("SIMPAN") { _, _ ->
                val modalStr = input.text.toString()
                if (modalStr.isNotEmpty()) {
                    val modal = modalStr.toFloat()
                    prefs.edit().apply {
                        putString("last_date", todayStr)
                        putFloat("modal_awal", modal)
                        apply()
                    }
                    modalHarian = modal.toDouble()
                    Toast.makeText(this, "Semangat Berjualan!", Toast.LENGTH_SHORT).show()
                } else {
                    showInputModalDialog(prefs, todayStr)
                }
            }
            .show()
    }

    private fun formatRupiah(amount: Double): String {
        return String.format(Locale("id", "ID"), "%,d", amount.toLong())
    }
}