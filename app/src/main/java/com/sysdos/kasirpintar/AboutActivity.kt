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

class AboutActivity : AppCompatActivity() {

    // 1. Variabel Global Menu Samping
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navView: com.google.android.material.navigation.NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // =============================================================
        // 🔥 1. SETUP MENU SAMPING (DRAWER)
        // =============================================================
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        val btnMenu = findViewById<View>(R.id.btnMenuDrawer) // Tombol Burger

        // A. Klik Tombol Burger
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // B. Setup Header Menu (Nama User)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")

        if (navView.headerCount > 0) {
            val header = navView.getHeaderView(0)
            header.findViewById<TextView>(R.id.tvHeaderName).text = realName
            header.findViewById<TextView>(R.id.tvHeaderRole).text = "Role: ${role?.uppercase()}"
        }

        // C. Logika Navigasi Pindah Halaman
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
        // 🔥 2. KODINGAN HALAMAN ABOUT
        // =============================================================

        // ❌ JANGAN PAKAI btnBack LAGI (Sudah dihapus dari XML)

        val tvVersion = findViewById<TextView>(R.id.tvAppVersion)
        val btnWA = findViewById<CardView>(R.id.btnContactWA)
        val btnEmail = findViewById<CardView>(R.id.btnContactEmail)
        val btnUpgrade = findViewById<Button>(R.id.btnUpgradePremium)

        // Tampilkan Versi Aplikasi
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "Versi Aplikasi: ${pInfo.versionName}"
        } catch (e: Exception) {
            tvVersion.text = "Versi 1.0"
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
        }

        btnUpgrade.setOnClickListener {
            val emailUser = session.getString("username", "") ?: ""

            if (emailUser.isNotEmpty()) {
                Toast.makeText(this, "Membuat Pesanan...", Toast.LENGTH_SHORT).show()

                // PANGGIL SERVER
                val api = ApiClient.webClient
                api.createPayment(emailUser).enqueue(object : Callback<PaymentResponse> {
                    override fun onResponse(call: Call<PaymentResponse>, response: Response<PaymentResponse>) {
                        if (response.isSuccessful && response.body() != null) {
                            val url = response.body()!!.payment_url

                            // Buka WebView Payment
                            val intent = Intent(this@AboutActivity, PaymentActivity::class.java)
                            intent.putExtra("PAYMENT_URL", url)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@AboutActivity, "Gagal koneksi server. Code: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<PaymentResponse>, t: Throwable) {
                        Toast.makeText(this@AboutActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                Toast.makeText(this, "Gagal: Email user tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Tambahkan ini di luar onCreate agar tombol Back HP menutup menu dulu
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}