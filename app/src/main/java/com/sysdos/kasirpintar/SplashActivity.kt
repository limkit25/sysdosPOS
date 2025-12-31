package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Sembunyikan Action Bar bawaan (jika masih nongol)
        supportActionBar?.hide()

        // Tahan 3 detik, lalu pindah ke LOGIN
        Handler(Looper.getMainLooper()).postDelayed({

            // PENTING: Arahkan ke LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)

            finish() // Tutup Splash agar tidak bisa di-back

        }, 3000)
    }
}