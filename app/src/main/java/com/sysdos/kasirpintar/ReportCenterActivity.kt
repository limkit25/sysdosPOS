package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.appcompat.widget.Toolbar

class ReportCenterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_center)

        // Setup Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Setup Cards
        findViewById<CardView>(R.id.cardSales).setOnClickListener {
            startActivity(Intent(this, SalesReportActivity::class.java))
        }

        findViewById<CardView>(R.id.cardLogs).setOnClickListener {
            startActivity(Intent(this, LogReportActivity::class.java))
        }

        findViewById<CardView>(R.id.cardTopProduct).setOnClickListener {
            startActivity(Intent(this, TopProductsActivity::class.java))
        }

        findViewById<CardView>(R.id.cardStockAsset).setOnClickListener {
            startActivity(Intent(this, ProductListActivity::class.java).apply {
                putExtra("OPEN_TAB_INDEX", 1)
            })
        }

        findViewById<CardView>(R.id.cardShiftHistory).setOnClickListener {
            startActivity(Intent(this, ShiftHistoryActivity::class.java))
        }
    }
}
