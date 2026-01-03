package com.sysdos.kasirpintar

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sysdos.kasirpintar.data.model.Supplier
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class SupplierActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_supplier)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val listView = findViewById<ListView>(R.id.lvSupplier)
        val fab = findViewById<FloatingActionButton>(R.id.fabAddSupplier)

        btnBack.setOnClickListener { finish() }

        // TOMBOL TAMBAH (Mode Baru)
        fab.setOnClickListener { showSupplierDialog(null) }

        // OBSERVE DATA
        viewModel.allSuppliers.observe(this) { suppliers ->
            // TAMPILKAN NAMA, NO HP, DAN ALAMAT
            val displayList = suppliers.map {
                "🏢 ${it.name}\n📞 ${it.phone}\n📍 ${it.address}" // <--- ALAMAT DITAMPILKAN
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
            listView.adapter = adapter

            // KLIK ITEM -> PILIH EDIT ATAU HAPUS
            listView.setOnItemClickListener { _, _, position, _ ->
                val selectedSupplier = suppliers[position]
                showOptionsDialog(selectedSupplier)
            }
        }
    }

    // Dialog Pilihan (Edit / Hapus)
    private fun showOptionsDialog(supplier: Supplier) {
        val options = arrayOf("✏️ Edit Data", "🗑️ Hapus Supplier")

        AlertDialog.Builder(this)
            .setTitle(supplier.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSupplierDialog(supplier) // Buka Dialog Edit
                    1 -> confirmDelete(supplier)      // Konfirmasi Hapus
                }
            }
            .show()
    }

    // Dialog Input (Bisa untuk BARU atau EDIT)
    private fun showSupplierDialog(supplierToEdit: Supplier?) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val etName = EditText(this).apply { hint = "Nama Supplier (PT/Toko)" }
        val etPhone = EditText(this).apply { hint = "Nomor HP / WA" }
        val etAddress = EditText(this).apply { hint = "Alamat Lengkap" }

        // Kalau Mode Edit -> Isi kolom dengan data lama
        if (supplierToEdit != null) {
            etName.setText(supplierToEdit.name)
            etPhone.setText(supplierToEdit.phone)
            etAddress.setText(supplierToEdit.address)
        }

        layout.addView(etName)
        layout.addView(etPhone)
        layout.addView(etAddress)

        val title = if (supplierToEdit == null) "Tambah Supplier" else "Edit Supplier"
        val btnText = if (supplierToEdit == null) "SIMPAN" else "UPDATE"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(btnText) { _, _ ->
                val name = etName.text.toString()
                val phone = etPhone.text.toString()
                val address = etAddress.text.toString()

                if (name.isNotEmpty()) {
                    if (supplierToEdit == null) {
                        // MODE SIMPAN BARU
                        val newSupplier = Supplier(name = name, phone = phone, address = address)
                        viewModel.insertSupplier(newSupplier)
                        Toast.makeText(this, "Supplier Disimpan!", Toast.LENGTH_SHORT).show()
                    } else {
                        // MODE UPDATE
                        val updatedSupplier = supplierToEdit.copy(
                            name = name,
                            phone = phone,
                            address = address
                        )
                        viewModel.updateSupplier(updatedSupplier)
                        Toast.makeText(this, "Data Diupdate!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Nama wajib diisi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDelete(supplier: Supplier) {
        AlertDialog.Builder(this)
            .setTitle("Hapus ${supplier.name}?")
            .setMessage("Data tidak bisa dikembalikan.")
            .setPositiveButton("HAPUS") { _, _ ->
                viewModel.deleteSupplier(supplier)
                Toast.makeText(this, "Dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}