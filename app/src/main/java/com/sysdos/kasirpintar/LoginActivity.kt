package com.sysdos.kasirpintar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.utils.SessionManager
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import okhttp3.ResponseBody // <--- IMPORT PENTING INI
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var sessionManager: SessionManager

    // Launcher untuk Google Sign In
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                handleGoogleLoginSuccess(account)
            } catch (e: ApiException) {
                Toast.makeText(this, "Gagal Login: Code ${e.statusCode}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "Login Dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Cek Panggilan dari Welcome Screen
        if (intent.getBooleanExtra("OPEN_REGISTER_DIRECTLY", false)) {
            showRegisterDialog()
        }

        sessionManager = SessionManager(this)

        // Cek Sesi (Sudah Login?)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        if (session.contains("username")) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        val etUser = findViewById<TextInputEditText>(R.id.etUsername)
        val etPass = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogle = findViewById<MaterialCardView>(R.id.btnGoogleLogin)
        val btnRegisterLink = findViewById<TextView>(R.id.btnRegisterLink)
        val btnGear = findViewById<ImageButton>(R.id.btnServerSetting)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)

        // Tampilkan Versi Aplikasi
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "Ver ${pInfo.versionName}"
        } catch (e: Exception) {}

        // 1. LOGIN MANUAL
        // 1. LOGIN MANUAL
        btnLogin.setOnClickListener {
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString().trim()
            if (u.isNotEmpty() && p.isNotEmpty()) {
                viewModel.login(u, p) { user ->
                    if (user != null) {
                        // 🔥 1. SISIPKAN DI SINI (Login Manual Sukses)
                        viewModel.sendDataToSalesSystem(user)
                        viewModel.checkServerLicense(user.username)

                        saveSession(user)
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Username/Password Salah!", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Harap isi semua kolom", Toast.LENGTH_SHORT).show()
            }
        }

        etPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick()
                true
            } else false
        }

        // 2. LOGIN GOOGLE
        btnGoogle.setOnClickListener { startGoogleSignIn() }

        // 3. DAFTAR (REGISTER)
        btnRegisterLink.setOnClickListener { showRegisterDialog() }

        // 4. SETTING SERVER (IP TOKO)
        btnGear.setOnClickListener { showServerDialog() }
    }

    // --- FUNGSI GOOGLE SIGN IN ---
    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = mGoogleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleLoginSuccess(account: GoogleSignInAccount) {
        val email = account.email ?: return
        val name = account.displayName ?: "User Google"

        viewModel.checkGoogleUser(email) { existingUser ->

            if (existingUser != null) {
                // === SKENARIO 1: USER SUDAH ADA ===
                Log.d("GoogleLogin", "User lama ditemukan: ${existingUser.username}")

                // 🔥 2. SISIPKAN DI SINI (Opsional: Lapor User Lama Login)
                viewModel.sendDataToSalesSystem(existingUser)
                viewModel.checkServerLicense(existingUser.username)

                saveSession(existingUser)
                syncToWeb(existingUser)
                gotoDashboard()

            } else {
                // === SKENARIO 2: USER BARU ===
                Log.d("GoogleLogin", "User baru! Mendaftar...")

                val newUser = User(
                    name = name,
                    username = email,
                    password = "google_auth",
                    role = "admin"
                )

                viewModel.insertUser(newUser)

                // 🔥 3. SISIPKAN DI SINI (WAJIB: User Baru dari Google)
                viewModel.sendDataToSalesSystem(newUser)

                syncToWeb(newUser)
                saveSession(newUser)

                Toast.makeText(this, "Selamat Datang Admin, $name!", Toast.LENGTH_LONG).show()

                val intent = Intent(this, StoreSettingsActivity::class.java)
                intent.putExtra("IS_INITIAL_SETUP", true)
                startActivity(intent)
                finish()
            }
        }
    }

    // 🔥 PERBAIKAN UTAMA DI SINI (Pakai ResponseBody) 🔥
    private fun syncToWeb(user: User) {
        val call = ApiClient.webClient.registerUser(user)

        // Menggunakan Callback<ResponseBody> sesuai error log
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if(response.isSuccessful) Log.d("GoogleLogin", "Sync Web Sukses")
                else Log.e("GoogleLogin", "Gagal Web: ${response.code()}")
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("GoogleLogin", "Gagal Koneksi Web: ${t.message}")
            }
        })
    }

    // --- FUNGSI REGISTER MANUAL ---
    private fun showRegisterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_user_entry, null)

        val etName = view.findViewById<EditText>(R.id.etName)
        val etUser = view.findViewById<EditText>(R.id.etUsername)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etPass = view.findViewById<EditText>(R.id.etPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Daftar Akun Baru")
            .setView(view)
            .setPositiveButton("DAFTAR", null) // null dulu
            .setNegativeButton("BATAL", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val nama = etName.text.toString().trim()
                val email = etUser.text.toString().trim()
                val hp = etPhone.text.toString().trim()
                val pass = etPass.text.toString().trim()

                // Validasi
                if (nama.isEmpty() || email.isEmpty() || hp.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this, "Semua data wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 1. TAMPILKAN LOADING (Agar user menunggu)
                val loading = android.app.ProgressDialog(this)
                loading.setMessage("Sedang mendaftarkan ke Server Pusat...")
                loading.setCancelable(false)
                loading.show()

                // Siapkan Data
                val newUser = User(
                    name = nama,
                    username = email,
                    phone = hp,
                    password = pass,
                    role = "admin"
                )

                // 2. SIMPAN DATABASE HP (LOKAL)
                viewModel.insertUser(newUser)
                viewModel.sendDataToSalesSystem(newUser)

                // 3. KIRIM KE WEB (CLOUD)
                // Kita panggil manual disini agar bisa mengontrol kapan 'finish()' dipanggil
                val call = ApiClient.webClient.registerUser(newUser)

                call.enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        loading.dismiss() // Tutup loading

                        if (response.isSuccessful) {
                            Log.d("Register", "✅ SUKSES MASUK WEB!")
                            Toast.makeText(this@LoginActivity, "Registrasi Online Berhasil!", Toast.LENGTH_LONG).show()
                        } else {
                            Log.e("Register", "❌ Gagal Web: ${response.code()}")
                            Toast.makeText(this@LoginActivity, "Disimpan di HP (Server Web Error: ${response.code()})", Toast.LENGTH_LONG).show()
                        }

                        // 🔥 BARU PINDAH HALAMAN DISINI (Setelah selesai lapor server)
                        finalizeRegistration(newUser, dialog)
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        loading.dismiss() // Tutup loading
                        Log.e("Register", "⚠️ Gagal Koneksi Web: ${t.message}")
                        Toast.makeText(this@LoginActivity, "Offline: Data tersimpan di HP saja.", Toast.LENGTH_LONG).show()

                        // 🔥 Tetap pindah halaman walaupun internet mati
                        finalizeRegistration(newUser, dialog)
                    }
                })
            }
        }
        dialog.show()
    }

    private fun saveSession(user: User) {
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val editor = session.edit()
        editor.putString("username", user.username)
        editor.putString("role", user.role)
        editor.putBoolean("is_logged_in", true)
        editor.apply()
    }

    private fun gotoDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun showServerDialog() {
        val currentUrl = sessionManager.getServerUrl().replace("http://", "").replace("/", "")
        val inputEdit = EditText(this)
        inputEdit.hint = "Contoh: 192.168.1.15:3000"
        inputEdit.setText(currentUrl)
        inputEdit.setPadding(60, 50, 60, 50)

        AlertDialog.Builder(this)
            .setTitle("Setting IP Server (Toko)")
            .setMessage("Masukkan IP Komputer Kasir untuk operasional harian.")
            .setView(inputEdit)
            .setPositiveButton("SIMPAN") { _, _ ->
                val newIp = inputEdit.text.toString().trim()
                if (newIp.isNotEmpty()) {
                    sessionManager.saveServerUrl(newIp)
                    Toast.makeText(this, "IP Tersimpan!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }
    // Fungsi kecil untuk pindah halaman biar rapi
    private fun finalizeRegistration(user: User, dialog: AlertDialog) {
        saveSession(user)
        viewModel.checkServerLicense(user.username)
        // Ke Setup Toko
        val intent = Intent(this, StoreSettingsActivity::class.java)
        intent.putExtra("IS_INITIAL_SETUP", true)
        startActivity(intent)

        dialog.dismiss()
        finish() // ✅ Tutup LoginActivity di sini
    }
}