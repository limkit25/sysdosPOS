package com.sysdos.kasirpintar

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
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

        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val role = session.getString("role", "kasir")?.lowercase()

        if (role != "admin" && role != "manager") {
            android.widget.Toast.makeText(this, "Akses Ditolak! Khusus Admin/Manager.", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val rv = findViewById<RecyclerView>(R.id.rvShiftLogs)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ShiftAdapter()
        rv.adapter = adapter

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Perluas ViewModel untuk mengakses allShiftLogs jika belum ada
        // Pastikan di ProductViewModel sudah ada: val allShiftLogs = shiftDao.getAllLogs().asLiveData()

        viewModel.allShiftLogs.observe(this) { logs ->
            adapter.submitList(logs)
        }
    }
}