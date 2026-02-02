package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.ShiftAdapter // Pastikan nama adapternya benar

class ShiftHistoryActivity : AppCompatActivity() {
    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: ShiftAdapter
    // Drawer Removed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shift_history)

        // === 1. SETUP MENU SAMPING (DRAWER) -> REMOVED ===

        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")

        // === 2. SETUP RECYCLERVIEW (INI YANG TADI KURANG) ===
        val rvShiftLogs = findViewById<RecyclerView>(R.id.rvShiftLogs)
        rvShiftLogs.layoutManager = LinearLayoutManager(this)

        // Inisialisasi adapter (Pastikan class ShiftAdapter sudah ada)
        adapter = ShiftAdapter()
        rvShiftLogs.adapter = adapter

        // === 3. HUBUNGKAN KE VIEWMODEL (AMBIL DATA) ===
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Ambil data dari database melalui ViewModel
        // Ambil data dari database melalui ViewModel (GLOBAL)
        viewModel.allShiftLogsGlobal.observe(this) { logs ->
            if (logs != null && logs.isNotEmpty()) {
                adapter.submitList(logs)
            } else {
                Toast.makeText(this, "Belum ada riwayat setoran", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}