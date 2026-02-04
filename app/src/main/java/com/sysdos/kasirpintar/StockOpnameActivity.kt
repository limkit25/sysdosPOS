package com.sysdos.kasirpintar

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.StockLog
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.util.*

class StockOpnameActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: OpnameProductAdapter
    private var allProducts: List<Product> = emptyList()
    private var opnameList: List<Product> = emptyList() // 🔥 Filtered List

    // 🔥 SCAN LAUNCHER (Restored)
    private val scanLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (!barcode.isNullOrEmpty()) {
                val etSearch = findViewById<EditText>(R.id.etSearch)
                etSearch.setText(barcode)
                Toast.makeText(this, "Barcode: $barcode", Toast.LENGTH_SHORT).show()
                
                // Auto Open Dialog
                val found = opnameList.find { (it.barcode ?: "") == barcode }
                if (found != null) {
                   showAdjustmentDialog(found)
                }
            }
        }
    }

    // ...

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_opname) // Pastikan XML ini ada
        
        // 🔥 Set Status Bar Blue
        window.statusBarColor = android.graphics.Color.parseColor("#1976D2")

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        setupRecyclerView()
        setupSearch()

        viewModel.allProducts.observe(this) { products ->
            allProducts = products
            // 🔥 FILTER OPNAME: Hanya yg TrackStock=TRUE (Barang Fisik) ATAU isIngredient=TRUE (Bahan Baku)
            // Menu Resep / Jasa (TrackStock=FALSE) otomatis HILANG dari list ini.
            opnameList = products.filter { it.trackStock || it.isIngredient }
            adapter.submitList(opnameList)
        }
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvProducts)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = OpnameProductAdapter { product ->
            showAdjustmentDialog(product)
        }
        rv.adapter = adapter
    }

    private fun setupSearch() {
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val btnScan = findViewById<android.view.View>(R.id.btnScan) // 🔥 Generic View biar aman (ImageButton)

        btnScan.setOnClickListener {
            val intent = android.content.Intent(this, ScanActivity::class.java)
            scanLauncher.launch(intent)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                // 🔥 SEARCH DARI LIST OPNAME (YG SUDAH BERSIH)
                val filtered = opnameList.filter {
                    it.name.lowercase().contains(query) || (it.barcode ?: "").contains(query)
                }
                adapter.submitList(filtered)
            }
        })
    }

    private fun showAdjustmentDialog(product: Product) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_stock_opname, null)
        val tvName = view.findViewById<TextView>(R.id.tvProductName)
        val tvSystemStock = view.findViewById<TextView>(R.id.tvSystemStock)
        val etPhysicalStock = view.findViewById<EditText>(R.id.etPhysicalStock)
        val tvDifference = view.findViewById<TextView>(R.id.tvDifference)
        val etNote = view.findViewById<EditText>(R.id.etNote)

        tvName.text = product.name
        tvSystemStock.text = "${product.stock}"
        
        // Auto-calculate difference
        etPhysicalStock.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val physical = s.toString().toIntOrNull() ?: product.stock
                val diff = physical - product.stock
                tvDifference.text = if (diff > 0) "+$diff" else "$diff"
                
                if (diff < 0) tvDifference.setTextColor(android.graphics.Color.RED)
                else if (diff > 0) tvDifference.setTextColor(android.graphics.Color.parseColor("#2E7D32")) // Green
                else tvDifference.setTextColor(android.graphics.Color.BLACK)
            }
        })

        AlertDialog.Builder(this)
            .setTitle("Penyesuaian Stok")
            .setView(view)
            .setPositiveButton("SIMPAN") { _, _ ->
                val physical = etPhysicalStock.text.toString().toIntOrNull()
                if (physical == null) {
                    Toast.makeText(this, "Stok fisik harus diisi!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val diff = physical - product.stock
                if (diff == 0) return@setPositiveButton // No change

                val note = etNote.text.toString().ifEmpty { "Stok Opname" }
                
                // 🔥 PROSES UPDATE STOK & LOG
                processAdjustment(product, diff, physical, note)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun processAdjustment(product: Product, diff: Int, newStock: Int, note: String) {
        // 1. Update Produk di DB Local
        val updatedProduct = product.copy(stock = newStock)
        viewModel.update(updatedProduct)

        // 2. Buat Log (Type: OPNAME)
        // 🔥 MODIFIKASI: Simpan Stok Awal & Akhir di dalam String SupplierName (Biar ga ubah DB)
        // Format: "SO: Catatan || Awal:10 || Akhir:12"
        val logInfo = "SO: $note || Awal:${product.stock} || Akhir:$newStock"

        val log = StockLog(
            timestamp = System.currentTimeMillis(),
            productName = product.name,
            supplierName = logInfo, 
            quantity = diff, // Selisih (+ atau -)
            costPrice = product.costPrice,
            totalCost = diff * product.costPrice, // Nilai selisih uang
            type = "OPNAME",
            invoiceNumber = "SO-${System.currentTimeMillis()}" // ID Unik Opname
        )
        // Gunakan ViewModel untuk insert log (pastikan function log ini ada/bisa diakses)
        // Karena ViewModel biasanya private fun, kita pakai 'recordPurchase' yg generic untuk stock log
        viewModel.recordPurchase(log) 

        Toast.makeText(this, "Stok berhasil disesuaikan!", Toast.LENGTH_SHORT).show()
    }

    // --- INNER ADAPTER CLASS ---
    class OpnameProductAdapter(private val onClick: (Product) -> Unit) : RecyclerView.Adapter<OpnameProductAdapter.ViewHolder>() {
        private var list: List<Product> = emptyList()

        fun submitList(newList: List<Product>) {
            list = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvName)
            val tvStock: TextView = itemView.findViewById(R.id.tvStock)
            val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)

            fun bind(p: Product) {
                tvName.text = p.name
                tvStock.text = "Sistem: ${p.stock} ${p.unit}"
                
                // Basic Image Loading (Same as ProductAdapter)
                 if (!p.imagePath.isNullOrEmpty()) {
                    Glide.with(itemView.context).load(p.imagePath).into(imgProduct)
                } else {
                    imgProduct.setImageResource(R.drawable.ic_launcher_background) // Placeholder
                }
                
                itemView.setOnClickListener { onClick(p) }
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product_simple, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = list.size
    }
}
