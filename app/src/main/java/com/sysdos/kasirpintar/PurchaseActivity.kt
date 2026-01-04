package com.sysdos.kasirpintar

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.StockLog
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.*

class PurchaseActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    // TAB INPUT
    private lateinit var adapterInput: PurchaseInputAdapter
    private val purchaseList = ArrayList<PurchaseItem>()
    private var allProducts: List<Product> = emptyList()
    private var supplierList: List<String> = emptyList()
    private lateinit var spSupplier: Spinner
    private lateinit var tvTotal: TextView
    private lateinit var svSearch: SearchView

    // TAB RIWAYAT
    private lateinit var adapterHistory: HistoryLogAdapter
    private var historyList: List<StockLog> = emptyList()

    // LAYOUTS & TABS
    private lateinit var layoutInput: LinearLayout
    private lateinit var layoutHistory: LinearLayout
    private lateinit var btnTabInput: Button
    private lateinit var btnTabHistory: Button

    // SCANNER
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            findAndAddProduct(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // INIT VIEW
        layoutInput = findViewById(R.id.layoutInput)
        layoutHistory = findViewById(R.id.layoutHistory)
        btnTabInput = findViewById(R.id.btnTabInput)
        btnTabHistory = findViewById(R.id.btnTabHistory)

        spSupplier = findViewById(R.id.spSupplier)
        tvTotal = findViewById(R.id.tvTotalPurchase)
        svSearch = findViewById(R.id.svSearchProduct)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // --- SETUP TAB CLICKS ---
        btnTabInput.setOnClickListener { switchTab("INPUT") }
        btnTabHistory.setOnClickListener { switchTab("HISTORY") }

        // --- SETUP INPUT PAGE ---
        val rvInput = findViewById<RecyclerView>(R.id.rvPurchaseItems)
        adapterInput = PurchaseInputAdapter(purchaseList,
            onDelete = { removeItem(it) },
            onEdit = { showEditDialog(it) }
        )
        rvInput.layoutManager = LinearLayoutManager(this)
        rvInput.adapter = adapterInput

        findViewById<View>(R.id.btnScanBarcode).setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(ScanActivity::class.java)
            barcodeLauncher.launch(options)
        }

        svSearch.setOnClickListener { showProductSearchDialog() }
        svSearch.setOnSearchClickListener { showProductSearchDialog() }
        findViewById<Button>(R.id.btnSavePurchase).setOnClickListener { saveTransaction() }

        // --- SETUP HISTORY PAGE ---
        val rvHistory = findViewById<RecyclerView>(R.id.rvHistoryLog)
        adapterHistory = HistoryLogAdapter()
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapterHistory

        // --- OBSERVE DATA ---
        viewModel.allProducts.observe(this) { allProducts = it }
        viewModel.allSuppliers.observe(this) { suppliers ->
            supplierList = suppliers.map { it.name }
            val adapterSp = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, supplierList)
            spSupplier.adapter = adapterSp
        }

        // --- PERBAIKAN LOGIKA LOAD DATA ---
        try {
            viewModel.stockLogs.observe(this) { logs ->
                // Gunakan '?: emptyList()' agar jika data null, dianggap list kosong
                val safeLogs = logs ?: emptyList()

                // Sekarang 'it' sudah dikenali sebagai StockLog
                historyList = safeLogs
                    .filter { it.type == "IN" }
                    .sortedByDescending { it.timestamp }

                adapterHistory.submitList(historyList)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memuat riwayat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchTab(tab: String) {
        val activeColor = ContextCompat.getColor(this, R.color.colorPrimary) // Biru
        val inactiveColor = Color.parseColor("#E0E0E0")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#757575")

        if (tab == "INPUT") {
            layoutInput.visibility = View.VISIBLE
            layoutHistory.visibility = View.GONE

            btnTabInput.setBackgroundColor(activeColor); btnTabInput.setTextColor(activeText)
            btnTabHistory.setBackgroundColor(inactiveColor); btnTabHistory.setTextColor(inactiveText)
        } else {
            layoutInput.visibility = View.GONE
            layoutHistory.visibility = View.VISIBLE

            btnTabHistory.setBackgroundColor(activeColor); btnTabHistory.setTextColor(activeText)
            btnTabInput.setBackgroundColor(inactiveColor); btnTabInput.setTextColor(inactiveText)
        }
    }

    // --- LOGIC INPUT ---
    private fun findAndAddProduct(keyword: String) {
        val product = allProducts.find { it.barcode == keyword || it.name.equals(keyword, true) }
        if (product != null) {
            addItemToCart(product)
            svSearch.setQuery("", false)
            Toast.makeText(this, "${product.name} ditambahkan", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Barang tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addItemToCart(product: Product) {
        val existing = purchaseList.find { it.product.id == product.id }
        if (existing != null) {
            existing.qty += 1
        } else {
            purchaseList.add(PurchaseItem(product, 1, product.costPrice))
        }
        updateUI()
    }

    private fun updateUI() {
        adapterInput.notifyDataSetChanged()
        val total = purchaseList.sumOf { it.qty * it.cost }
        tvTotal.text = formatRupiah(total)
    }

    private fun removeItem(item: PurchaseItem) {
        purchaseList.remove(item)
        updateUI()
    }

    private fun showProductSearchDialog() {
        val names = allProducts.map { "${it.name} \n(${it.barcode ?: "-"})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Pilih Barang").setItems(names) { _, which ->
            addItemToCart(allProducts[which])
        }.show()
    }

    private fun showEditDialog(item: PurchaseItem) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val etQty = EditText(this).apply { hint = "Jumlah"; inputType = InputType.TYPE_CLASS_NUMBER; setText(item.qty.toString()) }
        val etCost = EditText(this).apply { hint = "Harga Beli"; inputType = InputType.TYPE_CLASS_NUMBER; setText(item.cost.toInt().toString()) }

        layout.addView(TextView(this).apply { text = "Jumlah:"; textSize=12f })
        layout.addView(etQty)
        layout.addView(TextView(this).apply { text = "Harga Modal (per pcs):"; textSize=12f; layoutParams = LinearLayout.LayoutParams(-1,-2).apply{topMargin=20} })
        layout.addView(etCost)

        AlertDialog.Builder(this).setTitle(item.product.name).setView(layout).setPositiveButton("Update") { _, _ ->
            val q = etQty.text.toString().toIntOrNull() ?: 1
            val c = etCost.text.toString().toDoubleOrNull() ?: item.cost
            item.qty = q; item.cost = c
            updateUI()
        }.show()
    }

    private fun saveTransaction() {
        if (purchaseList.isEmpty()) { Toast.makeText(this, "Keranjang kosong!", Toast.LENGTH_SHORT).show(); return }
        val supplierName = if(spSupplier.selectedItem != null) spSupplier.selectedItem.toString() else "Umum"

        AlertDialog.Builder(this).setTitle("Konfirmasi Simpan").setMessage("Stok akan bertambah. Lanjutkan?")
            .setPositiveButton("Ya") { _, _ ->
                for (item in purchaseList) {
                    val oldProduct = item.product
                    val newStock = oldProduct.stock + item.qty
                    val updatedProduct = oldProduct.copy(stock = newStock, costPrice = item.cost, supplier = supplierName)
                    viewModel.update(updatedProduct)

                    val log = StockLog(timestamp = System.currentTimeMillis(), productName = oldProduct.name, supplierName = supplierName, quantity = item.qty, costPrice = item.cost, totalCost = item.qty * item.cost, type = "IN")
                    viewModel.recordPurchase(log)
                }
                Toast.makeText(this, "Stok Masuk Berhasil! ✅", Toast.LENGTH_LONG).show()
                purchaseList.clear(); updateUI()
                switchTab("HISTORY") // Pindah ke tab History setelah simpan
            }.setNegativeButton("Batal", null).show()
    }

    private fun formatRupiah(number: Double): String = String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')

    // --- DATA CLASS ---
    data class PurchaseItem(val product: Product, var qty: Int, var cost: Double)

    // --- ADAPTER INPUT ---
    inner class PurchaseInputAdapter(private val list: List<PurchaseItem>, private val onDelete: (PurchaseItem) -> Unit, private val onEdit: (PurchaseItem) -> Unit) : RecyclerView.Adapter<PurchaseInputAdapter.Holder>() {
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(android.R.id.text1)
            val tvInfo: TextView = v.findViewById(android.R.id.text2)
            init { tvName.textSize = 16f; tvName.setTypeface(null, android.graphics.Typeface.BOLD); v.setPadding(20, 20, 20, 20); v.setBackgroundColor(Color.WHITE) }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            holder.tvName.text = item.product.name
            holder.tvInfo.text = "${item.qty} pcs x ${formatRupiah(item.cost)} = ${formatRupiah(item.qty * item.cost)}"
            holder.itemView.setOnClickListener { onEdit(item) }
            holder.itemView.setOnLongClickListener { onDelete(item); true }
        }
        override fun getItemCount(): Int = list.size
    }

    // --- ADAPTER HISTORY ---
    // --- ADAPTER HISTORY (PERBAIKAN: HAPUS tvDate) ---
    inner class HistoryLogAdapter : RecyclerView.Adapter<HistoryLogAdapter.Holder>() {
        private var list: List<StockLog> = emptyList()
        fun submitList(newList: List<StockLog>) { list = newList; notifyDataSetChanged() }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            // HAPUS tvDate KARENA simple_list_item_2 CUMA PUNYA text1 DAN text2
            val tvName: TextView = v.findViewById(android.R.id.text1)   // Judul (Atas)
            val tvDetail: TextView = v.findViewById(android.R.id.text2) // Detail (Bawah)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(item.timestamp))

            // Text 1: Nama Barang & Jumlah
            holder.tvName.text = "${item.productName} (+${item.quantity})"
            holder.tvName.setTextColor(Color.parseColor("#2E7D32")) // Hijau
            holder.tvName.setTypeface(null, android.graphics.Typeface.BOLD)

            // Text 2: Tanggal, Supplier, Total (Gabung disini)
            holder.tvDetail.text = "$date • ${item.supplierName} • Total: ${formatRupiah(item.totalCost)}"
        }

        override fun getItemCount(): Int = list.size
    }
}