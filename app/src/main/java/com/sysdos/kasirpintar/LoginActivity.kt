package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cek apakah user sudah login sebelumnya? Kalau ya, langsung ke Dashboard
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        if (session.contains("username")) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // --- 1. HUBUNGKAN ID XML (INI YANG TADI ERROR) ---
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // --- 2. LOGIKA KLIK TOMBOL ---
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {

                // Cek Login ke Database
                viewModel.login(username, password) { user ->
                    if (user != null) {
                        saveSession(user) // Simpan sesi
                    } else {
                        // JAGA-JAGA: Jika Database kosong, izinkan admin/admin masuk pertama kali
                        if (username == "admin" && password == "admin") {
                            Toast.makeText(this, "Login Admin Darurat", Toast.LENGTH_SHORT).show()
                            // Buat user admin otomatis biar besok2 tersimpan
                            val adminBaru = User(username = "admin", password = "admin", role = "admin")
                            viewModel.insertUser(adminBaru)
                            saveSession(adminBaru)
                        } else {
                            // Gagal Login
                            runOnUiThread {
                                Toast.makeText(this, "Username / Password Salah!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Mohon isi username & password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSession(user: User) {
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val editor = session.edit()
        editor.putString("username", user.username)
        editor.putString("role", user.role)
        editor.apply()

        Toast.makeText(this, "Selamat Datang, ${user.username}!", Toast.LENGTH_SHORT).show()

        // Pindah ke Dashboard
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish() // Tutup halaman login agar tidak bisa back
    }
}