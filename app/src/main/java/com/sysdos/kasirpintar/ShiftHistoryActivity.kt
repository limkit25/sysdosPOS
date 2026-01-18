package com.sysdos.kasirpintar

import android.content.Context
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.ShiftAdapter

class ShiftHistoryActivity : AppCompatActivity() {
    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: ShiftAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shift_history)

        // 🔥 1. FUNGSI TOMBOL BACK (Agar bisa kembali ke Dashboard)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Cek Role (Keamanan)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val role = session.getString("role", "kasir")?.lowercase()

        if (role != "admin" && role != "manager") {
            Toast.makeText(this, "Akses Ditolak! Khusus Admin/Manager.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val rv = findViewById<RecyclerView>(R.id.rvShiftLogs)
        adapter = ShiftAdapter()

        // 🔥 2. LOGIKA TAMPILAN RESPONSIVE (HP vs TABLET)
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        if (screenWidthDp >= 600) {
            // Kalau TABLET (Lebar > 600dp) -> Pakai Grid 2 Kolom biar rapi
            rv.layoutManager = GridLayoutManager(this, 2)
        } else {
            // Kalau HP BIASA -> Pakai List ke Bawah
            rv.layoutManager = LinearLayoutManager(this)
        }

        rv.adapter = adapter

        // Setup ViewModel
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]
        viewModel.allShiftLogs.observe(this) { logs ->
            adapter.submitList(logs)
        }
    }
}