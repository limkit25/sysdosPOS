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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.utils.SessionManager
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

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
                Toast.makeText(this, "Google Sign In Gagal: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        val etUser = findViewById<TextInputEditText>(R.id.etUsername)
        val etPass = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogle = findViewById<MaterialCardView>(R.id.btnGoogleLogin)
//        val btnRegisterLink = findViewById<TextView>(R.id.btnRegisterLink)
        val btnGear = findViewById<ImageButton>(R.id.btnServerSetting)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)

        // Set Versi
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "Ver ${pInfo.versionName}"
        } catch (e: Exception) {}

        // 1. LOGIKA LOGIN MANUAL
        btnLogin.setOnClickListener {
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString().trim()
            if (u.isNotEmpty() && p.isNotEmpty()) {
                viewModel.login(u, p) { user ->
                    if (user != null) {
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

        // Enter di password langsung login
        etPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick()
                true
            } else false
        }

        // 2. LOGIKA LOGIN GOOGLE
        btnGoogle.setOnClickListener {
            startGoogleSignIn()
        }

        // 3. LOGIKA DAFTAR (REGISTER)
//        btnRegisterLink.setOnClickListener {
//            showRegisterDialog()
//        }

        // 4. SETTING SERVER
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

        // Cek apakah user google ini sudah ada di database lokal?
        // Karena ViewModel tidak punya fungsi getUserByName, kita ambil semua dulu (simplifikasi)
        viewModel.allUsers.observe(this) { users ->
            val existingUser = users.find { it.username == email }

            if (existingUser != null) {
                // User sudah ada -> Login
                saveSession(existingUser)
                gotoDashboard()
            } else {
                // User belum ada -> Registrasi Otomatis sebagai Cashier
                val newUser = User(
                    username = email,
                    password = "google_auth", // Password dummy
                    role = "cashier" // Default role
                )
                viewModel.insertUser(newUser)
                saveSession(newUser)
                Toast.makeText(this, "Selamat Datang, $name!", Toast.LENGTH_LONG).show()
                gotoDashboard()
            }
            // Hapus observer agar tidak terpanggil berulang kali
            viewModel.allUsers.removeObservers(this)
        }
    }

    // --- FUNGSI REGISTER MANUAL (DIALOG) ---
    private fun showRegisterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_user_entry, null)
        val etNewUser = view.findViewById<EditText>(R.id.etUsername)
        val etNewPass = view.findViewById<EditText>(R.id.etPassword)
        val spnRole = view.findViewById<Spinner>(R.id.spnRole)

        // Setup Spinner Role
        val roles = arrayOf("admin", "cashier")
        spnRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)

        AlertDialog.Builder(this)
            .setTitle("Daftar Akun Baru")
            .setView(view)
            .setPositiveButton("DAFTAR") { _, _ ->
                val u = etNewUser.text.toString().trim()
                val p = etNewPass.text.toString().trim()
                val r = roles[spnRole.selectedItemPosition]

                if (u.isNotEmpty() && p.isNotEmpty()) {
                    val newUser = User(username = u, password = p, role = r)
                    viewModel.insertUser(newUser)

                    // Auto Login setelah daftar
                    saveSession(newUser)
                    Toast.makeText(this, "Akun Berhasil Dibuat!", Toast.LENGTH_SHORT).show()

                    // Jika Admin baru, arahkan ke setting toko
                    if (r == "admin") {
                        val intent = Intent(this, StoreSettingsActivity::class.java)
                        intent.putExtra("IS_INITIAL_SETUP", true)
                        startActivity(intent)
                    } else {
                        gotoDashboard()
                    }
                    finish()
                } else {
                    Toast.makeText(this, "Data tidak boleh kosong", Toast.LENGTH_SHORT).show()
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
        editor.putBoolean("is_logged_in", true) // Penting buat Splash
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
            .setTitle("Setting IP Server")
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
}