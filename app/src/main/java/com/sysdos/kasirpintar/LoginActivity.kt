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
    
    // 🔥 STRICT LOGIN FLAG
    private var hasLocalUsers = false

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
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Cek Session (Kalau sudah login, langsung ke Dashboard)
        sessionManager = SessionManager(this)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        if (session.contains("username")) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        } else {
            // 🔥 SAFETY: Jika tidak login, pastikan sampah lisensi & toko dibersihkan
            getSharedPreferences("app_license", Context.MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("store_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        }

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Binding View
        val etUser = findViewById<TextInputEditText>(R.id.etUsername)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val layoutInputPhone = findViewById<LinearLayout>(R.id.layoutInputPhone)
        val layoutInputEmail = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutInputEmail)
        val btnToggleLoginMode = findViewById<TextView>(R.id.btnToggleLoginMode)

        val etPass = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogleLogin = findViewById<MaterialCardView>(R.id.btnGoogleLogin)
        val btnRegisterLink = findViewById<TextView>(R.id.btnRegisterLink)
        val btnGear = findViewById<ImageButton>(R.id.btnServerSetting)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)

        var isPhoneMode = true // Default Phone

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "Ver ${pInfo.versionName}"
        } catch (e: Exception) {}

        // 🔥 LOGIC Sembunyikan Tombol Daftar jika sudah ada User di DB Local
        viewModel.allUsers.observe(this) { users ->
            hasLocalUsers = !users.isNullOrEmpty()
            if (hasLocalUsers) {
                btnRegisterLink.visibility = View.GONE
            } else {
                btnRegisterLink.visibility = View.VISIBLE
            }
        }

        // --- TOGGLE LISTENER ---
        btnToggleLoginMode.setOnClickListener {
            isPhoneMode = !isPhoneMode
            if (isPhoneMode) {
                // Tampilkan Phone
                layoutInputPhone.visibility = View.VISIBLE
                layoutInputEmail.visibility = View.GONE
                btnToggleLoginMode.text = "Gunakan Email"
                etPhone.requestFocus()
            } else {
                // Tampilkan Email
                layoutInputPhone.visibility = View.GONE
                layoutInputEmail.visibility = View.VISIBLE
                btnToggleLoginMode.text = "Gunakan No. Handphone"
                etUser.requestFocus()
            }
        }

        // =================================================================
        // 🔒 1. LOGIKA LOGIN MANUAL (HP Atau EMAIL)
        // =================================================================
        btnLogin.setOnClickListener {
            val p = etPass.text.toString().trim()
            
            if (p.isEmpty()) {
                Toast.makeText(this, "Password/PIN harus diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isPhoneMode) {
                // --- LOGIN VIA HP ---
                val rawPhone = etPhone.text.toString().trim()
                if (rawPhone.isEmpty()) {
                    Toast.makeText(this, "No. Handphone harus diisi!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                viewModel.getUserByPhone(rawPhone) { user ->
                    if (user != null) {
                         verifyPasswordAndLogin(user, p)
                    } else {
                         // Coba dengan 0 di depan jika user tidak ketik 0
                         val altPhone = "0$rawPhone"
                         viewModel.getUserByPhone(altPhone) { user2 ->
                             if (user2 != null) {
                                  verifyPasswordAndLogin(user2, p)
                             } else {
                                  Toast.makeText(this, "No. HP tidak ditemukan di HP ini.", Toast.LENGTH_LONG).show()
                             }
                         }
                    }
                }

            } else {
                // --- LOGIN VIA EMAIL (LOGIC BARU: STRICT CHECK) ---
                val u = etUser.text.toString().trim()
                if (u.isEmpty()) {
                    Toast.makeText(this, "Email harus diisi!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // A. Cek di Database HP dulu
                viewModel.getUserByEmail(u) { existingUser ->
                    if (existingUser != null) {
                        verifyPasswordAndLogin(existingUser, p)
                    } else {
                        // B. STRICT CHECK: Jika HP ini sudah ada isinya (Toko Aktif), 
                        // JANGAN izinkan email asing check ke server.
                        if (hasLocalUsers) {
                             Toast.makeText(this, "⛔ Akses Ditolak. Email ini tidak terdaftar di perangkat ini. Hubungi Admin/Owner.", Toast.LENGTH_LONG).show()
                        } else {
                             // C. Jika HP Kosong (Baru Install/Reset), Izinkan Restore dari Server
                             checkUserOnCloudOrLocalServer(u, p)
                        }
                    }
                }
            }
        }
        
        btnGoogleLogin.setOnClickListener {
            isRegisterMode = false // Mode LOGIN
            startGoogleSignIn()
        }

        btnRegisterLink.setOnClickListener { 
            val intent = Intent(this, SubscriptionPlanActivity::class.java)
            startActivity(intent)
        }
        btnGear.setOnClickListener { showServerDialog() }

        etPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick()
                true
            } else false
        }
    }

    private fun verifyPasswordAndLogin(user: User, inputPass: String) {
        val storedPassword = user.password ?: ""
        val isMatch = if (storedPassword.startsWith("$2a$")) {
            try {
                org.mindrot.jbcrypt.BCrypt.checkpw(inputPass, storedPassword)
            } catch (e: Exception) { false }
        } else {
            storedPassword == inputPass
        }

        if (isMatch) {
            val branchId = user.branchId ?: 0
            viewModel.syncStoreConfigFromLocal(branchId) {
                processLoginSuccess(user)
            }
        } else {
            Toast.makeText(this, "❌ Password / PIN Salah!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkUserOnCloudOrLocalServer(u: String, p: String) {
        val loading = android.app.ProgressDialog(this)
        loading.setMessage("Mencari akun di server...")
        loading.show()

        viewModel.checkUserOnCloud(u) { response ->
            loading.dismiss()

            if (response != null && !response.message.lowercase().contains("tidak ditemukan")) {
                if (response.status == "BLOCKED") {
                    showErrorDialog("Akses Ditolak", "Akun terkunci di HP lain:\n${response.message}")
                } else {
                    val restoredUser = User(
                        name = "Owner Toko",
                        username = u,
                        password = p, 
                        role = "admin"
                    )
                    viewModel.insertUser(restoredUser)
                    val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_full_version", response.status == "PREMIUM").apply()

                    Toast.makeText(this, "Akun Email Dipulihkan!", Toast.LENGTH_LONG).show()
                    processLoginSuccess(restoredUser)
                }
            } else {
                loading.show()
                loading.setMessage("Mencari di Server Lokal...")
                
                viewModel.checkUserOnLocalServer(u) { localUser ->
                    loading.dismiss()
                    if (localUser != null) {
                        val role = localUser.role
                        val restoredUser = User(
                            name = localUser.name ?: "Kasir",
                            username = u,
                            password = p,
                            role = role,
                            branchId = localUser.branchId 
                        )
                        viewModel.insertUser(restoredUser)
                        
                        val branchId = localUser.branchId ?: 0
                        viewModel.syncStoreConfigFromLocal(branchId) {
                            Toast.makeText(this, "Login User Lokal Berhasil!", Toast.LENGTH_SHORT).show()
                            processLoginSuccess(restoredUser)
                        }
                    } else {
                        showErrorDialog("Gagal Masuk", "Email $u tidak ditemukan di Pusat maupun Server Lokal.")
                    }
                }
            }
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
                    // 🔥 STRICT CHECK GOOGLE LOGIN
                    if (hasLocalUsers) {
                         Toast.makeText(this, "⛔ Akses Ditolak. Email Google ini tidak terdaftar di perangkat ini.", Toast.LENGTH_LONG).show()
                         mGoogleSignInClient.signOut()
                         return@checkGoogleUser
                    }

                    val loading = android.app.ProgressDialog(this)
                    loading.setMessage("Mencari data akun Anda...")
                    loading.show()

                    viewModel.checkUserOnCloud(email) { response ->
                        loading.dismiss()

                        if (response != null && !response.message.lowercase().contains("tidak ditemukan")) {
                            if (response.status == "BLOCKED") {
                                showErrorDialog("Akses Ditolak!", "Akun terkunci:\n${response.message}")
                                mGoogleSignInClient.signOut()
                            } else {
                                proceedLogin(name, email, "google_auth", response.status)
                            }
                        } 
                        else {
                            loading.show()
                            loading.setMessage("Cek di Server Lokal...")
                            
                            viewModel.checkUserOnLocalServer(email) { localUser ->
                                loading.dismiss()
                                if (localUser != null) {
                                    proceedLogin(localUser.name ?: name, email, "google_auth", "LOCAL_ADMIN", localUser.role)
                                } else {
                                    showErrorDialog("Akses Ditolak", "Email $email belum terdaftar di Pusat maupun Server Lokal.")
                                    mGoogleSignInClient.signOut()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun proceedLogin(name: String, email: String, pass: String, status: String, role: String = "admin") {
        Toast.makeText(this, "Akun ditemukan! Memulihkan...", Toast.LENGTH_SHORT).show()
        viewModel.logoutAndReset {
            val restoredUser = User(
                name = name,
                username = email,
                password = pass,
                role = role
            )
            viewModel.insertUser(restoredUser)

            val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_full_version", status == "PREMIUM").apply()

            processLoginSuccess(restoredUser)
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
                    Toast.makeText(this@LoginActivity, "Registrasi Berhasil!", Toast.LENGTH_LONG).show()
                    finalizeRegistration(newUser, activeRegisterDialog)
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    loading.dismiss()
                    if (activeRegisterDialog?.isShowing == true) activeRegisterDialog?.dismiss()
                    Toast.makeText(this@LoginActivity, "Registrasi Offline.", Toast.LENGTH_LONG).show()
                    finalizeRegistration(newUser, activeRegisterDialog)
                }
            })
        }
    }

    private fun processLoginSuccess(user: User) {
        // 🔥 SET FLAG BAHWA ADA AKUN DI HP INI
        getSharedPreferences("app_config", Context.MODE_PRIVATE)
            .edit().putBoolean("has_local_account", true).apply()

        if (user.role == "admin") {
            viewModel.checkServerLicense(user.username)
        }
        saveSession(user)
        if (user.role == "admin") {
            viewModel.sendDataToSalesSystem(user)
            viewModel.syncUser(user)
        }
        Toast.makeText(this, "Selamat Datang, ${user.name}!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
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

        user.branch?.let { branch ->
            editor.putString("branch_name", branch.name)
            editor.putString("branch_address", branch.address)
        } ?: run {
            editor.putString("branch_name", "Pusat") 
            editor.putString("branch_address", "-")
        }

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