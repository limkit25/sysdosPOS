package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val btnLogin = findViewById<Button>(R.id.btnWelcomeLogin)
        val btnRegister = findViewById<Button>(R.id.btnWelcomeRegister)

        // 1. Tombol LOGIN -> Buka Login Activity Biasa
        btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // 2. Tombol DAFTAR -> Buka Login Activity TAPI bawa pesan "Tolong Buka Dialog Register"
        btnRegister.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("OPEN_REGISTER_DIRECTLY", true) // <--- KUNCI RAHASIANYA
            startActivity(intent)
        }
    }
}