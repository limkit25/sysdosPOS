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
                if (user.id == 1) { // Asumsi ID 1 adalah Super Admin awal
                    Toast.makeText(this, "Admin Utama tidak bisa dihapus!", Toast.LENGTH_SHORT).show()
                } else {
                    confirmDelete(user)
                }
            }
        )

        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = adapter

        viewModel.allUsers.observe(this) { users ->
            adapter.submitList(users)
        }

        // TAMBAH USER BARU
        fabAdd.setOnClickListener {
            showUserDialog(null) // null artinya Mode Tambah
        }
    }

    // Fungsi Dialog yang bisa untuk Edit maupun Tambah
    private fun showUserDialog(userToEdit: User?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_user_entry, null)
        val etUser = view.findViewById<EditText>(R.id.etDialogUser)
        val etPass = view.findViewById<EditText>(R.id.etDialogPass)
        val spnRole = view.findViewById<Spinner>(R.id.spnRole)

        // 1. Setup Spinner Role (Admin / Kasir)
        val roles = arrayOf("kasir", "manager", "admin")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnRole.adapter = adapterSpinner

        // 2. Jika Mode Edit, isi form dengan data lama
        if (userToEdit != null) {
            etUser.setText(userToEdit.username)
            etPass.setText(userToEdit.password)

            // Set posisi spinner sesuai role yang ada di database
            val rolePosition = roles.indexOf(userToEdit.role)
            if (rolePosition >= 0) {
                spnRole.setSelection(rolePosition)
            }
        }

        val title = if (userToEdit == null) "Tambah User Baru" else "Edit User #${userToEdit.id}"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Simpan") { _, _ ->
                val username = etUser.text.toString()
                val password = etPass.text.toString()
                val selectedRole = roles[spnRole.selectedItemPosition] // Ambil role yang dipilih

                if (username.isNotEmpty() && password.isNotEmpty()) {
                    if (userToEdit == null) {
                        // --- MODE TAMBAH ---
                        val newUser = User(
                            username = username,
                            password = password,
                            role = selectedRole
                        )
                        viewModel.insertUser(newUser)
                        Toast.makeText(this, "User berhasil dibuat!", Toast.LENGTH_SHORT).show()
                    } else {
                        // --- MODE EDIT ---
                        // Copy data lama, tapi ganti yang diedit
                        val updatedUser = userToEdit.copy(
                            username = username,
                            password = password,
                            role = selectedRole
                        )
                        viewModel.updateUser(updatedUser)
                        Toast.makeText(this, "Data User diperbarui!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Username & Password wajib diisi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDelete(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Hapus User?")
            .setMessage("Yakin hapus kasir: ${user.username}?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteUser(user)
                Toast.makeText(this, "User dihapus!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}