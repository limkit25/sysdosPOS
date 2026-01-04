package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.TimeUnit

class SplashActivity : AppCompatActivity() {

    // SETTING DURASI TRIAL DISINI
    private val TRIAL_DAYS = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Tampilkan Versi
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            findViewById<TextView>(R.id.tvVersion).text = "Versi $version"
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // --- 🛑 LOGIKA CEK TRIAL ---
        if (checkTrialExpired()) {
            return // JANGAN LANJUT KE LOGIN
        }
        // ---------------------------

        // Lanjut Loading Biasa
        Handler(Looper.getMainLooper()).postDelayed({
            val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
            val isLoggedIn = session.getBoolean("is_logged_in", false)

            if (isLoggedIn) {
                startActivity(Intent(this, DashboardActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2000)
    }

    private fun checkTrialExpired(): Boolean {
        val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)

        // 🔥 1. CEK APAKAH SUDAH FULL VERSION (TOKEN)? 🔥
        // Ini bagian penting yang kurang di kode Bapak tadi
        val isFull = prefs.getBoolean("is_full_version", false)
        if (isFull) {
            return false // LANGSUNG LOLOS (Tidak Expired) karena sudah Premium
        }

        // 2. Cek Tanggal Install
        var firstRunDate = prefs.getLong("first_install_date", 0L)
        if (firstRunDate == 0L) {
            firstRunDate = System.currentTimeMillis()
            prefs.edit().putLong("first_install_date", firstRunDate).apply()
        }

        // 3. Hitung Sisa Hari
        val now = System.currentTimeMillis()
        val diffMillis = now - firstRunDate
        val daysUsed = TimeUnit.MILLISECONDS.toDays(diffMillis)

        if (daysUsed >= TRIAL_DAYS) {
            showExpiredDialog()
            return true // Trial Habis
        } else {
            val sisa = TRIAL_DAYS - daysUsed
            Toast.makeText(this, "Sisa Masa Trial: $sisa Hari", Toast.LENGTH_LONG).show()
            return false // Masih Aman
        }
    }

    private fun showExpiredDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Masa Percobaan Habis 🔒")
            .setMessage("Masa trial $TRIAL_DAYS hari telah berakhir.\n\nSilakan hubungi Admin untuk membeli token aktivasi.")
            .setCancelable(false)
            .setPositiveButton("Keluar") { _, _ ->
                finishAffinity()
            }
            .create()
        dialog.show()
    }
}