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
import com.sysdos.kasirpintar.api.LeadRequest
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.utils.SessionManager
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var sessionManager: SessionManager

    // 🔥 VARIABEL PENTING: Untuk membedakan Login atau Register
    private var isRegisterMode = false
    // Biar dialog register bisa ditutup otomatis saat sukses Google
    private var activeRegisterDialog: AlertDialog? = null

    // Launcher Google
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                handleGoogleResult(account)
            } catch (e: ApiException) {
                Toast.makeText(this, "Gagal Login Google: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        if (intent.getBooleanExtra("OPEN_REGISTER_DIRECTLY", false)) {
            showRegisterDialog()
        }

        sessionManager = SessionManager(this)
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
        val btnGoogleLogin = findViewById<MaterialCardView>(R.id.btnGoogleLogin) // Tombol di Layar Login
        val btnRegisterLink = findViewById<TextView>(R.id.btnRegisterLink)
        val btnGear = findViewById<ImageButton>(R.id.btnServerSetting)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "Ver ${pInfo.versionName}"
        } catch (e: Exception) {}

        // 1. LOGIN MANUAL (YANG SUDAH DIPERBAIKI)
        btnLogin.setOnClickListener {
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString().trim()

            if (u.isNotEmpty() && p.isNotEmpty()) {

                // LANGKAH 1: Cek di Database HP (Lokal)
                viewModel.login(u, p) { user ->
                    if (user != null) {
                        // KASUS A: User masih ada di HP -> Langsung Masuk
                        processLoginSuccess(user)
                    } else {
                        // KASUS B: Data di HP Kosong (Habis Logout/Reset)
                        // 🔥 JANGAN MENYERAH! CEK KE SERVER DULU 🔥

                        val loading = android.app.ProgressDialog(this)
                        loading.setMessage("Mencari data akun Anda di Server...")
                        loading.show()

                        viewModel.checkUserOnCloud(u) { existsOnCloud ->
                            loading.dismiss()

                            if (existsOnCloud) {
                                // ✅ KETEMU DI SERVER!
                                // Artinya dia owner lama yang habis logout.
                                // Kita "Pulihkan" akunnya ke HP ini pakai password yang dia ketik.

                                val restoredUser = User(
                                    name = "Owner Toko", // Nama default krn server gak simpan nama lengkap user login
                                    username = u,
                                    password = p, // Pakai password yang barusan diketik
                                    role = "admin"
                                )

                                // Simpan ulang ke Database HP
                                viewModel.insertUser(restoredUser)

                                Toast.makeText(this, "Akun dipulihkan dari Server! Masuk...", Toast.LENGTH_LONG).show()
                                processLoginSuccess(restoredUser)

                            } else {
                                // ❌ MEMANG GAK ADA DI MANA-MANA
                                Toast.makeText(this, "Username/Password Salah atau Belum Daftar!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Harap isi email dan password", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. LOGIN GOOGLE (TOMBOL DI LAYAR LOGIN)
        btnGoogleLogin.setOnClickListener {
            isRegisterMode = false // Mode LOGIN
            startGoogleSignIn()
        }

        btnRegisterLink.setOnClickListener { showRegisterDialog() }
        btnGear.setOnClickListener { showServerDialog() }

        etPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick()
                true
            } else false
        }
    }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        // Logout dulu biar bisa pilih akun lain
        mGoogleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(mGoogleSignInClient.signInIntent)
        }
    }

    // 🔥 UPDATE LOGIKA LOGIN GOOGLE (BIAR BISA GANTI-GANTI AKUN)
    private fun handleGoogleResult(account: GoogleSignInAccount) {
        val email = account.email ?: return
        val name = account.displayName ?: "User Google"

        // 1. Cek Database Lokal (HP)
        viewModel.checkGoogleUser(email) { existingUser ->

            if (isRegisterMode) {
                // === MODE REGISTER (User Sengaja Klik Tombol Daftar) ===
                if (existingUser != null) {
                    Toast.makeText(this, "Email ini sudah ada di HP ini. Login saja.", Toast.LENGTH_LONG).show()
                } else {
                    // Cek ke Cloud dulu, jangan sampai duplikat
                    viewModel.checkUserOnCloud(email) { existsOnCloud ->
                        if (existsOnCloud) {
                            Toast.makeText(this, "Email ini SUDAH TERDAFTAR di Server! Silakan Login.", Toast.LENGTH_LONG).show()
                        } else {
                            performGoogleRegistration(name, email)
                        }
                    }
                }

            } else {
                // === MODE LOGIN (User Klik Masuk) ===
                if (existingUser != null) {
                    // KASUS A: User ada di HP -> Langsung Masuk
                    processLoginSuccess(existingUser)
                } else {
                    // KASUS B: User TIDAK ADA di HP (Mungkin habis di-reset User lain)
                    // 🔥 JANGAN LANGSUNG TOLAK! CEK CLOUD DULU 🔥

                    val loading = android.app.ProgressDialog(this)
                    loading.setMessage("Mencari data akun Anda...")
                    loading.show()

                    viewModel.checkUserOnCloud(email) { existsOnCloud ->
                        loading.dismiss()

                        if (existsOnCloud) {
                            // ✅ KETEMU DI SERVER! LAKUKAN RESTORE (PULIHKAN AKUN)
                            Toast.makeText(this, "Akun ditemukan! Memulihkan data...", Toast.LENGTH_SHORT).show()

                            // Hapus sisa data user sebelumnya (User B)
                            viewModel.logoutAndReset {
                                // Insert Ulang User A ke Database Lokal
                                val restoredUser = User(
                                    name = name,
                                    username = email,
                                    password = "google_auth",
                                    role = "admin"
                                )
                                viewModel.insertUser(restoredUser)

                                // Cek Lisensi & Masuk
                                processLoginSuccess(restoredUser)
                            }

                        } else {
                            // ❌ MEMANG TIDAK ADA DI MANA-MANA
                            showErrorDialog("Akun Belum Terdaftar", "Email ($email) belum terdaftar di sistem kami.\n\nSilakan klik 'Daftar Akun Baru'.")
                        }
                    }
                }
            }
        }
    }

    // 🔥 UPDATE FUNGSI INI (Google Register)
    private fun performGoogleRegistration(name: String, email: String) {
        val loading = android.app.ProgressDialog(this)
        loading.setMessage("Mendaftarkan akun Google...")
        loading.setCancelable(false)
        loading.show()

        // 1. Reset Data Lama
        viewModel.logoutAndReset {
            getSharedPreferences("app_license", Context.MODE_PRIVATE).edit().clear().apply()

            // 2. Simpan User Baru
            val newUser = User(
                name = name,
                username = email,
                password = "google_auth",
                role = "admin"
            )
            viewModel.insertUser(newUser)

            // 3. Kirim ke Cloud
            val leadRequest = LeadRequest(
                name = name, store_name = "Toko $name", store_address = "-", store_phone = "-", phone = "-", email = email
            )
            ApiClient.webClient.registerLead(leadRequest).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    loading.dismiss()
                    if (activeRegisterDialog?.isShowing == true) activeRegisterDialog?.dismiss()

                    Toast.makeText(this@LoginActivity, "Registrasi Berhasil! Silakan Setup Toko.", Toast.LENGTH_LONG).show()

                    // 🔥 ARAHKAN KE SETUP TOKO, BUKAN DASHBOARD
                    finalizeRegistration(newUser, activeRegisterDialog)
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    loading.dismiss()
                    if (activeRegisterDialog?.isShowing == true) activeRegisterDialog?.dismiss()

                    Toast.makeText(this@LoginActivity, "Registrasi Offline. Silakan Setup Toko.", Toast.LENGTH_LONG).show()

                    // 🔥 ARAHKAN KE SETUP TOKO
                    finalizeRegistration(newUser, activeRegisterDialog)
                }
            })
        }
    }

    private fun processLoginSuccess(user: User) {
        // Cek apakah data toko masih default/kosong?
        // Kalau kosong (baru daftar), arahkan ke Setup Toko
        // Kalau sudah ada, arahkan ke Dashboard

        viewModel.sendDataToSalesSystem(user)
        viewModel.checkServerLicense(user.username)
        saveSession(user)

        // Cek sederhana: apakah user ini baru saja dibuat? (Biasanya kalau baru, role masih admin default dan belum ada transaksi)
        // Atau kita bisa selalu arahkan ke Dashboard, nanti Dashboard yang cek kelengkapan data.
        // Untuk aman, kita langsung ke Dashboard saja.

        Toast.makeText(this, "Selamat Datang, ${user.name}!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun showRegisterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_user_entry, null)

        // Find Views
        val etName = view.findViewById<EditText>(R.id.etName)
        val etUser = view.findViewById<EditText>(R.id.etUsername)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etPass = view.findViewById<EditText>(R.id.etPassword)
        val btnGoogleReg = view.findViewById<MaterialCardView>(R.id.btnGoogleRegister) // Tombol Baru

        val dialog = AlertDialog.Builder(this)
            .setTitle("Daftar Akun Baru")
            .setView(view)
            .setPositiveButton("DAFTAR MANUAL", null)
            .setNegativeButton("BATAL", null)
            .create()

        activeRegisterDialog = dialog // Simpan referensi biar bisa ditutup

        // --- TOMBOL GOOGLE DI REGISTER ---
        btnGoogleReg.setOnClickListener {
            isRegisterMode = true // Set Mode ke REGISTER
            startGoogleSignIn()
        }

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                // LOGIKA DAFTAR MANUAL (Sama seperti sebelumnya)
                val nama = etName.text.toString().trim()
                val email = etUser.text.toString().trim()
                val hp = etPhone.text.toString().trim()
                val pass = etPass.text.toString().trim()

                if (nama.isEmpty() || email.isEmpty() || hp.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this, "Lengkapi semua data!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val loading = android.app.ProgressDialog(this)
                loading.setMessage("Proses Pendaftaran...")
                loading.show()

                viewModel.logoutAndReset {
                    getSharedPreferences("app_license", Context.MODE_PRIVATE).edit().clear().apply()

                    val newUser = User(name = nama, username = email, phone = hp, password = pass, role = "admin")
                    viewModel.insertUser(newUser)

                    val req = LeadRequest(name = nama, store_name = "Toko $nama", store_address = "-", store_phone = "-", phone = hp, email = email)
                    ApiClient.webClient.registerLead(req).enqueue(object : Callback<ResponseBody> {
                        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                            loading.dismiss()
                            finalizeRegistration(newUser, dialog)
                        }
                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                            loading.dismiss()
                            finalizeRegistration(newUser, dialog)
                        }
                    })
                }
            }
        }
        dialog.show()
    }

    // 🔥 UPDATE FUNGSI INI (Pintu Keluar Register)
    private fun finalizeRegistration(user: User, dialog: AlertDialog?) {
        // 1. Simpan Sesi dulu biar dianggap login
        saveSession(user)
        viewModel.sendDataToSalesSystem(user)
        viewModel.checkServerLicense(user.username)

        dialog?.dismiss()

        // 2. 🔥 BELOKKAN KE STORE SETTINGS (SETUP AWAL)
        val intent = Intent(this, StoreSettingsActivity::class.java)
        // Kirim sinyal bahwa ini adalah "Pendaftaran Awal"
        intent.putExtra("IS_INITIAL_SETUP", true)
        startActivity(intent)

        // 3. Tutup LoginActivity agar user tidak bisa back ke login
        finish()
    }

    private fun showErrorDialog(title: String, msg: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun saveSession(user: User) {
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val editor = session.edit()
        editor.putString("username", user.username)
        editor.putString("role", user.role)
        editor.putBoolean("is_logged_in", true)
        editor.apply()
    }

    private fun showServerDialog() {
        val currentUrl = sessionManager.getServerUrl().replace("http://", "").replace("/", "")
        val inputEdit = EditText(this)
        inputEdit.setText(currentUrl)
        AlertDialog.Builder(this)
            .setTitle("Setting IP Server")
            .setView(inputEdit)
            .setPositiveButton("SIMPAN") { _, _ ->
                sessionManager.saveServerUrl(inputEdit.text.toString().trim())
            }.show()
    }
}