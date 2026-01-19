package com.sysdos.kasirpintar

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.UserAdapter

class UserListActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: UserAdapter
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navView: com.google.android.material.navigation.NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_list)

        // === SETUP MENU SAMPING ===
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        val btnMenu = findViewById<android.view.View>(R.id.btnMenuDrawer) // ID Baru

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        // Setup Header Menu
        val session = getSharedPreferences("session_kasir", android.content.Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")

        if (navView.headerCount > 0) {
            val header = navView.getHeaderView(0)
            header.findViewById<android.widget.TextView>(R.id.tvHeaderName).text = realName
            header.findViewById<android.widget.TextView>(R.id.tvHeaderRole).text = "Role: ${role?.uppercase()}"
        }

        // Navigasi
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(android.content.Intent(this, DashboardActivity::class.java)); finish() }
                R.id.nav_kasir -> { startActivity(android.content.Intent(this, MainActivity::class.java)); finish() }
                R.id.nav_stok -> { startActivity(android.content.Intent(this, ProductListActivity::class.java)); finish() }
                R.id.nav_laporan -> { startActivity(android.content.Intent(this, SalesReportActivity::class.java)); finish() }
                R.id.nav_user -> drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START) // Tutup aja
                // ... menu lain
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        // === KODINGAN LAMA ===
        // HAPUS BARIS INI: val btnBack = findViewById<android.view.View>(R.id.btnBack)...

        viewModel = androidx.lifecycle.ViewModelProvider(this)[ProductViewModel::class.java]
        val rvUsers = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvUserList)
        val fabAdd = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddUser)
        val tvHint = findViewById<android.widget.TextView>(R.id.tvHint)

        // ... Sisa logika Adapter, ViewModel, dan FAB tetap sama ...
        tvHint.text = "Klik user untuk Edit. Tahan untuk Hapus."

        adapter = UserAdapter(
            onClick = { user -> showUserDialog(user) },
            onLongClick = { user ->
                if (user.id == 1 || user.role == "admin") confirmDelete(user) else confirmDelete(user)
            }
        )
        rvUsers.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvUsers.adapter = adapter

        viewModel.allUsers.observe(this) { users -> adapter.submitList(users) }

        fabAdd.setOnClickListener { showUserDialog(null) }
    }

    // Tambahkan ini di luar onCreate
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun showUserDialog(userToEdit: User?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_user_entry, null)

        // 1. Inisialisasi View
        val etName = view.findViewById<EditText>(R.id.etName)
        val etUser = view.findViewById<EditText>(R.id.etUsername)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etPass = view.findViewById<EditText>(R.id.etPassword)
        val spRole = view.findViewById<Spinner>(R.id.spRole) // <--- INI BARU

        // Sembunyikan Tombol Google
        val btnGoogle = view.findViewById<android.view.View>(R.id.btnGoogleRegister)
        if (btnGoogle != null) btnGoogle.visibility = android.view.View.GONE

        // 2. Setup Pilihan Role (Spinner)
        val roleOptions = arrayOf("admin", "manager", "kasir")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleOptions)
        spRole.adapter = adapter

        // 3. Isi Data Jika Mode Edit
        if (userToEdit != null) {
            etName.setText(userToEdit.name)
            etUser.setText(userToEdit.username)
            etPhone.setText(userToEdit.phone)
            etPass.setText(userToEdit.password)

            // Set Pilihan Spinner sesuai Role user yang diedit
            val roleIndex = roleOptions.indexOf(userToEdit.role)
            if (roleIndex >= 0) {
                spRole.setSelection(roleIndex)
            }
        }

        val title = if (userToEdit == null) "Tambah Pegawai Baru" else "Edit User #${userToEdit.id}"

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Simpan", null) // Nanti di-override biar ga auto-close
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val nama = etName.text.toString().trim()
                val username = etUser.text.toString().trim()
                val hp = etPhone.text.toString().trim()
                val password = etPass.text.toString().trim()

                // 🔥 AMBIL ROLE DARI PILIHAN SPINNER
                val selectedRole = spRole.selectedItem.toString()

                // --- 🛡️ VALIDASI ---
                var isValid = true

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                    etUser.error = "Format email salah!"
                    isValid = false
                }
                if (hp.length < 10) {
                    etPhone.error = "No HP min 10 angka"
                    isValid = false
                }
                if (nama.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Data tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                    isValid = false
                }

                // --- EKSEKUSI ---
                if (isValid) {
                    if (userToEdit == null) {
                        // MODE TAMBAH
                        val newUser = User(
                            name = nama,
                            username = username,
                            phone = hp,
                            password = password,
                            role = selectedRole // <--- PAKAI YANG DIPILIH
                        )
                        viewModel.insertUser(newUser)
                        viewModel.syncUser(newUser) // Kirim ke VPS (opsional)
                        Toast.makeText(this, "Pegawai ($selectedRole) berhasil dibuat!", Toast.LENGTH_SHORT).show()
                    } else {
                        // MODE EDIT
                        val updatedUser = userToEdit.copy(
                            name = nama,
                            username = username,
                            phone = hp,
                            password = password,
                            role = selectedRole // <--- PAKAI YANG DIPILIH
                        )
                        viewModel.updateUser(updatedUser)
                        viewModel.syncUser(updatedUser)
                        Toast.makeText(this, "Data diperbarui!", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(user: User) {
        // 🔥 1. AMBIL USERNAME YANG SEDANG LOGIN
        val session = getSharedPreferences("session_kasir", android.content.Context.MODE_PRIVATE)
        val myUsername = session.getString("username", "")

        // 🔥 2. CEK: APAKAH INI AKUN SAYA SENDIRI?
        if (user.username == myUsername) {
            // ⛔ JIKA YA, TOLAK!
            Toast.makeText(this, "⛔ Eits! Tidak bisa menghapus akun sendiri (Sedang Login)!", Toast.LENGTH_LONG).show()
            return // Stop, jangan lanjut ke dialog hapus
        }

        // 🔥 3. (OPSIONAL) CEK APAKAH USER ID = 1 (OWNER PERTAMA)
        // Biar ID 1 gak bisa dihapus sama sekali walau oleh admin lain (Proteksi Ganda)
        if (user.id == 1) {
            Toast.makeText(this, "⛔ Akun Utama (Owner) tidak bisa dihapus!", Toast.LENGTH_LONG).show()
            return
        }

        // KALAU LOLOS CEK DI ATAS, BARU TAMPILKAN DIALOG
        AlertDialog.Builder(this)
            .setTitle("Hapus User?")
            .setMessage("Yakin hapus user: ${user.name} (${user.username})?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteUser(user)
                Toast.makeText(this, "User dihapus!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}