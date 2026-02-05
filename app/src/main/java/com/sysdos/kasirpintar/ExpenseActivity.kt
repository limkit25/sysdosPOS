package com.sysdos.kasirpintar

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class ExpenseActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    // Views
    private lateinit var spCategory: Spinner
    private lateinit var etAmount: EditText
    private lateinit var etNote: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense)

        viewModel = ViewModelProvider(this).get(ProductViewModel::class.java)

        spCategory = findViewById(R.id.spExpenseCategory)
        etAmount = findViewById(R.id.etExpenseAmount)
        etNote = findViewById(R.id.etExpenseNote)
        btnSave = findViewById(R.id.btnSaveExpense)
        btnBack = findViewById(R.id.btnBack)

        setupSpinner()
        setupListeners()
    }

    private fun setupSpinner() {
        val categories = arrayOf("Iuran Sampah", "Keamanan", "Listrik/Air", "Bahan Baku", "Gaji Karyawan", "Operasional", "Lainnya")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCategory.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        // Save Button
        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString()
            val note = etNote.text.toString()
            val category = spCategory.selectedItem.toString()

            if (amountStr.isEmpty()) {
                etAmount.error = "Wajib diisi"
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull() ?: 0.0
            if (amount <= 0) {
                etAmount.error = "Minimal > 0"
                return@setOnClickListener
            }

            viewModel.insertExpense(amount, category, note) {
                Toast.makeText(this, "Pengeluaran Disimpan!", Toast.LENGTH_SHORT).show()
                etAmount.setText("")
                etNote.setText("")
            }
        }
    }
}
