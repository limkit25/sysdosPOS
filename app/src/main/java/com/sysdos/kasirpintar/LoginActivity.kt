package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Cek sesi login dulu (kalau user tutup aplikasi tapi belum logout)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        if (session.contains("username")) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // UI LOGIN
        val layoutLogin = findViewById<LinearLayout>(R.id.layoutLogin)
        val etUser = findViewById<EditText>(R.id.etUsername)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // UI REGISTER (ADMIN PERTAMA)
        val layoutRegister = findViewById<LinearLayout>(R.id.layoutRegister)
        val etRegUser = findViewById<EditText>(R.id.etRegUsername)
        val etRegPass = findViewById<EditText>(R.id.etRegPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)

        // --- CEK APAKAH DATABASE KOSONG? ---
        viewModel.allUsers.observe(this) { users ->
            if (users.isNullOrEmpty()) {
                // KOSONG -> TAMPILKAN REGISTER
                layoutLogin.visibility = View.GONE
                layoutRegister.visibility = View.VISIBLE
                tvSubtitle.text = "Setup Awal Aplikasi"
            } else {
                // ADA ISI -> TAMPILKAN LOGIN
                layoutLogin.visibility = View.VISIBLE
                layoutRegister.visibility = View.GONE
                tvSubtitle.text = "Silakan Login untuk melanjutkan"
            }
        }

        // --- LOGIKA LOGIN BIASA ---
        btnLogin.setOnClickListener {
            val u = etUser.text.toString()
            val p = etPass.text.toString()

            val login = viewModel.login(u, p) { user ->
                if (user != null) {
                    saveSession(user)
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Login Gagal!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- LOGIKA REGISTER ADMIN PERTAMA ---
        btnRegister.setOnClickListener {
            val u = etRegUser.text.toString()
            val p = etRegPass.text.toString()

            if (u.isNotEmpty() && p.isNotEmpty()) {
                // 1. Buat User Admin
                val newAdmin = User(username = u, password = p, role = "admin")
                viewModel.insertUser(newAdmin)

                // 2. Simpan Sesi (Auto Login)
                saveSession(newAdmin)

                // 3. Pindah ke Halaman Setup Toko
                Toast.makeText(this, "Akun Admin Dibuat!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, StoreSettingsActivity::class.java)
                intent.putExtra("IS_INITIAL_SETUP", true) // KODE RAHASIA BAHWA INI SETUP AWAL
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Isi semua data!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSession(user: User) {
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val editor = session.edit()
        editor.putString("username", user.username)
        editor.putString("role", user.role)
        editor.apply()
    }
}