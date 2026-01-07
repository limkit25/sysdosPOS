package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText // <--- PENTING: Import Ini
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.utils.SessionManager
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        // Cek sesi login
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        if (session.contains("username")) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // --- BINDING UI (HANYA SEKALI DEKLARASI) ---
        val layoutLogin = findViewById<LinearLayout>(R.id.layoutLogin)
        val etUser = findViewById<EditText>(R.id.etUsername)

        // 🔥 PERBAIKAN: Gunakan TextInputEditText agar sesuai XML
        val etPass = findViewById<TextInputEditText>(R.id.etPassword)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGear = findViewById<ImageButton>(R.id.btnServerSetting)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)

        // UI REGISTER
        val layoutRegister = findViewById<LinearLayout>(R.id.layoutRegister)
        val etRegUser = findViewById<EditText>(R.id.etRegUsername)
        val etRegPass = findViewById<TextInputEditText>(R.id.etRegPassword) // Ini juga TextInput
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)

        // --- TAMPILKAN VERSI APP ---
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "Ver ${pInfo.versionName}"
        } catch (e: Exception) {
            tvAppVersion.text = "Ver 1.0"
        }

        // --- CEK DATABASE KOSONG ---
        viewModel.allUsers.observe(this) { users ->
            if (users.isNullOrEmpty()) {
                layoutLogin.visibility = View.GONE
                layoutRegister.visibility = View.VISIBLE
                tvSubtitle.text = "Setup Awal Aplikasi"
            } else {
                layoutLogin.visibility = View.VISIBLE
                layoutRegister.visibility = View.GONE
                tvSubtitle.text = "Silakan Login untuk melanjutkan"
            }
        }

        // --- LOGIKA ENTER DI PASSWORD LANGSUNG LOGIN ---
        etPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick() // Otomatis tekan tombol Login
                true
            } else {
                false
            }
        }
        // 🔥 TAMBAHAN BARU: LOGIKA ENTER PASSWORD REGISTER 🔥
        etRegPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnRegister.performClick() // Otomatis tekan tombol Daftar
                true
            } else {
                false
            }
        }

        // --- TOMBOL LOGIN ---
        btnLogin.setOnClickListener {
            val u = etUser.text.toString()
            val p = etPass.text.toString()

            viewModel.login(u, p) { user ->
                if (user != null) {
                    saveSession(user)
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Username/Password Salah!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- TOMBOL SETTING SERVER ---
        btnGear.setOnClickListener { showServerDialog() }

        // --- TOMBOL REGISTER (DAFTAR ADMIN) ---
        btnRegister.setOnClickListener {
            val u = etRegUser.text.toString()
            val p = etRegPass.text.toString()

            if (u.isNotEmpty() && p.isNotEmpty()) {
                // 1. Buat User Admin Baru
                val newAdmin = User(username = u, password = p, role = "admin")
                viewModel.insertUser(newAdmin)

                // 2. Simpan Sesi (Auto Login)
                saveSession(newAdmin)
                Toast.makeText(this, "Akun Admin Dibuat!", Toast.LENGTH_SHORT).show()

                // 🔥 PERBAIKAN: KE SETTING TOKO DULU (BUKAN DASHBOARD) 🔥
                val intent = Intent(this, StoreSettingsActivity::class.java)

                // Kirim sinyal bahwa ini adalah "Setup Awal"
                // Agar StoreSettingsActivity menyembunyikan tombol Back & mengubah tombol Simpan jadi "Lanjut"
                intent.putExtra("IS_INITIAL_SETUP", true)

                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Isi semua data!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showServerDialog() {
        val currentUrl = sessionManager.getServerUrl().replace("http://", "").replace("/", "")
        val inputEdit = EditText(this)
        inputEdit.hint = "Contoh: 192.168.1.15:3000"
        inputEdit.setText(currentUrl)
        inputEdit.setPadding(60, 50, 60, 50)

        AlertDialog.Builder(this)
            .setTitle("⚙️ Setting IP Server")
            .setMessage("Masukkan IP Address Laptop:")
            .setView(inputEdit)
            .setPositiveButton("SIMPAN") { _, _ ->
                val newIp = inputEdit.text.toString().trim()
                if (newIp.isNotEmpty()) {
                    sessionManager.saveServerUrl(newIp)
                    Toast.makeText(this, "IP Server Tersimpan!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun saveSession(user: User) {
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val editor = session.edit()
        editor.putString("username", user.username)
        editor.putString("role", user.role)
        editor.apply()
    }
}