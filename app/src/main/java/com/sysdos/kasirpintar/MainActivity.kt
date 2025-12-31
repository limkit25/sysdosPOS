package com.sysdos.kasirpintar

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.sysdos.kasirpintar.data.model.Category
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.utils.PrinterHelper
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var productAdapter: ProductAdapter

    // UI Elements di Layar Utama
    private lateinit var svSearch: androidx.appcompat.widget.SearchView
    private lateinit var llCategories: LinearLayout
    private lateinit var tvCartCount: TextView
    private lateinit var tvCartTotal: TextView
    private lateinit var btnCheckout: Button

    private var fullList: List<Product> = ArrayList()
    private var selectedCategory: String = "Semua"

    // Printer
    private lateinit var printerHelper: PrinterHelper
    private var selectedPrinterAddress: String? = null

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            viewModel.scanAndAddToCart(
                barcode = result.contents,
                onResult = { product ->
                    if (product == null) Toast.makeText(this, "Barang tidak ditemukan", Toast.LENGTH_SHORT).show()
                },
                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        printerHelper = PrinterHelper(this)
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 1. AUTO CONNECT PRINTER
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        selectedPrinterAddress = prefs.getString("printer_mac", null)
        if (selectedPrinterAddress != null) connectPrinterSilent(selectedPrinterAddress!!)

        // 2. BIND VIEW LAYAR UTAMA
        svSearch = findViewById(R.id.svSearch)
        llCategories = findViewById(R.id.llCategories)
        tvCartCount = findViewById(R.id.tvCartCount)
        tvCartTotal = findViewById(R.id.tvCartTotal)
        btnCheckout = findViewById(R.id.btnCheckout)
        val btnScan = findViewById<ImageButton>(R.id.btnScan)
        val rvProducts = findViewById<RecyclerView>(R.id.rvProducts)

        // Setup RecyclerView
        productAdapter = ProductAdapter(
            onItemClick = { product ->
                viewModel.addToCart(product) { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            },
            onItemLongClick = { product -> showCartActionDialog(product) }
        )
        rvProducts.layoutManager = GridLayoutManager(this, 2)
        rvProducts.adapter = productAdapter

        // 3. OBSERVE DATA
        viewModel.allProducts.observe(this) { products ->
            fullList = products
            filterData()
        }
        viewModel.allCategories.observe(this) { categories ->
            setupCategoryButtons(categories ?: emptyList())
        }
        viewModel.cart.observe(this) {
            productAdapter.updateCartCounts(it)
            val itemCount = it.sumOf { item -> item.stock }
            tvCartCount.text = "$itemCount Item"
        }
        viewModel.totalPrice.observe(this) {
            tvCartTotal.text = formatRupiah(it)
        }

        // 4. LOGIKA SEARCH & FILTER
        svSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterData()
                return true
            }
        })

        // 5. TOMBOL AKSI
        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setPrompt("Scan Barcode")
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
            barcodeLauncher.launch(options)
        }
        val bottomBar = findViewById<androidx.cardview.widget.CardView>(R.id.bottomBar)
        bottomBar.setOnClickListener { showCartPreview() }

        btnCheckout.setOnClickListener {
            if (viewModel.totalPrice.value == 0.0) {
                Toast.makeText(this, "Keranjang Kosong!", Toast.LENGTH_SHORT).show()
            } else {
                showCartPreview() // BUKA POPUP PEMBAYARAN
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    // --- POPUP PEMBAYARAN (DIALOG) ---
    private fun showPaymentDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        // Bind View Dialog
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvDialogTotal)
        val etDiscount = dialogView.findViewById<EditText>(R.id.etDialogDiscount)
        val cbTax = dialogView.findViewById<CheckBox>(R.id.cbDialogTax)
        val spPayment = dialogView.findViewById<Spinner>(R.id.spDialogPayment)
        val layoutCash = dialogView.findViewById<LinearLayout>(R.id.layoutDialogCash)
        val etCash = dialogView.findViewById<EditText>(R.id.etDialogCash)
        val tvChange = dialogView.findViewById<TextView>(R.id.tvDialogChange)
        val btnPay = dialogView.findViewById<Button>(R.id.btnDialogPay)

        var currentTotal = viewModel.totalPrice.value ?: 0.0
        var finalAmount = currentTotal
        var diskon = 0.0
        var pajak = 0.0

        // Fungsi Hitung Realtime di Dialog
        fun calculate() {
            val discStr = etDiscount.text.toString()
            diskon = if (discStr.isNotEmpty()) discStr.toDouble() else 0.0

            var afterDisc = currentTotal - diskon
            if (afterDisc < 0) afterDisc = 0.0

            pajak = if (cbTax.isChecked) afterDisc * 0.10 else 0.0
            finalAmount = afterDisc + pajak

            tvTotal.text = formatRupiah(finalAmount)

            // Hitung Kembalian
            if (layoutCash.visibility == View.VISIBLE) {
                val cashStr = etCash.text.toString()
                val cash = if (cashStr.isNotEmpty()) cashStr.toDouble() else 0.0
                val change = cash - finalAmount

                if (change >= 0) {
                    tvChange.text = "Kembali: ${formatRupiah(change)}"
                    tvChange.setTextColor(Color.parseColor("#388E3C"))
                    btnPay.isEnabled = true
                } else {
                    tvChange.text = "Kurang: ${formatRupiah(Math.abs(change))}"
                    tvChange.setTextColor(Color.RED)
                    // btnPay.isEnabled = false // Opsional: Matikan tombol jika kurang
                }
            } else {
                btnPay.isEnabled = true
            }
        }


        // Listeners
        spPayment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (spPayment.selectedItem.toString().contains("Tunai")) {
                    layoutCash.visibility = View.VISIBLE
                    etCash.requestFocus()
                } else {
                    layoutCash.visibility = View.GONE
                }
                calculate()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        etDiscount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { calculate() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etCash.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { calculate() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        cbTax.setOnCheckedChangeListener { _, _ -> calculate() }

        // Initial Calculation
        calculate()

        // KLIK BAYAR
        btnPay.setOnClickListener {
            val paymentMethod = spPayment.selectedItem.toString()
            val cashStr = etCash.text.toString()
            var cashReceived = if (cashStr.isNotEmpty()) cashStr.toDouble() else 0.0

            if (!paymentMethod.contains("Tunai")) {
                cashReceived = finalAmount
            } else if (cashReceived < finalAmount) {
                Toast.makeText(this, "Uang Kurang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val changeAmount = cashReceived - finalAmount

            viewModel.checkout(
                subtotal = currentTotal,
                discount = diskon,
                tax = pajak,
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                changeAmount = changeAmount
            ) { transaction ->
                if (transaction != null) {
                    Toast.makeText(this, "✅ Transaksi Berhasil!", Toast.LENGTH_SHORT).show()
                    if (selectedPrinterAddress != null) printStruk(transaction)

                    dialog.dismiss()
                    resetUI()
                } else {
                    Toast.makeText(this, "Gagal Transaksi", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }


    private fun resetUI() {
        svSearch.setQuery("", false)
        selectedCategory = "Semua"
        val allCats = viewModel.allCategories.value ?: emptyList()
        setupCategoryButtons(allCats)
        filterData()
    }

    // --- HELPER LAINNYA ---
    private fun setupCategoryButtons(categories: List<Category>) {
        llCategories.removeAllViews()
        addCategoryButton("Semua")
        for (cat in categories) addCategoryButton(cat.name)
    }

    private fun addCategoryButton(categoryName: String) {
        val btn = TextView(this)
        btn.text = categoryName
        btn.setPadding(40, 20, 40, 20)
        btn.gravity = Gravity.CENTER

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 16, 0)
        btn.layoutParams = params

        // --- LOGIKA WARNA TOMBOL ---
        if (selectedCategory == categoryName) {
            // JIKA DIPILIH (AKTIF) -> Teks Biru, Background Putih, Tebal
            btn.setBackgroundResource(R.drawable.bg_rounded_white)
            btn.background.setTint(Color.WHITE) // Tetap Putih
            btn.setTextColor(Color.parseColor("#1976D2")) // Teks jadi Biru
            btn.setTypeface(null, android.graphics.Typeface.BOLD) // Huruf Tebal

            // Opsional: Tambahkan Border/Garis pinggir (Elevation) biar kelihatan beda
            btn.elevation = 8f
        } else {
            // JIKA TIDAK DIPILIH -> Teks Abu, Background Putih, Biasa
            btn.setBackgroundResource(R.drawable.bg_rounded_white)
            btn.background.setTint(Color.WHITE) // Tetap Putih
            btn.setTextColor(Color.parseColor("#757575")) // Teks Abu
            btn.setTypeface(null, android.graphics.Typeface.NORMAL) // Huruf Biasa
            btn.elevation = 0f
        }

        btn.setOnClickListener {
            selectedCategory = categoryName
            // Refresh tombol biar warnanya berubah
            val allCats = viewModel.allCategories.value ?: emptyList()
            setupCategoryButtons(allCats)
            // Filter Produk
            filterData()
        }

        llCategories.addView(btn)
    }

    private fun filterData() {
        val query = svSearch.query.toString().lowercase()
        val filtered = fullList.filter { product ->
            val matchName = product.name.lowercase().contains(query)
            val matchCategory = if (selectedCategory == "Semua") true else product.category == selectedCategory
            matchName && matchCategory
        }
        productAdapter.submitList(filtered)
    }

    private fun showCartActionDialog(product: Product) {
        val options = arrayOf("➖ Kurangi 1", "❌ Batal Item")
        AlertDialog.Builder(this)
            .setTitle(product.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.decreaseCartItem(product)
                    1 -> viewModel.removeCartItem(product)
                }
            }
            .show()
    }

    private fun formatRupiah(number: Double): String = String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')

    private fun connectPrinterSilent(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }
        printerHelper.connectPrinter(address) {}
    }
    private fun showCartPreview() {
        val currentCart = viewModel.cart.value ?: emptyList()
        if (currentCart.isEmpty()) {
            Toast.makeText(this, "Keranjang kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cart_preview, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val container = dialogView.findViewById<LinearLayout>(R.id.llCartItems)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvPreviewTotal)
        val btnAddMore = dialogView.findViewById<Button>(R.id.btnAddMore)
        val btnProceed = dialogView.findViewById<Button>(R.id.btnProceedToPay)

        val currentTotal = viewModel.totalPrice.value ?: 0.0
        tvTotal.text = formatRupiah(currentTotal)

        for (item in currentCart) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_cart_row, null)

            val tvName = itemView.findViewById<TextView>(R.id.tvCartName)
            val tvPrice = itemView.findViewById<TextView>(R.id.tvCartPrice)
            val tvQty = itemView.findViewById<TextView>(R.id.tvCartQty)
            val tvSubtotal = itemView.findViewById<TextView>(R.id.tvCartSubtotal)

            val btnMinus = itemView.findViewById<View>(R.id.btnMinus)
            val btnPlus = itemView.findViewById<View>(R.id.btnPlus)
            val btnRemove = itemView.findViewById<View>(R.id.btnRemoveItem)

            tvName.text = item.name
            tvPrice.text = "@ ${formatRupiah(item.price)}"
            tvQty.text = item.stock.toString()
            tvSubtotal.text = formatRupiah(item.price * item.stock)

            // --- PERBAIKAN TOMBOL PLUS (+) ---
            btnPlus.setOnClickListener {
                // Cari Data Asli di Master List (fullList) untuk tahu Stok Gudang
                val originalProduct = fullList.find { it.id == item.id }

                if (originalProduct != null) {
                    // Pakai originalProduct yang stoknya masih utuh (gudang)
                    viewModel.addToCart(originalProduct) { msg ->
                        // Kalau stok habis, munculkan pesan
                        if (msg.contains("Stok", true)) {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    dialog.dismiss() // Tutup sebentar
                    showCartPreview() // Buka lagi (Refresh)
                }
            }

            // --- TOMBOL MINUS (-) ---
            btnMinus.setOnClickListener {
                viewModel.decreaseCartItem(item)
                dialog.dismiss()
                showCartPreview()
            }
            // Set icon minus jika belum ada
            val imgMinus = btnMinus as? ImageButton
            imgMinus?.setImageResource(android.R.drawable.btn_minus)

            // --- TOMBOL SAMPAH ---
            btnRemove.setOnClickListener {
                viewModel.removeCartItem(item)
                dialog.dismiss()
                showCartPreview()
                Toast.makeText(this, "${item.name} dihapus", Toast.LENGTH_SHORT).show()
            }

            container.addView(itemView)
        }

        btnAddMore.setOnClickListener { dialog.dismiss() }

        btnProceed.setOnClickListener {
            dialog.dismiss()
            showPaymentDialog()
        }

        dialog.show()
    }
    private fun printStruk(trx: com.sysdos.kasirpintar.data.model.Transaction) {
        try {
            val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
            val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
            val storeName = prefs.getString("name", "WARUNG BERKAH POS")
            val storeAddress = prefs.getString("address", "Indonesia")
            val storePhone = prefs.getString("phone", "-")
            val kasirName = session.getString("username", "Admin")
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

            printerHelper.setAlign(1)
            printerHelper.setBold(true)
            printerHelper.printText("$storeName\n")
            printerHelper.setBold(false)
            printerHelper.printText("$storeAddress\nTelp: $storePhone\n--------------------------------\n")
            printerHelper.setAlign(0)
            printerHelper.printText("ID : #${trx.id}\nTgl: ${dateFormat.format(java.util.Date(trx.timestamp))}\nKasir: $kasirName\n--------------------------------\n")
            printerHelper.printText(trx.itemsSummary)
            printerHelper.printText("--------------------------------\n")
            printerHelper.printText(formatRow("Subtotal", formatRupiah(trx.subtotal)) + "\n")
            if (trx.discount > 0) printerHelper.printText(formatRow("Diskon", "-" + formatRupiah(trx.discount)) + "\n")
            if (trx.tax > 0) printerHelper.printText(formatRow("Pajak 10%", "+" + formatRupiah(trx.tax)) + "\n")
            printerHelper.printText("--------------------------------\n")
            printerHelper.setBold(true)
            printerHelper.printText(formatRow("TOTAL", formatRupiah(trx.totalAmount)) + "\n")
            printerHelper.setBold(false)
            if (trx.paymentMethod.contains("Tunai")) {
                printerHelper.printText(formatRow("Tunai", formatRupiah(trx.cashReceived)) + "\n")
                printerHelper.printText(formatRow("Kembali", formatRupiah(trx.changeAmount)) + "\n")
            } else {
                printerHelper.printText(formatRow("Bayar", trx.paymentMethod) + "\n")
            }
            printerHelper.setAlign(1)
            printerHelper.printText("--------------------------------\n${prefs.getString("email", "Terima Kasih!")}\n")
            printerHelper.feedPaper()
        } catch (e: Exception) {}
    }

    private fun formatRow(kiri: String, kanan: String): String {
        val maxChars = 30
        val maxKiri = maxChars - kanan.length - 1
        val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri
        val sisa = maxChars - textKiri.length - kanan.length
        return "$textKiri${" ".repeat(if (sisa > 0) sisa else 1)}$kanan"
    }

    override fun onDestroy() {
        super.onDestroy()
        try { printerHelper.close() } catch (e: Exception) {}
    }
}