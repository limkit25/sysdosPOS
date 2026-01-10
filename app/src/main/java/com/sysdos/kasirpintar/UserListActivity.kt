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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_list)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        val rvUsers = findViewById<RecyclerView>(R.id.rvUserList)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddUser)
        val tvHint = findViewById<TextView>(R.id.tvHint)

        tvHint.text = "Klik user untuk Edit. Tahan untuk Hapus."

        // SETUP ADAPTER
        adapter = UserAdapter(
            onClick = { user ->
                // EDIT USER
                showUserDialog(user)
            },
            onLongClick = { user ->
                // HAPUS USER
                if (user.id == 1 || user.role == "admin") {
                    // Proteksi sederhana: Admin tidak bisa dihapus sembarangan (opsional)
                    confirmDelete(user)
                } else {
                    confirmDelete(user)
                }
            }
        )

        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = adapter

        // Load data user real-time
        viewModel.allUsers.observe(this) { users ->
            adapter.submitList(users)
        }

        // TAMBAH USER BARU
        fabAdd.setOnClickListener {
            showUserDialog(null) // null artinya Mode Tambah
        }
    }

    private fun showUserDialog(userToEdit: User?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_user_entry, null)

        // Inisialisasi View (Spinner SUDAH DIHAPUS)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etUser = view.findViewById<EditText>(R.id.etUsername)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etPass = view.findViewById<EditText>(R.id.etPassword)

        // Isi data jika Mode Edit
        if (userToEdit != null) {
            etName.setText(userToEdit.name)
            etUser.setText(userToEdit.username)
            etPhone.setText(userToEdit.phone)
            etPass.setText(userToEdit.password)
        }

        val title = if (userToEdit == null) "Tambah Admin Baru" else "Edit User #${userToEdit.id}"

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Simpan", null) // null agar tidak auto-close
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val nama = etName.text.toString().trim()
                val username = etUser.text.toString().trim()
                val hp = etPhone.text.toString().trim()
                val password = etPass.text.toString().trim()

                // 🔥 PAKSA ROLE JADI ADMIN (HARDCODE)
                val fixedRole = "admin"

                // --- 🛡️ VALIDASI ---
                var isValid = true

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                    etUser.error = "Format email salah!"
                    isValid = false
                }

                if (hp.length < 10 || !hp.all { it.isDigit() }) {
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
                        // MODE TAMBAH (Otomatis Admin)
                        val newUser = User(
                            name = nama,
                            username = username,
                            phone = hp,
                            password = password,
                            role = fixedRole
                        )
                        viewModel.insertUser(newUser)
                        viewModel.syncUser(newUser)
                        Toast.makeText(this, "Admin berhasil dibuat!", Toast.LENGTH_SHORT).show()
                    } else {
                        // MODE EDIT (Tetap Admin / Update data lain)
                        val updatedUser = userToEdit.copy(
                            name = nama,
                            username = username,
                            phone = hp,
                            password = password,
                            role = fixedRole
                        )
                        viewModel.updateUser(updatedUser)
                        viewModel.syncUser(updatedUser)
                        Toast.makeText(this, "Data User diperbarui!", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(user: User) {
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