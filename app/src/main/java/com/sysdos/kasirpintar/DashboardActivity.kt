package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
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

        // --- 1. OTOMATIS TANYA MODAL SAAT BUKA ---
        checkDailyModal()

        // Hubungkan ID (Sesuai Layout Baru)
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvRole = findViewById<TextView>(R.id.tvRole)
        val tvRevenue = findViewById<TextView>(R.id.tvTodayRevenue)

        val cardPOS = findViewById<CardView>(R.id.cardPOS)
        val cardProduct = findViewById<CardView>(R.id.cardProduct)
        val cardReport = findViewById<CardView>(R.id.cardReport)
        val cardUser = findViewById<CardView>(R.id.cardUser)

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnSettingToko = findViewById<ImageView>(R.id.btnSettings)

        // Session
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "Admin")
        val role = session.getString("role", "kasir")

        // Set Teks Profil
        tvGreeting.text = "Halo, $username"
        tvRole.text = "Role: ${role?.uppercase()}"

        // Atur Hak Akses
        cardPOS.visibility = View.VISIBLE
        cardProduct.visibility = View.VISIBLE
        cardReport.visibility = View.VISIBLE
        cardUser.visibility = View.VISIBLE

        if (role == "kasir") {
            cardProduct.visibility = View.GONE
            // cardReport.visibility = View.GONE // Kasir bisa lihat laporan? (Opsional)
            cardUser.visibility = View.GONE
        } else if (role == "manager") {
            cardUser.visibility = View.GONE
        }

        // Hitung Pendapatan (CARA MANUAL YANG AMAN)
        viewModel.allTransactions.observe(this) { transactions ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date())
            var totalHariIni = 0.0

            for (trx in transactions) {
                // Konversi timestamp ke tanggal
                val trxDateStr = sdf.format(Date(trx.timestamp))
                // Cek apakah transaksi hari ini?
                if (trxDateStr == todayStr) {
                    totalHariIni += trx.totalAmount
                }
            }
            tvRevenue.text = String.format(Locale("id", "ID"), "Rp %,d", totalHariIni.toLong())
        }

        // Tombol Navigasi Menu
        cardPOS.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        cardProduct.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java)) }
        cardReport.setOnClickListener { startActivity(Intent(this, ReportActivity::class.java)) }
        cardUser.setOnClickListener { startActivity(Intent(this, UserListActivity::class.java)) }

        btnSettingToko.setOnClickListener {
            if (role == "kasir") {
                Toast.makeText(this, "Akses Ditolak: Hanya Admin/Manager", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, StoreSettingsActivity::class.java))
            }
        }

        // --- LOGOUT LOGIC ---
        btnLogout.setOnClickListener {
            if (role == "kasir") {
                // KASIR: Pilih Setor atau Logout Biasa
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
                // ADMIN: Logout Biasa
                AlertDialog.Builder(this)
                    .setTitle("Konfirmasi")
                    .setMessage("Yakin ingin keluar aplikasi?")
                    .setPositiveButton("Ya") { _, _ -> performLogout(session) }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    // --- FUNGSI PENDUKUNG (TETAP SAMA SEPERTI YANG LAMA) ---

    private fun performLogout(session: android.content.SharedPreferences) {
        session.edit().clear().apply()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showCloseSessionDialog(session: android.content.SharedPreferences) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Total uang fisik di laci"

        AlertDialog.Builder(this)
            .setTitle("🔒 Tutup Kasir")
            .setMessage("Hitung uang fisik dan masukkan jumlahnya:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("SETOR & LOGOUT") { _, _ ->
                val strFisik = input.text.toString()
                if (strFisik.isNotEmpty()) {
                    val uangFisik = strFisik.toDouble()

                    // Hitung Selisih
                    val tvRev = findViewById<TextView>(R.id.tvTodayRevenue)
                    val cleanString = tvRev.text.toString().replace("[^0-9]".toRegex(), "")
                    val omzetSistem = if (cleanString.isNotEmpty()) cleanString.toDouble() else 0.0

                    val totalHarusAda = modalHarian + omzetSistem
                    val selisih = uangFisik - totalHarusAda

                    var pesan = "Sesi Ditutup. "
                    pesan += if (selisih == 0.0) "Klop! 👍"
                    else if (selisih < 0) "⚠️ MINUS Rp ${formatRupiah(abs(selisih))}"
                    else "⚠️ LEBIH Rp ${formatRupiah(selisih)}"

                    Toast.makeText(this, pesan, Toast.LENGTH_LONG).show()
                    performLogout(session)
                } else {
                    Toast.makeText(this, "Isi jumlah uang!", Toast.LENGTH_SHORT).show()
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
            prefs.edit().clear().apply()
            showInputModalDialog(prefs, todayDate)
        } else {
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