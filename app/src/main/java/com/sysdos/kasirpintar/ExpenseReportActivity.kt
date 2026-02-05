package com.sysdos.kasirpintar

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExpenseReportActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    private lateinit var btnExport: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var historyContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_report)

        viewModel = ViewModelProvider(this).get(ProductViewModel::class.java)

        btnExport = findViewById(R.id.btnExportExpense)
        btnBack = findViewById(R.id.btnBack)
        historyContainer = findViewById(R.id.llExpenseHistoryContainer)

        setupListeners()
        loadRecentHistory()
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnExport.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun loadRecentHistory() {
        val end = System.currentTimeMillis()
        val start = end - (7L * 24 * 3600 * 1000)

        viewModel.getExpensesByDate(start, end) { list ->
            historyContainer.removeAllViews()

            if (list.isEmpty()) {
                val tv = TextView(this)
                tv.text = "Belum ada pengeluaran dalam 7 hari terakhir."
                tv.setPadding(16, 16, 16, 16)
                historyContainer.addView(tv)
            } else {
                list.forEach { exp ->
                    val view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, historyContainer, false)
                    val text1 = view.findViewById<TextView>(android.R.id.text1)
                    val text2 = view.findViewById<TextView>(android.R.id.text2)

                    text1.text = "${exp.category} - Rp ${String.format("%,.0f", exp.amount)}"
                    text2.text = "${dateFormatter.format(Date(exp.timestamp))} | ${exp.note}"
                    
                    historyContainer.addView(view)
                }
            }
        }
    }

    private fun showDateRangePicker() {
        // Step 1: Pick Start Date
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val startCal = Calendar.getInstance()
            startCal.set(year, month, dayOfMonth, 0, 0, 0)
            val startTime = startCal.timeInMillis

            // Step 2: Pick End Date
            pickEndDate(startTime)

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            setTitle("Pilih Tanggal Awal")
            show()
        }
    }

    private fun pickEndDate(startTime: Long) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val endCal = Calendar.getInstance()
            endCal.set(year, month, dayOfMonth, 23, 59, 59)
            val endTime = endCal.timeInMillis

            if (endTime < startTime) {
                Toast.makeText(this, "Tanggal Akhir tidak boleh kurang dari Tanggal Awal", Toast.LENGTH_SHORT).show()
                return@DatePickerDialog
            }

            exportReport(startTime, endTime)

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            setTitle("Pilih Tanggal Akhir")
            show()
        }
    }

    private fun exportReport(start: Long, end: Long) {
        Toast.makeText(this, "Mengunduh Laporan...", Toast.LENGTH_SHORT).show()
        viewModel.exportExpensesToCsv(start, end) { file, errorMsg ->
            if (file != null) {
                shareFile(file)
            } else {
                Toast.makeText(this, errorMsg ?: "Gagal membuat laporan", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND)
            // Use text/plain or */* to ensure WhatsApp and others detect it as a file/doc
            intent.type = "text/plain" 
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            Toast.makeText(this, "Membuka opsi bagikan...", Toast.LENGTH_SHORT).show()
            startActivity(Intent.createChooser(intent, "Bagikan Laporan Pengeluaran"))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
