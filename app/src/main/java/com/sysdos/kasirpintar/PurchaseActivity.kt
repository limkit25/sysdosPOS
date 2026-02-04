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
import androidx.lifecycle.lifecycleScope // 🔥 IMPORT CORRECT
import kotlinx.coroutines.launch       // 🔥 IMPORT CORRECT

class PurchaseActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    // DATA
    private val purchaseList = ArrayList<PurchaseItem>()
    private var allProducts: List<Product> = emptyList()
    private var supplierList: List<Supplier> = emptyList()

    // UI
    private lateinit var adapterInput: PurchaseInputAdapter
    private lateinit var adapterHistory: HistoryGroupAdapter
    private lateinit var adapterSearch: SearchProductAdapter // 🔥 ADAPTER PENCARIAN
    private lateinit var spSupplier: Spinner
    private lateinit var btnAddSupplier: ImageButton
    private lateinit var tvTotal: TextView
    private lateinit var etInvoiceNumber: EditText
    private lateinit var layoutInput: LinearLayout
    private lateinit var layoutHistory: LinearLayout
    
    // 🔥 VIEW BARU UTK UI PENCARIAN
    private lateinit var layoutCartContent: LinearLayout
    private lateinit var layoutBottomAction: LinearLayout
    private lateinit var layoutSearchOverlay: LinearLayout // 🔥 NEW PARENT
    private lateinit var rvSearchFull: RecyclerView
    private lateinit var btnSearchTrigger: TextView
    private lateinit var btnBackFromSearch: ImageButton
    private lateinit var etSearchOverlay: EditText

    private lateinit var btnTabInput: Button
    private lateinit var btnTabHistory: Button

    // 🔥 VIEW BARU UTK FORM INPUT FULL SCREEN
    private lateinit var layoutFormInput: ScrollView
    private lateinit var tvFormProductName: TextView
    private lateinit var tvFormUnitInfo: TextView
    private lateinit var cbFormBulkMode: CheckBox
    private lateinit var etFormQty: TextView // TextInputEditText extends TextView
    private lateinit var etFormCost: TextView
    private lateinit var layoutFormBulkDetails: LinearLayout
    private lateinit var rgFormConversion: RadioGroup
    private lateinit var rbFormManual: RadioButton
    private lateinit var rbFormKilo: RadioButton
    private lateinit var etFormIsiPerDus: TextView
    private lateinit var etFormTotalHarga: TextView
    private lateinit var tvFormPreview: TextView
    private lateinit var btnFormSubmit: Button
    private lateinit var btnFormCancel: Button

    // STATE PENCARIAN & VAR
    private var selectedProductForInput: Product? = null

    // SCANNER
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            findAndAddProduct(result.contents)
        }
    }

    private lateinit var layoutTopInputs: LinearLayout // 🔥 WRAPPER INPUT SUPPLIER/INVOICE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        initViews()
        setupRecyclerViews()
        observeData()
        setupListeners()
        setupFormListeners() // 🔥 SETUP LISTENER FORM
    }

    private fun initViews() {
        layoutInput = findViewById(R.id.layoutInput)
        layoutHistory = findViewById(R.id.layoutHistory)
        btnTabInput = findViewById(R.id.btnTabInput)
        btnTabHistory = findViewById(R.id.btnTabHistory)
        spSupplier = findViewById(R.id.spSupplier)
        btnAddSupplier = findViewById(R.id.btnAddSupplier)
        tvTotal = findViewById(R.id.tvTotalPurchase)
        etInvoiceNumber = findViewById(R.id.etInvoiceNumber)
        
        layoutTopInputs = findViewById(R.id.layoutTopInputs) // 🔥 BIND

        // 🔥 BIND FORM VIEWS
        layoutCartContent = findViewById(R.id.layoutCartContent)
        layoutBottomAction = findViewById(R.id.layoutBottomAction)
        layoutSearchOverlay = findViewById(R.id.layoutSearchOverlay) // 🔥 BIND
        rvSearchFull = findViewById(R.id.rvSearchResultsFull)
        btnSearchTrigger = findViewById(R.id.btnSearchTrigger)
        btnBackFromSearch = findViewById(R.id.btnBackFromSearch)
        etSearchOverlay = findViewById(R.id.etSearchOverlay)

        layoutFormInput = findViewById(R.id.layoutFormInput)
        tvFormProductName = findViewById(R.id.tvFormProductName)
        tvFormUnitInfo = findViewById(R.id.tvFormUnitInfo)
        cbFormBulkMode = findViewById(R.id.cbFormBulkMode)
        etFormQty = findViewById(R.id.etFormQty)
        etFormCost = findViewById(R.id.etFormCost)
        layoutFormBulkDetails = findViewById(R.id.layoutFormBulkDetails)
        rgFormConversion = findViewById(R.id.rgFormConversion)
        rbFormManual = findViewById(R.id.rbFormManual)
        rbFormKilo = findViewById(R.id.rbFormKilo)
        etFormIsiPerDus = findViewById(R.id.etFormIsiPerDus)
        etFormTotalHarga = findViewById(R.id.etFormTotalHarga)
        tvFormPreview = findViewById(R.id.tvFormPreview)
        btnFormSubmit = findViewById(R.id.btnFormSubmit)
        btnFormCancel = findViewById(R.id.btnFormCancel)

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        com.sysdos.kasirpintar.utils.BottomNavHelper.setup(this, bottomNav, viewModel)
    }

    private fun setupRecyclerViews() {
        val rvInput = findViewById<RecyclerView>(R.id.rvPurchaseItems)
        adapterInput = PurchaseInputAdapter(purchaseList,
            onDelete = { removeItem(it) },
            onEdit = { openInputForm(it.product, it) } // 🔥 GANTI SHOW DIALOG
        )
        rvInput.layoutManager = LinearLayoutManager(this)
        rvInput.adapter = adapterInput

        val rvHistory = findViewById<RecyclerView>(R.id.rvHistoryLog)
        adapterHistory = HistoryGroupAdapter { purchaseId -> showPurchaseDetailDialog(purchaseId) }
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapterHistory

        adapterSearch = SearchProductAdapter(emptyList()) { selectedProduct ->
            // Saat Item diklik:
            etSearchOverlay.setText("")
            etSearchOverlay.clearFocus()
            toggleSearchMode(false) 
            
            openInputForm(selectedProduct) // 🔥 BUKA FULL FORM
        }
        rvSearchFull.layoutManager = LinearLayoutManager(this)
        rvSearchFull.adapter = adapterSearch
    }

    private fun toggleSearchMode(isActive: Boolean) {
        if (isActive) {
            layoutTopInputs.visibility = View.GONE
            layoutCartContent.visibility = View.GONE
            layoutBottomAction.visibility = View.GONE
            layoutSearchOverlay.visibility = View.VISIBLE // 🔥 SHOW OVERLAY
            layoutFormInput.visibility = View.GONE
            
            adapterSearch.updateList(purchaseableProducts) // 🔥 TAMPILKAN HANYA YG BISA DIBELI
            etSearchOverlay.setText("") // Reset text
            etSearchOverlay.requestFocus() // Focus ke input baru
        } else {
            layoutTopInputs.visibility = View.VISIBLE
            layoutCartContent.visibility = View.VISIBLE
            layoutBottomAction.visibility = View.VISIBLE
            layoutSearchOverlay.visibility = View.GONE // 🔥 HIDE OVERLAY
            layoutFormInput.visibility = View.GONE
        }
    }

    // 🔥 PENGGANTI DIALOG -> FULL FORM LOGIC
    private fun openInputForm(product: Product, existingItem: PurchaseItem? = null) {
        selectedProductForInput = product
        
        // Hide others, Show Form
        layoutTopInputs.visibility = View.GONE // 🔥 HIDE HEADER JUGA
        layoutCartContent.visibility = View.GONE
        layoutBottomAction.visibility = View.GONE
        layoutSearchOverlay.visibility = View.GONE // 🔥 HIDE SEARCH OVERLAY
        layoutFormInput.visibility = View.VISIBLE

        // Reset & Fill Data
        tvFormProductName.text = product.name
        tvFormUnitInfo.text = "Satuan Dasar: ${product.unit} (Stok: ${product.stock})"
        
        cbFormBulkMode.isChecked = false
        layoutFormBulkDetails.visibility = View.GONE
        etFormQty.isEnabled = true
        etFormCost.isEnabled = true
        etFormQty.hint = "Jumlah Beli (${product.unit})"
        
        // Cek Unit -> Tampilkan Opsi Kg?
        val unit = product.unit.lowercase()
        if (unit.contains("gr") || unit.contains("ml") || unit.contains("cc")) {
            rbFormKilo.visibility = View.VISIBLE
            rbFormKilo.text = "Konversi ke ${product.unit} (x1000)" // Misal: Kg -> Gr
        } else {
            rbFormKilo.visibility = View.GONE
        }
        
        // 🔥 UPDATE LABEL AGAR TIDAK BINGUNG
        rbFormManual.text = "Input Langsung (${product.unit})"
        rbFormManual.isChecked = true // Reset radio

        if (existingItem != null) {
            etFormQty.text = existingItem.qty.toString()
            etFormCost.text = existingItem.cost.toInt().toString()
        } else {
            etFormQty.text = ""
            etFormCost.text = product.costPrice.toInt().toString()
        }
        etFormIsiPerDus.text = ""
        etFormTotalHarga.text = ""
        tvFormPreview.text = ""
        
        etFormQty.requestFocus()
    }

    private fun setupFormListeners() {
        btnFormCancel.setOnClickListener {
            // Tutup Form -> Balik ke Search/Cart
            layoutFormInput.visibility = View.GONE
            toggleSearchMode(false) // Balik ke Cart
        }

        cbFormBulkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutFormBulkDetails.visibility = View.VISIBLE
                etFormCost.isEnabled = false
                etFormQty.hint = "Jumlah Beli (Pack/Kg/Dus)"
            } else {
                layoutFormBulkDetails.visibility = View.GONE
                etFormCost.isEnabled = true
                etFormQty.hint = "Jumlah Beli (${selectedProductForInput?.unit})"
            }
        }

        rgFormConversion.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbFormKilo) {
                etFormIsiPerDus.text = "1000"
                etFormIsiPerDus.isEnabled = false
                etFormQty.hint = "Jumlah (Kg/Liter)"
            } else {
                etFormIsiPerDus.text = ""
                etFormIsiPerDus.isEnabled = true
                etFormIsiPerDus.hint = "Isi per Dus (Pcs)"
                etFormQty.hint = "Jumlah Beli (Dus)"
            }
        }

        // Calculator Watcher
        val calculatorWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!cbFormBulkMode.isChecked) return
                val product = selectedProductForInput ?: return

                val qtyBeli = etFormQty.text.toString().toIntOrNull() ?: 0
                val isiPerUnit = etFormIsiPerDus.text.toString().toIntOrNull() ?: 1
                val totalUang = etFormTotalHarga.text.toString().toDoubleOrNull() ?: 0.0

                val totalStokMasuk = qtyBeli * isiPerUnit
                val hargaPerUnitDasar = if (totalStokMasuk > 0) totalUang / totalStokMasuk else 0.0

                etFormCost.text = hargaPerUnitDasar.toInt().toString()
                tvFormPreview.text = "Masuk Stok: $totalStokMasuk ${product.unit}\nModal Baru: Rp ${hargaPerUnitDasar.toInt()}/${product.unit}"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        
        // Pake cast ke TextView/EditText aman krn TextWatcher ada di TextView
        etFormQty.addTextChangedListener(calculatorWatcher)
        etFormIsiPerDus.addTextChangedListener(calculatorWatcher)
        etFormTotalHarga.addTextChangedListener(calculatorWatcher)

        btnFormSubmit.setOnClickListener {
            val product = selectedProductForInput ?: return@setOnClickListener
            var finalQty = etFormQty.text.toString().toIntOrNull() ?: 0
            val finalCost = etFormCost.text.toString().toDoubleOrNull() ?: 0.0

            if (cbFormBulkMode.isChecked) {
                val isi = etFormIsiPerDus.text.toString().toIntOrNull() ?: 1
                finalQty *= isi
            }

            if (finalQty > 0) {
                addItemToCart(product, finalQty, finalCost)
                
                // Tutup Form & Search
                layoutFormInput.visibility = View.GONE
                toggleSearchMode(false)
                Toast.makeText(this, "✅ Ditambahkan ke keranjang", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Jumlah tidak boleh 0", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ... (SISA KODE LISTENER, INIT VIEWS, DLL TETAP)



    private fun setupListeners() {
        btnTabInput.setOnClickListener { switchTab("INPUT") }
        btnTabHistory.setOnClickListener { switchTab("HISTORY") }
        btnAddSupplier.setOnClickListener { showAddSupplierDialog() }

        findViewById<View>(R.id.btnScanBarcode).setOnClickListener {
            // ... (Barcode logic sama)
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(ScanActivity::class.java)
            barcodeLauncher.launch(options)
        }
        // 🔥 LISTENER EXPORT EXCEL
        findViewById<Button>(R.id.btnExportExcel).setOnClickListener {
            showExportDateDialog()
        }

        findViewById<Button>(R.id.btnSavePurchase).setOnClickListener { saveTransaction() }

        // 🔥 SEARCH LISTENERS (OVERLAY)
        // 🔥 SEARCH LISTENERS (OVERLAY)
        btnSearchTrigger.setOnClickListener {
            toggleSearchMode(true)
        }
        
        btnBackFromSearch.setOnClickListener {
            toggleSearchMode(false)
        }
        
        etSearchOverlay.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s.toString().lowercase()
                // 🔥 GUNAKAN purchaseableProducts AGAR MENU RACIKAN TIDAK MUNCUL
                val filteredList = purchaseableProducts.filter {
                    it.name.lowercase().contains(keyword) || (it.barcode ?: "").contains(keyword)
                }
                adapterSearch.updateList(filteredList)
            }
        })
    }

    private var purchaseableProducts: List<Product> = emptyList() // Filtered List

    private fun observeData() {
        viewModel.allProducts.observe(this) { products ->
            allProducts = products
            // 🔥 FILTER: Hanya Barang Fisik (trackStock=True) ATAU Bahan Baku (isIngredient=True)
            // Menu racikan / Jasa (trackStock=False & isIngredient=False) TIDAK MASUK SINI
            purchaseableProducts = products.filter { it.trackStock || it.isIngredient }
        }
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
            openInputForm(product) // 🔥 PANGGIL FULL FORM
        } else {
            Toast.makeText(this, "Barang tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }

    // --- REMOVED showAddToCartDialog (OLD POPUP) ---

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



    // --- SAVE & OTHERS ---
    private fun saveTransaction() {
        if (purchaseList.isEmpty()) { Toast.makeText(this, "Keranjang kosong!", Toast.LENGTH_SHORT).show(); return }
        
        // 🔥 VALIDASI SUPPLIER WAJIB DIPILIH
        if (spSupplier.selectedItemPosition == 0) {
            Toast.makeText(this, "⚠️ Harap pilih Supplier terlebih dahulu!", Toast.LENGTH_SHORT).show()
            spSupplier.performClick() // Buka spinner otomatis biar user sadar
            return
        }
        val supplierName = spSupplier.selectedItem.toString()

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
                        totalCost = item.qty * item.cost, type = "IN",
                        invoiceNumber = etInvoiceNumber.text.toString() // 🔥 SIMPAN NOMOR FAKTUR
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

    // --- LOGIKA EXPORT EXCEL (CSV) ---
    private fun showExportDateDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val tvInfo = TextView(context).apply { text = "Pilih Rentang Tanggal:"; textSize = 16f; setPadding(0,0,0,20) }
        val btnStartDate = Button(context).apply { text = "Tanggal Awal" }
        val btnEndDate = Button(context).apply { text = "Tanggal Akhir" }

        var startMillis = System.currentTimeMillis()
        var endMillis = System.currentTimeMillis()

        btnStartDate.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(context, { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                startMillis = cal.timeInMillis
                btnStartDate.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnEndDate.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(context, { _, y, m, d ->
                cal.set(y, m, d, 23, 59, 59)
                endMillis = cal.timeInMillis
                btnEndDate.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        layout.addView(tvInfo); layout.addView(btnStartDate); layout.addView(btnEndDate)

        AlertDialog.Builder(context)
            .setTitle("Export Laporan Belanja")
            .setView(layout)
            .setPositiveButton("DOWNLOAD") { _, _ ->
                processExport(startMillis, endMillis)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun processExport(start: Long, end: Long) {
        // Panggil ViewModel untuk ambil data (Harus dibuat di ViewModel dulu, tapi kita inject langsung ke DAO via repository klo bisa, atau pakai direct Coroutine di sini sementara)
        // Karena ViewModel belum ada fungsi getLogsByDate, kita tambahkan via helper atau direct call. 
        // Biar cepat & aman, kita luncurkan coroutine dari sini.
        
        Toast.makeText(this, "Sedang memproses...", Toast.LENGTH_SHORT).show()

        // GUNAKAN lifecycleScope.launch (Standard)
        lifecycleScope.launch {
            // Akses DAO via Database instance (bypass ViewModel utk kecepatan dev)
            val db = com.sysdos.kasirpintar.data.AppDatabase.getDatabase(applicationContext)
            val logs = db.stockLogDao().getLogsByDateRangeAndType(start, end, "IN")

            if (logs.isEmpty()) {
                Toast.makeText(this@PurchaseActivity, "Tidak ada data pada tanggal tsb", Toast.LENGTH_SHORT).show()
            } else {
                generateCSV(logs, start, end)
            }
        }
    }

    private fun generateCSV(logs: List<StockLog>, start: Long, end: Long) {
        val sb = StringBuilder()
        sb.append("Tanggal,Nomor Faktur,Supplier,Nama Barang,Qty,Harga Satuan,Total\n")

        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        
        for (log in logs) {
            val date = dateFormat.format(Date(log.timestamp))
            // Escape koma dengan tanda kutip jika perlu
            val namaBarang = log.productName.replace(",", " ")
            val supplier = log.supplierName.replace(",", " ")
            
            sb.append("$date,${log.invoiceNumber},$supplier,$namaBarang,${log.quantity},${log.costPrice.toLong()},${log.totalCost.toLong()}\n")
        }

        val fileName = "Laporan_Belanja_${System.currentTimeMillis()}.csv"
        val file = java.io.File(getExternalFilesDir(null), fileName)
        
        try {
            file.writeText(sb.toString())
            shareFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal membuat file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFile(file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
        intent.type = "text/csv" // Atau "application/vnd.ms-excel"
        intent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        startActivity(android.content.Intent.createChooser(intent, "Kirim Laporan Ke..."))
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
            holder.tvCalc.text = "${item.qty} ${item.product.unit} x ${formatRupiah(item.cost)} = ${formatRupiah(item.qty * item.cost)}"

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
            
            // 🔥 TAMPILKAN INVOICE NUMBER JIKA ADA
            val displayTitle = if (item.invoiceNumber.isNotEmpty()) item.invoiceNumber else item.supplierName
            
            holder.tvHeader.text = "FAKTUR: $displayTitle"; holder.tvHeader.setTextColor(Color.parseColor("#1976D2")); holder.tvHeader.setTypeface(null, android.graphics.Typeface.BOLD)
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
            holder.tvInfo.text = "Stok: ${product.stock} ${product.unit} | Modal: Rp ${product.costPrice.toInt()}"
            holder.itemView.setOnClickListener { onItemClick(product) }
        }
        override fun getItemCount(): Int = items.size
    }
    override fun onBackPressed() {
        // Jika sedang mode pencarian, tutup dulu search-nya
        if (layoutSearchOverlay.visibility == View.VISIBLE) {
            toggleSearchMode(false)
            etSearchOverlay.setText("")
            etSearchOverlay.clearFocus()
        } else {
            super.onBackPressed()
        }
    }
}