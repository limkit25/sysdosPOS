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
    // ❌ HAPUS btnBack DARI SINI
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navView: com.google.android.material.navigation.NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_report)

        // === 1. SETUP MENU SAMPING ===
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        val btnMenu = findViewById<android.view.View>(R.id.btnMenuDrawer)

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        // Setup Header & Navigasi
        val session = getSharedPreferences("session_kasir", android.content.Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")
        if (navView.headerCount > 0) {
            val header = navView.getHeaderView(0)
            header.findViewById<android.widget.TextView>(R.id.tvHeaderName).text = realName
            header.findViewById<android.widget.TextView>(R.id.tvHeaderRole).text = "Role: ${role?.uppercase()}"
        }

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(android.content.Intent(this, DashboardActivity::class.java)); finish() }
                R.id.nav_kasir -> { startActivity(android.content.Intent(this, MainActivity::class.java)); finish() }
                R.id.nav_laporan -> { startActivity(android.content.Intent(this, SalesReportActivity::class.java)); finish() }
                R.id.nav_stok -> { startActivity(android.content.Intent(this, ProductListActivity::class.java)); finish() }
                R.id.nav_user -> { startActivity(android.content.Intent(this, UserListActivity::class.java)); finish() }
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        // INIT VIEW
        btnVoid = findViewById(R.id.btnTabVoid)
        btnRetur = findViewById(R.id.btnTabRetur)
        rv = findViewById(R.id.rvLogReport)

        // INIT DATA
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]
        adapter = LogReportAdapter()

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // DEFAULT LOAD: VOID
        loadData("VOID")

        // LISTENER
        btnVoid.setOnClickListener { loadData("VOID") }
        btnRetur.setOnClickListener { loadData("OUT") }

        // ❌ HAPUS BARIS INI: btnBack.setOnClickListener { finish() }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun loadData(type: String) {
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

        viewModel.getLogReport(type).observe(this) { logs ->
            adapter.submitList(logs)
        }
    }
}