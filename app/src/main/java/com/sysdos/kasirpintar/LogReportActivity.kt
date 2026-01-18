package com.sysdos.kasirpintar

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.viewmodel.LogReportAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class LogReportActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: LogReportAdapter
    private lateinit var btnVoid: Button
    private lateinit var btnRetur: Button
    private lateinit var rv: RecyclerView
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_report) // 🔥 HUBUNGKAN XML

        // INIT VIEW
        btnVoid = findViewById(R.id.btnTabVoid)
        btnRetur = findViewById(R.id.btnTabRetur)
        rv = findViewById(R.id.rvLogReport)
        btnBack = findViewById(R.id.btnBackLog)

        // INIT DATA
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]
        adapter = LogReportAdapter()

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // DEFAULT LOAD: VOID
        loadData("VOID")

        // LISTENER
        btnVoid.setOnClickListener { loadData("VOID") }
        btnRetur.setOnClickListener { loadData("OUT") } // Di DB tipe retur = "OUT"
        btnBack.setOnClickListener { finish() }
    }

    private fun loadData(type: String) {
        // Update Tampilan Tombol
        if (type == "VOID") {
            btnVoid.alpha = 1.0f
            btnRetur.alpha = 0.5f
            btnVoid.text = "RIWAYAT VOID (AKTIF)"
            btnRetur.text = "RIWAYAT RETUR"
        } else {
            btnRetur.alpha = 1.0f
            btnVoid.alpha = 0.5f
            btnRetur.text = "RIWAYAT RETUR (AKTIF)"
            btnVoid.text = "RIWAYAT VOID"
        }

        // Ambil Data
        viewModel.getLogReport(type).observe(this) { logs ->
            adapter.submitList(logs)
        }
    }
}