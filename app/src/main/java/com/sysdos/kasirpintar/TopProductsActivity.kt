package com.sysdos.kasirpintar

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.util.*

class TopProductsActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var rvProducts: RecyclerView
    private var fullTransactionList: List<Transaction> = emptyList()

    // Filter Buttons
    private lateinit var btnToday: Button
    private lateinit var btnWeek: Button
    private lateinit var btnMonth: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_products)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // Init Views
        rvProducts = findViewById(R.id.rvTopProducts)
        rvProducts.layoutManager = LinearLayoutManager(this)

        btnToday = findViewById(R.id.btnFilterToday)
        btnWeek = findViewById(R.id.btnFilterWeek)
        btnMonth = findViewById(R.id.btnFilterMonth)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Observe Data

        viewModel.allTransactions.observe(this) { transactions ->
            fullTransactionList = transactions
            // Default: Tampilkan Hari Ini
            filterData("TODAY")
            updateFilterUI(btnToday)
        }

        // Listener Filter
        btnToday.setOnClickListener { updateFilterUI(btnToday); filterData("TODAY") }
        btnWeek.setOnClickListener { updateFilterUI(btnWeek); filterData("WEEK") }
        btnMonth.setOnClickListener { updateFilterUI(btnMonth); filterData("MONTH") }
    }

    private fun filterData(range: String) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)

        val filteredList = when (range) {
            "TODAY" -> fullTransactionList.filter { it.timestamp >= calendar.timeInMillis }
            "WEEK" -> { calendar.add(Calendar.DAY_OF_YEAR, -6); fullTransactionList.filter { it.timestamp >= calendar.timeInMillis } }
            "MONTH" -> { calendar.add(Calendar.DAY_OF_YEAR, -29); fullTransactionList.filter { it.timestamp >= calendar.timeInMillis } }
            else -> fullTransactionList
        }

        calculateProductPerformance(filteredList)
    }

    data class ProductSales(val name: String, var qty: Int, var total: Double)

    private fun calculateProductPerformance(transactions: List<Transaction>) {
        val productMap = HashMap<String, ProductSales>()

        for (trx in transactions) {
            val items = trx.itemsSummary.split(";")
            for (itemStr in items) {
                val parts = itemStr.split("|")
                if (parts.size >= 4) {
                    val name = parts[0]
                    val qty = parts[1].toIntOrNull() ?: 0
                    val total = parts[3].toDoubleOrNull() ?: 0.0

                    if (productMap.containsKey(name)) {
                        val existing = productMap[name]!!
                        existing.qty += qty
                        existing.total += total
                    } else {
                        productMap[name] = ProductSales(name, qty, total)
                    }
                }
            }
        }

        val sortedList = productMap.values.sortedByDescending { it.qty }
        rvProducts.adapter = ProductReportAdapter(sortedList)
    }

    private fun updateFilterUI(activeBtn: Button) {
        val allBtns = listOf(btnToday, btnWeek, btnMonth)
        for (btn in allBtns) {
            btn.setBackgroundColor(Color.WHITE)
            btn.setTextColor(Color.GRAY)
        }
        activeBtn.setBackgroundColor(Color.parseColor("#E3F2FD")) // Biru Muda
        activeBtn.setTextColor(Color.parseColor("#1976D2")) // Biru Tua
    }

    // --- ADAPTER ---
    inner class ProductReportAdapter(private val list: List<ProductSales>) : RecyclerView.Adapter<ProductReportAdapter.Holder>() {
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvRank: TextView = v.findViewById(R.id.tvRank)
            val tvName: TextView = v.findViewById(R.id.tvProductName)
            val tvQty: TextView = v.findViewById(R.id.tvProductQty)
            val tvTotal: TextView = v.findViewById(R.id.tvProductTotal)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_report_product, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            holder.tvRank.text = (position + 1).toString()
            holder.tvName.text = item.name
            holder.tvQty.text = "Terjual: ${item.qty} pcs"
            holder.tvTotal.text = String.format(Locale("id", "ID"), "Rp %,d", item.total.toLong()).replace(',', '.')

            when(position) {
                0 -> holder.tvRank.setTextColor(Color.parseColor("#FFD700"))
                1 -> holder.tvRank.setTextColor(Color.parseColor("#C0C0C0"))
                2 -> holder.tvRank.setTextColor(Color.parseColor("#CD7F32"))
                else -> holder.tvRank.setTextColor(Color.parseColor("#1976D2"))
            }
        }

        override fun getItemCount(): Int = list.size
    }
}