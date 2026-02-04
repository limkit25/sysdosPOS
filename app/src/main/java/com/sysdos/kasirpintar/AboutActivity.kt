package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat // 🔥 Wajib untuk Drawer

// Import Server (Retrofit)
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.PaymentResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.appcompat.app.AlertDialog
import android.content.DialogInterface

class AboutActivity : AppCompatActivity() {

    // Drawer Removed


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // =============================================================
        // 🔥 1. SETUP MENU SAMPING (DRAWER) -> REMOVED
        // =============================================================

        // B. Setup Header Menu (Nama User)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")


        // =============================================================
        // 🔥 2. KODINGAN HALAMAN ABOUT
        // =============================================================

        // ❌ JANGAN PAKAI btnBack LAGI (Sudah dihapus dari XML)

        val tvVersion = findViewById<TextView>(R.id.tvAppVersion)
        val btnWA = findViewById<CardView>(R.id.btnContactWA)
        val btnEmail = findViewById<CardView>(R.id.btnContactEmail)
        val btnUpgrade = findViewById<Button>(R.id.btnUpgradePremium)
        val btnCheckUpdate = findViewById<Button>(R.id.btnCheckUpdate) // 🔥 BUTTON BARU

        // Tampilkan Versi Aplikasi
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "Versi Aplikasi: ${pInfo.versionName}"
        } catch (e: Exception) {
            tvVersion.text = "Versi 1.0"
        }

        // Logic Panduan Aplikasi
        findViewById<Button>(R.id.btnHelpGuide).setOnClickListener {
             startActivity(Intent(this, HelpActivity::class.java))
        }

        // Logic Update Manual
        btnCheckUpdate.setOnClickListener {
             Toast.makeText(this, "Mengecek Update...", Toast.LENGTH_SHORT).show()
             UpdateManager(this).checkForUpdate(isManual = true)
        }

        // Tombol WhatsApp
        btnWA.setOnClickListener {
            val phoneNumber = "628179842043"
            val message = "Halo Admin Sysdos POS, saya butuh bantuan."
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=$message")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp tidak terinstall", Toast.LENGTH_SHORT).show()
            }
        }

        // Tombol Email
        btnEmail.setOnClickListener {
            val email = "support@sysdos.my.id"
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "Support Sysdos POS")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Aplikasi Email tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }

        // ============================================================
        // 🔥 3. LOGIKA UPGRADE PREMIUM
        // ============================================================
        val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
        val isFull = licensePrefs.getBoolean("is_full_version", false)

        if (isFull) {
            btnUpgrade.visibility = View.GONE
        } else {
            btnUpgrade.text = "FORM AKTIVASI" // 🔥 GANTI TEKS
        }

        btnUpgrade.setOnClickListener {
            val emailUser = session.getString("username", "") ?: ""

            if (emailUser.isNotEmpty()) {
                Toast.makeText(this, "Membuat Pesanan...", Toast.LENGTH_SHORT).show()



                // 🔥 LANGSUNG KE FORM AKTIVASI (FULL FORM)
                val intent = Intent(this@AboutActivity, ActivationActivity::class.java)
                intent.putExtra("STORE_NAME", "Toko Saya") 
                intent.putExtra("STORE_PHONE", "08123456789")
                intent.putExtra("STORE_TYPE", "Retail")
                
                // Default ke 1 Bulan, tapi user bisa ganti di dalam form
                intent.putExtra("PLAN_NAME", "PREMIUM 1 BULAN") 
                intent.putExtra("PRICE", "Rp 50.000")
                
                startActivity(intent)
                // (Code removed)
            } else {
                Toast.makeText(this, "Gagal: Email user tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }

        // 🔥 LOGIKA SIMULASI MENU (DUMMY DATA) -> Removed
    }

    // Tambahkan ini di luar onCreate agar tombol Back HP menutup menu dulu

    // Tambahkan ini di luar onCreate agar tombol Back HP menutup menu dulu
    override fun onBackPressed() {
        super.onBackPressed()
    }
}