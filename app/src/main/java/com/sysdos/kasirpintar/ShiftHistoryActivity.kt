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
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navView: com.google.android.material.navigation.NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shift_history)

        // === 1. SETUP MENU SAMPING (DRAWER) ===
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        val btnMenu = findViewById<View>(R.id.btnMenuDrawer)

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")

        if (navView.headerCount > 0) {
            val header = navView.getHeaderView(0)
            header.findViewById<TextView>(R.id.tvHeaderName).text = realName
            header.findViewById<TextView>(R.id.tvHeaderRole).text = "Role: ${role?.uppercase()}"
        }

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, DashboardActivity::class.java)); finish() }
                R.id.nav_kasir -> { startActivity(Intent(this, MainActivity::class.java)); finish() }
                R.id.nav_stok -> { startActivity(Intent(this, ProductListActivity::class.java)); finish() }
                R.id.nav_laporan -> { startActivity(Intent(this, SalesReportActivity::class.java)); finish() }
                R.id.nav_user -> { startActivity(Intent(this, UserListActivity::class.java)); finish() }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // === 2. SETUP RECYCLERVIEW (INI YANG TADI KURANG) ===
        val rvShiftLogs = findViewById<RecyclerView>(R.id.rvShiftLogs)
        rvShiftLogs.layoutManager = LinearLayoutManager(this)

        // Inisialisasi adapter (Pastikan class ShiftAdapter sudah ada)
        adapter = ShiftAdapter()
        rvShiftLogs.adapter = adapter

        // === 3. HUBUNGKAN KE VIEWMODEL (AMBIL DATA) ===
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Ambil data dari database melalui ViewModel
        viewModel.allShiftLogs.observe(this) { logs ->
            if (logs != null && logs.isNotEmpty()) {
                adapter.submitList(logs)
            } else {
                Toast.makeText(this, "Belum ada riwayat setoran", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}