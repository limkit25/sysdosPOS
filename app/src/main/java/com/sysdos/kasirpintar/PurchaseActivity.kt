package com.sysdos.kasirpintar

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
import com.sysdos.kasirpintar.data.model.Supplier
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.*

class PurchaseActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    // DATA
    private val purchaseList = ArrayList<PurchaseItem>()
    private var allProducts: List<Product> = emptyList()
    private var supplierList: List<Supplier> = emptyList()

    // UI
    private lateinit var adapterInput: PurchaseInputAdapter
    private lateinit var adapterHistory: HistoryGroupAdapter
    private lateinit var spSupplier: Spinner
    private lateinit var btnAddSupplier: ImageButton
    private lateinit var tvTotal: TextView
    private lateinit var svSearch: SearchView
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

        initViews()
        setupRecyclerViews()
        observeData()
        setupListeners()
    }

    private fun initViews() {
        layoutInput = findViewById(R.id.layoutInput)
        layoutHistory = findViewById(R.id.layoutHistory)
        btnTabInput = findViewById(R.id.btnTabInput)
        btnTabHistory = findViewById(R.id.btnTabHistory)
        spSupplier = findViewById(R.id.spSupplier)
        btnAddSupplier = findViewById(R.id.btnAddSupplier)
        tvTotal = findViewById(R.id.tvTotalPurchase)
        svSearch = findViewById(R.id.svSearchProduct)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupRecyclerViews() {
        // === 1. RecyclerView untuk LIST BARANG MASUK (Input Mode) ===
        val rvInput = findViewById<RecyclerView>(R.id.rvPurchaseItems)
        adapterInput = PurchaseInputAdapter(purchaseList,
            onDelete = { removeItem(it) },
            onEdit = { showAddToCartDialog(it.product, it) }
        )

        // 🔥 LOGIKA RESPONSIVE: HP (List) vs TABLET (Grid)
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        if (screenWidthDp >= 600) {
            // TABLET: Gunakan Grid 2 Kolom biar tidak terlalu lebar kosong
            rvInput.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        } else {
            // HP: Tetap List ke Bawah
            rvInput.layoutManager = LinearLayoutManager(this)
        }

        rvInput.adapter = adapterInput


        // === 2. RecyclerView untuk HISTORY (Riwayat Faktur) ===
        val rvHistory = findViewById<RecyclerView>(R.id.rvHistoryLog)
        adapterHistory = HistoryGroupAdapter { purchaseId -> showPurchaseDetailDialog(purchaseId) }

        // 🔥 LOGIKA RESPONSIVE HISTORY JUGA
        if (screenWidthDp >= 600) {
            // TABLET: Grid 2 Kolom
            rvHistory.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        } else {
            // HP: List
            rvHistory.layoutManager = LinearLayoutManager(this)
        }

        rvHistory.adapter = adapterHistory
    }

    private fun setupListeners() {
        btnTabInput.setOnClickListener { switchTab("INPUT") }
        btnTabHistory.setOnClickListener { switchTab("HISTORY") }
        btnAddSupplier.setOnClickListener { showAddSupplierDialog() }

        findViewById<View>(R.id.btnScanBarcode).setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(ScanActivity::class.java)
            barcodeLauncher.launch(options)
        }

        svSearch.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showProductSearchDialog()
                svSearch.clearFocus()
            }
        }
        findViewById<Button>(R.id.btnSavePurchase).setOnClickListener { saveTransaction() }
    }

    private fun observeData() {
        viewModel.allProducts.observe(this) { allProducts = it }
        viewModel.allSuppliers.observe(this) { suppliers ->
            supplierList = suppliers
            val names = suppliers.map { it.name }.toMutableList()
            names.add(0, "-- Pilih Supplier --")
            spSupplier.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
        viewModel.purchaseHistory.observe(this) { logs -> adapterHistory.submitList(logs ?: emptyList()) }
    }

    private fun switchTab(tab: String) {
        val activeColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val inactive = Color.parseColor("#E0E0E0")
        val activeTxt = Color.WHITE
        val inactiveTxt = Color.parseColor("#757575")

        if (tab == "INPUT") {
            layoutInput.visibility = View.VISIBLE
            layoutHistory.visibility = View.GONE
            btnTabInput.setBackgroundColor(activeColor); btnTabInput.setTextColor(activeTxt)
            btnTabHistory.setBackgroundColor(inactive); btnTabHistory.setTextColor(inactiveTxt)
        } else {
            layoutInput.visibility = View.GONE
            layoutHistory.visibility = View.VISIBLE
            btnTabHistory.setBackgroundColor(activeColor); btnTabHistory.setTextColor(activeTxt)
            btnTabInput.setBackgroundColor(inactive); btnTabInput.setTextColor(inactiveTxt)
        }
    }

    private fun findAndAddProduct(keyword: String) {
        val product = allProducts.find { it.barcode == keyword || it.name.equals(keyword, true) }
        if (product != null) {
            showAddToCartDialog(product) // 🔥 PANGGIL DIALOG BARU
        } else {
            Toast.makeText(this, "Barang tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔥 FITUR UTAMA: DIALOG INPUT DENGAN KALKULATOR GROSIR 🔥
    private fun showAddToCartDialog(product: Product, existingItem: PurchaseItem? = null) {
        val context = this
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 20)

        // 1. Opsi Mode
        val cbBulkMode = CheckBox(context)
        cbBulkMode.text = "Beli Satuan Besar (Dus/Pack/Kg)?"
        cbBulkMode.textSize = 14f
        cbBulkMode.setTextColor(Color.BLUE)

        // 2. Input Utama
        val etQty = EditText(context).apply { hint = "Jumlah Beli (Pcs)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val etCost = EditText(context).apply { hint = "Harga Modal (per Pcs)"; inputType = InputType.TYPE_CLASS_NUMBER }

        // 3. Input Tambahan (Hidden Awalnya)
        val layoutBulk = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val etIsiPerDus = EditText(context).apply { hint = "Isi per Dus (Pcs)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val etTotalHarga = EditText(context).apply { hint = "Total Rupiah (Semua)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val tvPreview = TextView(context).apply { text = "Hasil: 0 Pcs @ Rp 0"; setPadding(0,10,0,0); setTextColor(Color.DKGRAY) }

        layoutBulk.addView(etIsiPerDus)
        layoutBulk.addView(etTotalHarga)
        layoutBulk.addView(tvPreview)

        layout.addView(cbBulkMode)
        layout.addView(etQty)
        layout.addView(etCost)
        layout.addView(layoutBulk)

        // ISI DATA DEFAULT JIKA EDIT
        if (existingItem != null) {
            etQty.setText(existingItem.qty.toString())
            etCost.setText(existingItem.cost.toInt().toString())
        } else {
            etCost.setText(product.costPrice.toInt().toString())
        }

        // --- LOGIKA PERHITUNGAN OTOMATIS ---
        cbBulkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutBulk.visibility = View.VISIBLE
                etCost.isEnabled = false // Harga satuan dikunci, dihitung otomatis
                etQty.hint = "Jumlah Beli (Dus/Pack)" // Ubah hint
            } else {
                layoutBulk.visibility = View.GONE
                etCost.isEnabled = true
                etQty.hint = "Jumlah Beli (Pcs)"
            }
        }

        // Listener untuk hitung otomatis saat ngetik di mode Dus
        val calculatorWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!cbBulkMode.isChecked) return

                val jumDus = etQty.text.toString().toIntOrNull() ?: 0
                val isiPerDus = etIsiPerDus.text.toString().toIntOrNull() ?: 1
                val totalUang = etTotalHarga.text.toString().toDoubleOrNull() ?: 0.0

                val totalPcs = jumDus * isiPerDus
                val hargaPerPcs = if (totalPcs > 0) totalUang / totalPcs else 0.0

                // Update Preview & Field Cost
                etCost.setText(hargaPerPcs.toInt().toString())
                tvPreview.text = "Masuk Stok: $totalPcs Pcs\nModal Baru: Rp ${hargaPerPcs.toInt()}/pcs"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etQty.addTextChangedListener(calculatorWatcher)
        etIsiPerDus.addTextChangedListener(calculatorWatcher)
        etTotalHarga.addTextChangedListener(calculatorWatcher)

        // Tampilkan Dialog
        AlertDialog.Builder(context)
            .setTitle(product.name)
            .setView(layout)
            .setPositiveButton("MASUKKAN") { _, _ ->
                var finalQty = etQty.text.toString().toIntOrNull() ?: 0
                val finalCost = etCost.text.toString().toDoubleOrNull() ?: 0.0

                // Jika Mode Grosir aktif, hitung ulang Qty-nya
                if (cbBulkMode.isChecked) {
                    val isi = etIsiPerDus.text.toString().toIntOrNull() ?: 1
                    finalQty *= isi // Konversi Dus ke Pcs
                }

                if (finalQty > 0) {
                    addItemToCart(product, finalQty, finalCost)
                    svSearch.clearFocus()
                } else {
                    Toast.makeText(context, "Jumlah tidak boleh 0", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun addItemToCart(product: Product, qty: Int, cost: Double) {
        // Cek jika item sudah ada, update/timpa
        val existing = purchaseList.find { it.product.id == product.id }
        if (existing != null) {
            existing.qty += qty
            existing.cost = cost // Update harga modal terbaru
        } else {
            purchaseList.add(PurchaseItem(product, qty, cost))
        }
        updateUI()
        Toast.makeText(this, "Masuk Keranjang: $qty pcs", Toast.LENGTH_SHORT).show()
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

    // --- SMART SEARCH (DIPERTAHANKAN) ---
    private fun showProductSearchDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_product_search, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchQuery)
        val rvSearch = dialogView.findViewById<RecyclerView>(R.id.rvSearchResults)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Cari Barang")
            .setView(dialogView)
            .setNegativeButton("Batal", null)
            .create()

        val searchAdapter = SearchProductAdapter(allProducts) { selectedProduct ->
            dialog.dismiss()
            showAddToCartDialog(selectedProduct) // 🔥 PANGGIL DIALOG KALKULATOR
        }
        rvSearch.layoutManager = LinearLayoutManager(this)
        rvSearch.adapter = searchAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s.toString().lowercase()
                val filteredList = allProducts.filter {
                    it.name.lowercase().contains(keyword) || (it.barcode ?: "").contains(keyword)
                }
                searchAdapter.updateList(filteredList)
            }
        })
        dialog.show()
        etSearch.requestFocus()
    }

    // --- SAVE & OTHERS ---
    private fun saveTransaction() {
        if (purchaseList.isEmpty()) { Toast.makeText(this, "Keranjang kosong!", Toast.LENGTH_SHORT).show(); return }
        val supplierName = if(spSupplier.selectedItem != null && spSupplier.selectedItemPosition > 0) spSupplier.selectedItem.toString() else "Umum"

        AlertDialog.Builder(this).setTitle("Konfirmasi Simpan").setMessage("Stok akan bertambah. Lanjutkan?")
            .setPositiveButton("Ya") { _, _ ->
                val purchaseId = System.currentTimeMillis()
                for (item in purchaseList) {
                    val oldProduct = item.product
                    val newStock = oldProduct.stock + item.qty
                    val updatedProduct = oldProduct.copy(stock = newStock, costPrice = item.cost, supplier = supplierName)
                    viewModel.update(updatedProduct)

                    val log = StockLog(
                        purchaseId = purchaseId, timestamp = purchaseId, productName = oldProduct.name,
                        supplierName = supplierName, quantity = item.qty, costPrice = item.cost,
                        totalCost = item.qty * item.cost, type = "IN"
                    )
                    viewModel.recordPurchase(log)
                }
                Toast.makeText(this, "Stok Masuk Berhasil! ✅", Toast.LENGTH_LONG).show()
                purchaseList.clear(); updateUI()
                switchTab("HISTORY")
            }.setNegativeButton("Batal", null).show()
    }

    private fun showPurchaseDetailDialog(purchaseId: Long) {
        viewModel.getPurchaseDetails(purchaseId) { details ->
            if (details.isEmpty()) return@getPurchaseDetails
            val sb = StringBuilder()
            var grandTotal = 0.0
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(details[0].timestamp))
            val supp = details[0].supplierName
            details.forEach { item ->
                sb.append("📦 ${item.productName}\n")
                sb.append("   ${item.quantity} x ${formatRupiah(item.costPrice)} = ${formatRupiah(item.totalCost)}\n")
                sb.append("--------------------------------\n")
                grandTotal += item.totalCost
            }
            AlertDialog.Builder(this).setTitle("Detail Faktur").setMessage("Supplier: $supp\nWaktu: $dateStr\n\n$sb\nTOTAL: ${formatRupiah(grandTotal)}").setPositiveButton("Tutup", null).show()
        }
    }

    private fun showAddSupplierDialog() {
        val context = this
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 10) }
        val etName = EditText(context).apply { hint = "Nama Supplier (Wajib)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS }
        val etPhone = EditText(context).apply { hint = "Nomor Telepon/WA"; inputType = InputType.TYPE_CLASS_PHONE }
        val etAddress = EditText(context).apply { hint = "Alamat Lengkap"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 2 }
        layout.addView(etName); layout.addView(etPhone); layout.addView(etAddress)

        AlertDialog.Builder(context).setTitle("Tambah Supplier").setView(layout).setPositiveButton("SIMPAN") { _, _ ->
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                val newSupplier = Supplier(name = name, phone = etPhone.text.toString(), address = etAddress.text.toString())
                viewModel.insertSupplier(newSupplier)
                Toast.makeText(context, "Supplier '$name' disimpan!", Toast.LENGTH_SHORT).show()
            } else { Toast.makeText(context, "Nama wajib diisi!", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("Batal", null).show()
    }

    private fun formatRupiah(number: Double): String = String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')
    data class PurchaseItem(val product: Product, var qty: Int, var cost: Double)

    // ADAPTER: Input Keranjang (DENGAN TOMBOL HAPUS)
    inner class PurchaseInputAdapter(
        private val list: List<PurchaseItem>,
        private val onDelete: (PurchaseItem) -> Unit,
        private val onEdit: (PurchaseItem) -> Unit
    ) : RecyclerView.Adapter<PurchaseInputAdapter.Holder>() {

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvProductName)
            val tvCalc: TextView = v.findViewById(R.id.tvCalculation)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
            val layoutInfo: LinearLayout = v.findViewById(R.id.layoutInfo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            // 🔥 UBAH BAGIAN INI: Gunakan layout baru 'item_purchase_row'
            // Jangan pakai 'item_cart_row' agar kasir tidak terganggu
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_purchase_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]

            holder.tvName.text = item.product.name
            holder.tvCalc.text = "${item.qty} pcs x ${formatRupiah(item.cost)} = ${formatRupiah(item.qty * item.cost)}"

            // Klik Tombol Sampah -> Hapus
            holder.btnDelete.setOnClickListener {
                onDelete(item)
            }

            // Klik Bagian Teks -> Edit Jumlah
            holder.layoutInfo.setOnClickListener {
                onEdit(item)
            }
        }

        override fun getItemCount(): Int = list.size
    }

    // ADAPTER HISTORY & SEARCH (SAMA SEPERTI SEBELUMNYA)
    inner class HistoryGroupAdapter(private val onClick: (Long) -> Unit) : RecyclerView.Adapter<HistoryGroupAdapter.Holder>() {
        private var list: List<StockLog> = emptyList()
        fun submitList(newList: List<StockLog>) { list = newList; notifyDataSetChanged() }
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvHeader: TextView = v.findViewById(android.R.id.text1)
            val tvSub: TextView = v.findViewById(android.R.id.text2)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            val date = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
            holder.tvHeader.text = "FAKTUR: ${item.supplierName}"; holder.tvHeader.setTextColor(Color.parseColor("#1976D2")); holder.tvHeader.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.tvSub.text = "$date\n(Ketuk untuk lihat detail)"
            holder.itemView.setOnClickListener { onClick(item.purchaseId) }
        }
        override fun getItemCount(): Int = list.size
    }

    inner class SearchProductAdapter(private var items: List<Product>, private val onItemClick: (Product) -> Unit) : RecyclerView.Adapter<SearchProductAdapter.Holder>() {
        fun updateList(newItems: List<Product>) { items = newItems; notifyDataSetChanged() }
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(android.R.id.text1)
            val tvInfo: TextView = v.findViewById(android.R.id.text2)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val product = items[position]
            holder.tvName.text = product.name; holder.tvName.setTextColor(Color.BLACK)
            holder.tvInfo.text = "Stok: ${product.stock} | Modal: Rp ${product.costPrice.toInt()}"
            holder.itemView.setOnClickListener { onItemClick(product) }
        }
        override fun getItemCount(): Int = items.size
    }
}