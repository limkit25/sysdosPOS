package com.sysdos.kasirpintar

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class SubscriptionPlanActivity : AppCompatActivity() {

    private lateinit var cardFree: MaterialCardView
    private lateinit var cardMonthly: MaterialCardView
    private lateinit var cardYearly: MaterialCardView
    
    private lateinit var rbFree: RadioButton
    private lateinit var rbMonthly: RadioButton
    private lateinit var rbYearly: RadioButton
    
    private lateinit var btnContinue: Button

    private var selectedPlan = ""
    private var selectedPrice = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription_plan)

        cardFree = findViewById(R.id.cardFree)
        cardMonthly = findViewById(R.id.cardMonthly)
        cardYearly = findViewById(R.id.cardYearly)

        rbFree = findViewById(R.id.rbFree)
        rbMonthly = findViewById(R.id.rbMonthly)
        rbYearly = findViewById(R.id.rbYearly)

        btnContinue = findViewById(R.id.btnContinue)
        val btnBack = findViewById<TextView>(R.id.btnBack)

        // Listeners for Cards (act like RadioGroup)
        cardFree.setOnClickListener { selectPlan(0) }
        cardMonthly.setOnClickListener { selectPlan(1) }
        cardYearly.setOnClickListener { selectPlan(2) }

        // Forward clicks on RadioButtons to Cards
        rbFree.setOnClickListener { selectPlan(0) }
        rbMonthly.setOnClickListener { selectPlan(1) }
        rbYearly.setOnClickListener { selectPlan(2) }

        btnContinue.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            intent.putExtra("SUBSCRIPTION_PLAN", selectedPlan)
            intent.putExtra("PLAN_PRICE", selectedPrice)
            startActivity(intent)
            // Optional: dont finish() if we want user to be able to back here from register
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun selectPlan(index: Int) {
        // Reset All
        resetCard(cardFree, rbFree)
        resetCard(cardMonthly, rbMonthly)
        resetCard(cardYearly, rbYearly)

        // Highlight Selected
        when (index) {
            0 -> {
                highlightCard(cardFree, rbFree)
                selectedPlan = "FREE_TRIAL"
                selectedPrice = "Rp 0"
            }
            1 -> {
                highlightCard(cardMonthly, rbMonthly)
                selectedPlan = "PREMIUM 1 BULAN"
                selectedPrice = "Rp 50.000"
            }
            2 -> {
                highlightCard(cardYearly, rbYearly)
                selectedPlan = "PREMIUM 1 TAHUN"
                selectedPrice = "Rp 450.000"
            }
        }

        btnContinue.isEnabled = true
        btnContinue.background.setTint(Color.parseColor("#1976D2"))
    }

    private fun resetCard(card: MaterialCardView, rb: RadioButton) {
        card.strokeColor = Color.parseColor("#DDDDDD")
        card.strokeWidth = 2
        card.setCardBackgroundColor(Color.WHITE)
        rb.isChecked = false
    }

    private fun highlightCard(card: MaterialCardView, rb: RadioButton) {
        card.strokeColor = Color.parseColor("#1976D2")
        card.strokeWidth = 6
        card.setCardBackgroundColor(Color.parseColor("#E3F2FD")) // Light Blue Hint
        rb.isChecked = true
    }
}
