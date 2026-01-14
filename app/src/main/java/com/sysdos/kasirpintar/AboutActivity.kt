package com.sysdos.kasirpintar

import android.content.Context // 🔥 Wajib untuk SharedPreferences
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View // 🔥 Wajib untuk View.GONE
import android.widget.Button // 🔥 Wajib untuk Tombol Upgrade
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

// 🔥 IMPORT KHUSUS KONEKSI SERVER (RETROFIT)
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.PaymentResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val tvVersion = findViewById<TextView>(R.id.tvAppVersion)
        val btnWA = findViewById<CardView>(R.id.btnContactWA)
        val btnEmail = findViewById<CardView>(R.id.btnContactEmail)

        // 🔥 DEFINISI TOMBOL UPGRADE
        val btnUpgrade = findViewById<Button>(R.id.btnUpgradePremium)

        // 1. Tampilkan Versi Aplikasi secara Otomatis
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "Versi Aplikasi: ${pInfo.versionName}"
        } catch (e: Exception) {
            tvVersion.text = "Versi 1.0"
        }

        // 2. Tombol Kembali
        btnBack.setOnClickListener { finish() }

        // 3. Tombol WhatsApp
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

        // 4. Tombol Email
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

        // 5. 🔥 LOGIKA TOMBOL UPGRADE PREMIUM (BARU) 🔥

        // Cek dulu, kalau sudah premium, tombol beli disembunyikan
        val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
        val isFull = licensePrefs.getBoolean("is_full_version", false)

        if (isFull) {
            btnUpgrade.visibility = View.GONE
        }

        btnUpgrade.setOnClickListener {
            // ============================================================
            // 🚀 MODE BYPASS (TESTING TAMPILAN)
            // Langsung buka PaymentActivity tanpa tanya Server dulu
            // ============================================================

            // 1. Siapkan Niat (Intent) ke PaymentActivity
/*
val intent = Intent(this@AboutActivity, PaymentActivity::class.java)

// 2. Isi Link Palsu dulu (Misal ke Google atau Dummy Midtrans)
// Nanti kalau Server sudah bener, ini akan otomatis diganti link asli
intent.putExtra("PAYMENT_URL", "https://simulator.sandbox.midtrans.com/gopay/partner/app/payment-pin")

// 3. JALAN!
startActivity(intent)

 */
// ============================================================
// ⚠️ KODE ASLI (KITA MATIKAN DULU SAMPAI SERVER BENAR)
// ============================================================

val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
// Ambil email/username yang sedang login
val email = session.getString("username", "") ?: ""

if (email.isNotEmpty()) {
Toast.makeText(this, "Membuat Pesanan...", Toast.LENGTH_SHORT).show()

// Pastikan di ApiClient.kt Anda sudah ada 'webClient'
// Jika error di 'webClient', ganti jadi 'apiService' atau 'client' sesuai nama di ApiClient.kt Anda
val api = ApiClient.webClient

api.createPayment(email).enqueue(object : Callback<PaymentResponse> {
override fun onResponse(call: Call<PaymentResponse>, response: Response<PaymentResponse>) {
if (response.isSuccessful && response.body() != null) {
    val url = response.body()!!.payment_url

    // 🔥 UBAH BAGIAN INI (PAKAI WEBVIEW) 🔥
    val intent = Intent(this@AboutActivity, PaymentActivity::class.java)
    intent.putExtra("PAYMENT_URL", url)
    startActivity(intent)
} else {
    Toast.makeText(this@AboutActivity, "Gagal koneksi server bayar. Code: ${response.code()}", Toast.LENGTH_SHORT).show()
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
}