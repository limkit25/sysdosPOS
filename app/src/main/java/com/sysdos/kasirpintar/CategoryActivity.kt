package com.sysdos.kasirpintar

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton // Import ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.model.Category
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class CategoryActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var etCategory: EditText
    private lateinit var btnAdd: Button
    private lateinit var lvCategory: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 1. BIND VIEW
        val btnBack = findViewById<ImageButton>(R.id.btnBack) // Tombol Header
        etCategory = findViewById(R.id.etNewCategory)
        btnAdd = findViewById(R.id.btnAddCategory)
        lvCategory = findViewById(R.id.lvCategory)

        // 2. FUNGSI TOMBOL BACK
        btnBack.setOnClickListener { finish() }

        // 3. OBSERVE DATA
        viewModel.allCategories.observe(this) { categories ->
            val safeCategories: List<Category> = categories ?: emptyList()

            // Tampilkan Nama Kategori di List
            val names = safeCategories.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
            lvCategory.adapter = adapter

            // Klik item untuk hapus
            lvCategory.setOnItemClickListener { _, _, position, _ ->
                val selectedCat = safeCategories[position]
                confirmDelete(selectedCat)
            }
        }

        // 4. TAMBAH KATEGORI
        btnAdd.setOnClickListener {
            val name = etCategory.text.toString().trim()
            if (name.isNotEmpty()) {
                viewModel.addCategory(etCategoryName.text.toString())
                etCategory.setText("")
                Toast.makeText(this, "Kategori ditambah!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nama kategori tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Kategori?")
            .setMessage("Hapus '${category.name}' dari daftar?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteCategory(category)
                Toast.makeText(this, "Kategori dihapus.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}