package com.sysdos.kasirpintar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

// IMPORT WAJIB
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.LeadRequest
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.utils.SessionManager
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    private var isRegisterMode = false
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

        // Setup Google Client
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        setContentView(R.layout.activity_login)

        // Cek apakah ada request buka register langsung
        if (intent.getBooleanExtra("OPEN_REGISTER_DIRECTLY", false)) {
            showRegisterDialog()
        }

        // Cek Session (Kalau sudah login, langsung ke Dashboard)
        sessionManager = SessionManager(this)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        if (session.contains("username")) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Binding View
        val etUser = findViewById<TextInputEditText>(R.id.etUsername)
        val etPass = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogleLogin = findViewById<MaterialCardView>(R.id.btnGoogleLogin)
        val btnRegisterLink = findViewById<TextView>(R.id.btnRegisterLink)
        val btnGear = findViewById<ImageButton>(R.id.btnServerSetting)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "Ver ${pInfo.versionName}"
        } catch (e: Exception) {}

        // =================================================================
        // 🔒 1. LOGIKA LOGIN MANUAL (USERNAME & PASSWORD)
        // =================================================================
        btnLogin.setOnClickListener {
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString().trim()

            if (u.isNotEmpty() && p.isNotEmpty()) {
                // A. Cek di Database HP dulu
                viewModel.getUserByEmail(u) { existingUser ->
                    if (existingUser != null) {
                        // User Ada di HP -> Cek Password
                        // User Ada di HP -> Cek Password (Hybrid: Bisa Text Biasa / Hash BCrypt)
                        val storedPassword = existingUser.password
                        val isMatch = if (storedPassword.startsWith("$2a$")) {
                            // Cek pakai BCrypt (Untuk User Web)
                            try {
                                org.mindrot.jbcrypt.BCrypt.checkpw(p, storedPassword)
                            } catch (e: Exception) { false }
                        } else {
                            // Cek string biasa (Untuk Owner / User Lama)
                            storedPassword == p
                        }

                        if (isMatch) {
                            processLoginSuccess(existingUser)
                        } else {
                            Toast.makeText(this, "❌ Password Salah!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // B. User Kosong di HP -> Cek Server (Siapa tau ganti HP)
                        val loading = android.app.ProgressDialog(this)
                        loading.setMessage("Mencari akun di server...")
                        loading.show()

                        viewModel.checkUserOnCloud(u) { response ->
                            loading.dismiss()

                            if (response != null) {
                                // 🔥 SATPAM 1: CEK APAKAH AKUN DITEMUKAN? 🔥
                                if (response.message.lowercase().contains("tidak ditemukan")) {
                                    showErrorDialog("Gagal Masuk", "Email $u belum terdaftar di sistem.\nSilakan Daftar Akun dulu.")
                                }
                                // 🔥 SATPAM 2: CEK BLOCK 🔥
                                else if (response.status == "BLOCKED") {
                                    showErrorDialog("Akses Ditolak", "Akun terkunci di HP lain:\n${response.message}")
                                }
                                else {
                                    // ✅ LOLOS: Akun Valid -> Restore ke HP
                                    val restoredUser = User(
                                        name = "Owner Toko",
                                        username = u,
                                        password = p, // Simpan password yang diketik user
                                        role = "admin"
                                    )
                                    viewModel.insertUser(restoredUser)

                                    val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
                                    prefs.edit().putBoolean("is_full_version", response.status == "PREMIUM").apply()

                                    Toast.makeText(this, "Akun Dipulihkan!", Toast.LENGTH_LONG).show()
                                    processLoginSuccess(restoredUser)
                                }
                            } else {
                                Toast.makeText(this, "Gagal koneksi ke server.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Harap isi email dan password", Toast.LENGTH_SHORT).show()
            }
        }

        // =================================================================
        // 🔒 2. LOGIKA LOGIN GOOGLE
        // =================================================================
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
        mGoogleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(mGoogleSignInClient.signInIntent)
        }
    }

    private fun handleGoogleResult(account: GoogleSignInAccount) {
        val email = account.email ?: return
        val name = account.displayName ?: "User Google"

        viewModel.checkGoogleUser(email) { existingUser ->

            // --- MODE REGISTER (User klik Daftar) ---
            if (isRegisterMode) {
                if (existingUser != null) {
                    Toast.makeText(this, "Email sudah ada di HP ini. Login saja.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.checkUserOnCloud(email) { response ->
                        if (response != null && !response.message.lowercase().contains("tidak ditemukan")) {
                            Toast.makeText(this, "Email SUDAH TERDAFTAR di Server! Login saja.", Toast.LENGTH_LONG).show()
                        } else {
                            performGoogleRegistration(name, email)
                        }
                    }
                }
            }

            // --- MODE LOGIN (User klik Masuk) ---
            else {
                if (existingUser != null) {
                    processLoginSuccess(existingUser)
                } else {
                    val loading = android.app.ProgressDialog(this)
                    loading.setMessage("Mencari data akun Anda...")
                    loading.show()

                    viewModel.checkUserOnCloud(email) { response ->
                        loading.dismiss()

                        if (response != null) {
                            // 🔥 SATPAM 1: CEK APAKAH AKUN DITEMUKAN? 🔥
                            if (response.message.lowercase().contains("tidak ditemukan")) {
                                showErrorDialog("Akses Ditolak", "Email $email belum terdaftar.\nSilakan 'Daftar Akun' terlebih dahulu.")
                                mGoogleSignInClient.signOut()
                            }
                            // 🔥 SATPAM 2: CEK BLOCK 🔥
                            else if (response.status == "BLOCKED") {
                                showErrorDialog("Akses Ditolak!", "Akun terkunci:\n${response.message}")
                                mGoogleSignInClient.signOut()
                            }
                            else {
                                // ✅ LOLOS: Akun Valid -> Restore
                                Toast.makeText(this, "Akun ditemukan! Memulihkan...", Toast.LENGTH_SHORT).show()
                                viewModel.logoutAndReset {
                                    val restoredUser = User(
                                        name = name,
                                        username = email,
                                        password = "google_auth",
                                        role = "admin"
                                    )
                                    viewModel.insertUser(restoredUser)

                                    val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
                                    prefs.edit().putBoolean("is_full_version", response.status == "PREMIUM").apply()

                                    processLoginSuccess(restoredUser)
                                }
                            }
                        } else {
                            showErrorDialog("Gagal Login", "Gagal menghubungi server.")
                            mGoogleSignInClient.signOut()
                        }
                    }
                }
            }
        }
    }

    private fun performGoogleRegistration(name: String, email: String) {
        val loading = android.app.ProgressDialog(this)
        loading.setMessage("Mendaftarkan akun Google...")
        loading.setCancelable(false)
        loading.show()

        viewModel.logoutAndReset {
            getSharedPreferences("app_license", Context.MODE_PRIVATE).edit().clear().apply()

            val newUser = User(
                name = name,
                username = email,
                password = "google_auth",
                role = "admin"
            )
            viewModel.insertUser(newUser)

            val leadRequest = LeadRequest(
                name = name, store_name = "Toko $name", store_address = "-", store_phone = "-", phone = "-", email = email
            )
            ApiClient.webClient.registerLead(leadRequest).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    loading.dismiss()
                    if (activeRegisterDialog?.isShowing == true) activeRegisterDialog?.dismiss()
                    Toast.makeText(this@LoginActivity, "Registrasi Cloud Berhasil!", Toast.LENGTH_SHORT).show()
                    
                    // 🔥 DUAL SYNC: REGISTER LOCAL JUGA 🔥
                    Thread {
                        try {
                            ApiClient.getLocalClient(this@LoginActivity).registerLocalUser(newUser).execute()
                            runOnUiThread { Toast.makeText(this@LoginActivity, "Sync Lokal OK!", Toast.LENGTH_SHORT).show() }
                        } catch (e: Exception) {
                            e.printStackTrace()
                             runOnUiThread {
                                android.app.AlertDialog.Builder(this@LoginActivity)
                                    .setTitle("⚠️ Sync Lokal Gagal")
                                    .setMessage("Registrasi Cloud SUKSES, tapi gagal konek ke Server Lokal.\n\nPastikan Aplikasi Server Go sudah jalan & IP Server diatur benar (Port 9000).")
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    }.start()

                    finalizeRegistration(newUser, activeRegisterDialog)
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    loading.dismiss()
                    Toast.makeText(this@LoginActivity, "Registrasi Offline.", Toast.LENGTH_LONG).show()
                    
                    // Register Lokal (Backup)
                    Thread {
                       try { ApiClient.getLocalClient(this@LoginActivity).registerLocalUser(newUser).execute() } catch (e: Exception) {}
                    }.start()

                    if (activeRegisterDialog?.isShowing == true) activeRegisterDialog?.dismiss()
                    finalizeRegistration(newUser, activeRegisterDialog)
                }
            })
        }
    }

    private fun processLoginSuccess(user: User) {
        if (user.role == "admin") {
            viewModel.checkServerLicense(user.username)
        }
        saveSession(user)
        if (user.role == "admin") {
            viewModel.sendDataToSalesSystem(user)
        }
        Toast.makeText(this, "Selamat Datang, ${user.name}!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun showRegisterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_user_entry, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etUser = view.findViewById<EditText>(R.id.etUsername)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etPass = view.findViewById<EditText>(R.id.etPassword)
        val spRole = view.findViewById<Spinner>(R.id.spRole)
        if (spRole != null) spRole.visibility = View.GONE

        val btnGoogleReg = view.findViewById<View>(R.id.btnGoogleRegister)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Daftar Akun Owner")
            .setView(view)
            .setPositiveButton("DAFTAR MANUAL", null)
            .setNegativeButton("BATAL", null)
            .create()

        activeRegisterDialog = dialog

        btnGoogleReg.setOnClickListener {
            isRegisterMode = true
            startGoogleSignIn()
        }

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val nama = etName.text.toString().trim()
                val email = etUser.text.toString().trim()
                val hp = etPhone.text.toString().trim()
                val pass = etPass.text.toString().trim()

                if (nama.isEmpty() || email.isEmpty() || hp.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this, "Lengkapi semua data!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val loading = android.app.ProgressDialog(this)
                loading.setMessage("Cek ketersediaan email...")
                loading.show()

                // 🔥 CEK DUPLIKAT DI SERVER SEBELUM DAFTAR MANUAL
                viewModel.checkUserOnCloud(email) { response ->
                    if (response != null && !response.message.lowercase().contains("tidak ditemukan")) {
                        loading.dismiss()
                        showErrorDialog("Gagal Daftar", "Email ini SUDAH TERDAFTAR di sistem pusat.\nSilakan Login saja.")
                    } else {
                        // Oke, email bersih, lanjut daftar
                        loading.setMessage("Menyimpan data...")
                        viewModel.logoutAndReset {
                            getSharedPreferences("app_license", Context.MODE_PRIVATE).edit().clear().apply()
                            
                            val newUser = User(name = nama, username = email, phone = hp, password = pass, role = "admin")
                            viewModel.insertUser(newUser)

                            // 1. DAFTAR KE CLOUD (Utama)
                            val req = LeadRequest(name = nama, store_name = "Toko $nama", store_address = "-", store_phone = "-", phone = hp, email = email)
                            
                            ApiClient.webClient.registerLead(req).enqueue(object : Callback<ResponseBody> {
                                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                                    loading.dismiss()
                                    if (activeRegisterDialog?.isShowing == true) activeRegisterDialog?.dismiss()
                                    Toast.makeText(this@LoginActivity, "Registrasi Cloud Berhasil!", Toast.LENGTH_SHORT).show()
                                    
                                    // 🔥 2. DUAL REGISTRATION: DAFTAR KE LOCAL GO SERVER JUGA 🔥
                                    Thread {
                                        try {
                                            ApiClient.getLocalClient(this@LoginActivity).registerLocalUser(newUser).execute()
                                            runOnUiThread { Toast.makeText(this@LoginActivity, "Sync Lokal OK!", Toast.LENGTH_SHORT).show() }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            runOnUiThread {
                                                android.app.AlertDialog.Builder(this@LoginActivity)
                                                    .setTitle("⚠️ Sync Lokal Gagal")
                                                    .setMessage("Registrasi Cloud SUKSES, tapi gagal konek ke Server Lokal.\n\nPastikan Aplikasi Server Go sudah jalan & IP Server diatur benar (Port 9000).")
                                                    .setPositiveButton("OK", null)
                                                    .show()
                                            }
                                        }
                                    }.start()

                                    finalizeRegistration(newUser, dialog)
                                }
                                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                                    // Jika Gagal Cloud, Tetap Simpan Lokal ?
                                    // Idealnya jangan, tapi kalau internet mati, user mau pake offline.
                                    // Kita izinkan offline, tapi Cloud-nya pending.
                                    loading.dismiss()
                                    Toast.makeText(this@LoginActivity, "Registrasi Offline (Cloud Gagal).", Toast.LENGTH_LONG).show()

                                    // Coba Daftar Lokal (Backup)
                                    Thread {
                                       try { ApiClient.getLocalClient(this@LoginActivity).registerLocalUser(newUser).execute() } catch (e: Exception) {}
                                    }.start()

                                    if (activeRegisterDialog?.isShowing == true) activeRegisterDialog?.dismiss()
                                    finalizeRegistration(newUser, dialog)
                                }
                            })
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun finalizeRegistration(user: User, dialog: AlertDialog?) {
        saveSession(user)
        viewModel.sendDataToSalesSystem(user)
        viewModel.checkServerLicense(user.username)
        dialog?.dismiss()
        val intent = Intent(this, StoreSettingsActivity::class.java)
        intent.putExtra("IS_INITIAL_SETUP", true)
        startActivity(intent)
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
        val namaLengkap = if (user.name.isNullOrEmpty()) user.username else user.name
        editor.putString("fullname", namaLengkap)

        // 🔥 SIMPAN DATA CABANG (Fixed untuk Kotlin var)
        val currentBranch = user.branch // Cek object direct (jika dari Retrofit)
        
        if (currentBranch != null) {
            editor.putString("branch_name", currentBranch.name)
            editor.putString("branch_address", currentBranch.address)
            editor.apply()
        } else {
            // Jika Object Null (misal dari Room), Cek ID-nya
            val bId = user.branchId ?: 0
            if (bId > 0) {
                 // Cari Nama Cabang di DB Lokal (Background Thread)
                 // Karena saveSession dipanggil di MainThread, kita pakai Thread sederhana
                 Thread {
                     try {
                         // Gunakan runBlocking atau GlobalScope karena ini Thread biasa
                         // ATAU ganti getBranchById jadi non-suspend jika perlu, tapi suspend lebih aman.
                         // Kita pakai runBlocking untuk simplisitas di dalam Thread ini
                         // Kita pakai runBlocking untuk simplisitas di dalam Thread ini
                         kotlinx.coroutines.runBlocking {
                             val dbBranch = com.sysdos.kasirpintar.data.AppDatabase.getDatabase(this@LoginActivity)
                                 .branchDao().getBranchById(bId).first()
                             if (dbBranch != null) {
                                val e = getSharedPreferences("session_kasir", Context.MODE_PRIVATE).edit()
                                e.putString("branch_name", dbBranch.name)
                                e.putString("branch_address", dbBranch.address)
                                e.apply()
                             }
                         }
                     } catch (e: Exception) { e.printStackTrace() }
                 }.start()
                 // Sambil nunggu thread, set sementara (bisa ketimpa nanti)
                 editor.putString("branch_name", "Memuat...") 
            } else {
                editor.putString("branch_name", "Pusat") // Default Pusat
                editor.putString("branch_address", "-")
            }
            editor.apply()
        }
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