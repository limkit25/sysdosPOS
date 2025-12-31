package com.sysdos.kasirpintar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.utils.PrinterHelper
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var productAdapter: ProductAdapter

    // UI Baru
    private lateinit var tvCartCount: TextView
    private lateinit var tvCartTotal: TextView
    private lateinit var btnCheckout: Button
    private lateinit var svSearch: SearchView

    // Tombol Filter
    private lateinit var btnAll: Button
    private lateinit var btnFood: Button
    private lateinit var btnDrink: Button
    private lateinit var btnSnack: Button

    private var fullList: List<Product> = ArrayList()
    private var currentCategory = "Semua"

    // Printer & Scanner
    private lateinit var printerHelper: PrinterHelper
    private var selectedPrinterAddress: String? = null

    // Launcher Scanner
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

        // 2. BIND UI
        tvCartCount = findViewById(R.id.tvCartCount)
        tvCartTotal = findViewById(R.id.tvCartTotal)
        btnCheckout = findViewById(R.id.btnCheckout)
        svSearch = findViewById(R.id.svSearch)

        btnAll = findViewById(R.id.btnCatAll)
        btnFood = findViewById(R.id.btnCatFood)
        btnDrink = findViewById(R.id.btnCatDrink)
        btnSnack = findViewById(R.id.btnCatSnack)

        val btnScan = findViewById<ImageButton>(R.id.btnScan)
        val rvProducts = findViewById<RecyclerView>(R.id.rvProducts)

        // 3. SETUP ADAPTER (GRID 3 KOLOM)
        productAdapter = ProductAdapter(
            onItemClick = { product ->
                viewModel.addToCart(product) { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            },
            onItemLongClick = { product -> showCartActionDialog(product) }
        )

        rvProducts.layoutManager = GridLayoutManager(this, 3) // Grid 3 Kolom
        rvProducts.adapter = productAdapter

        // 4. OBSERVERS
        viewModel.allProducts.observe(this) { products ->
            fullList = products
            filterList(svSearch.query.toString())
        }

        viewModel.cart.observe(this) { cartList ->
            productAdapter.updateCartCounts(cartList)
            updateBottomBar()
        }

        viewModel.totalPrice.observe(this) { updateBottomBar() }

        // 5. SEARCH LISTENER
        svSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        // 6. FILTER BUTTONS
        setupFilterButton(btnAll, "Semua")
        setupFilterButton(btnFood, "Makanan")
        setupFilterButton(btnDrink, "Minuman")
        setupFilterButton(btnSnack, "Snack") // Pastikan ejaan sesuai database (Case Insensitive)

        // 7. TOMBOL BAYAR -> DIALOG
        btnCheckout.setOnClickListener {
            if (viewModel.totalPrice.value == 0.0) {
                Toast.makeText(this, "Keranjang Kosong!", Toast.LENGTH_SHORT).show()
            } else {
                showPaymentDialog()
            }
        }

        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setPrompt("Scan Barcode")
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
            barcodeLauncher.launch(options)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    // --- LOGIKA FILTER KATEGORI (VERSI FIX WARNA) ---
    private fun setupFilterButton(btn: Button, category: String) {
        btn.setOnClickListener {
            currentCategory = category
            filterList(svSearch.query.toString())

            // Ubah Warna Tombol agar user tau mana yang aktif
            val buttons = listOf(btnAll, btnFood, btnDrink, btnSnack)
            buttons.forEach {
                // Tombol TIDAK aktif (Putih, Teks Biru)
                it.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.white)
                it.setTextColor(Color.parseColor("#1976D2"))
            }

            // Tombol AKTIF (Biru, Teks Putih)
            // Ganti R.color.design_default_color_primary dengan kode warna manual
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1976D2"))
            btn.setTextColor(Color.WHITE)
        }
    }

    private fun filterList(query: String?) {
        val searchQuery = query?.lowercase() ?: ""

        val filtered = fullList.filter { product ->
            val matchName = product.name.lowercase().contains(searchQuery)
            // Logic Filter Kategori
            val matchCat = if (currentCategory == "Semua") true else product.category.equals(currentCategory, ignoreCase = true)
            matchName && matchCat
        }
        productAdapter.submitList(filtered)
    }

    // --- UPDATE BOTTOM BAR ---
    private fun updateBottomBar() {
        val cartList = viewModel.cart.value ?: emptyList()
        val totalItems = cartList.sumOf { it.stock } // stock di cart melambangkan quantity
        val totalPrice = viewModel.totalPrice.value ?: 0.0

        tvCartCount.text = "$totalItems Item"
        tvCartTotal.text = formatRupiah(totalPrice)
    }

    // --- DIALOG PEMBAYARAN (FIX: LENGKAP DENGAN QRIS & TRANSFER) ---
    private fun showPaymentDialog() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        dialog.setContentView(view)

        // Bind View
        val tvTotal = view.findViewById<TextView>(R.id.tvDialogTotal)
        val etCash = view.findViewById<EditText>(R.id.etDialogCash)
        val etDiscount = view.findViewById<EditText>(R.id.etDialogDiscount)
        val tvChange = view.findViewById<TextView>(R.id.tvDialogChange)
        val btnPay = view.findViewById<Button>(R.id.btnDialogPay)
        val cbTax = view.findViewById<CheckBox>(R.id.cbDialogTax)

        // Komponen Baru
        val rgPayment = view.findViewById<RadioGroup>(R.id.rgPaymentMethod)
        val layoutTunai = view.findViewById<LinearLayout>(R.id.layoutTunai)
        val rbTunai = view.findViewById<RadioButton>(R.id.rbTunai)
        val rbQris = view.findViewById<RadioButton>(R.id.rbQris)
        val rbTransfer = view.findViewById<RadioButton>(R.id.rbTransfer)

        // Variabel penampung
        var metodeBayar = "Tunai"

        fun hitung() {
            val subtotal = viewModel.totalPrice.value ?: 0.0
            val disc = etDiscount.text.toString().toDoubleOrNull() ?: 0.0
            val tax = if (cbTax.isChecked) (subtotal - disc) * 0.1 else 0.0
            val grandTotal = (subtotal - disc) + tax

            tvTotal.text = formatRupiah(grandTotal)

            // LOGIKA BEDAKAN TUNAI vs NON-TUNAI
            if (metodeBayar == "Tunai") {
                // Mode Tunai: Harus hitung kembalian
                layoutTunai.visibility = View.VISIBLE
                val cash = etCash.text.toString().toDoubleOrNull() ?: 0.0
                val change = cash - grandTotal

                if (change >= 0) {
                    tvChange.text = "Kembali: ${formatRupiah(change)}"
                    tvChange.setTextColor(Color.parseColor("#388E3C")) // Hijau
                    btnPay.isEnabled = true
                } else {
                    tvChange.text = "Kurang: ${formatRupiah(Math.abs(change))}"
                    tvChange.setTextColor(Color.RED)
                    btnPay.isEnabled = false // Uang kurang, gak bisa bayar
                }
            } else {
                // Mode QRIS/Transfer: Bayar PAS, langsung bisa klik
                layoutTunai.visibility = View.GONE
                btnPay.isEnabled = true
            }

            // Simpan data di tag tombol untuk diambil saat klik
            btnPay.tag = Triple(disc, tax, grandTotal)
        }

        // Listener Ganti Metode Bayar
        rgPayment.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbTunai -> metodeBayar = "Tunai"
                R.id.rbQris -> metodeBayar = "QRIS"
                R.id.rbTransfer -> metodeBayar = "Transfer"
            }
            hitung() // Hitung ulang status tombol
        }

        // Listener Input Angka
        etCash.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = hitung()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        etDiscount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = hitung()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        cbTax.setOnCheckedChangeListener { _, _ -> hitung() }

        hitung() // Init awal

        // PROSES BAYAR
        btnPay.setOnClickListener {
            val (disc, tax, grandTotal) = btnPay.tag as Triple<Double, Double, Double>

            var cashReceived = 0.0
            var changeAmount = 0.0

            if (metodeBayar == "Tunai") {
                cashReceived = etCash.text.toString().toDoubleOrNull() ?: 0.0
                changeAmount = cashReceived - grandTotal
            } else {
                // Kalau QRIS/Transfer, anggap uang pas
                cashReceived = grandTotal
                changeAmount = 0.0
            }

            // Panggil ViewModel
            viewModel.checkout(
                subtotal = viewModel.totalPrice.value ?: 0.0,
                discount = disc,
                tax = tax,
                paymentMethod = metodeBayar, // Kirim Tunai/QRIS/Transfer
                cashReceived = cashReceived,
                changeAmount = changeAmount
            ) { trx ->
                if (trx != null) {
                    Toast.makeText(this, "✅ Lunas via $metodeBayar!", Toast.LENGTH_SHORT).show()
                    if (selectedPrinterAddress != null) printStruk(trx)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    // --- HELPER LAINNYA ---
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

    private fun formatRupiah(number: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')
    }

    private fun formatRow(kiri: String, kanan: String): String {
        val maxChars = 30
        val maxKiri = maxChars - kanan.length - 1
        val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri
        val textKanan = kanan
        val sisaSpasi = maxChars - textKiri.length - textKanan.length
        val spasi = if (sisaSpasi > 0) " ".repeat(sisaSpasi) else " "
        return "$textKiri$spasi$textKanan"
    }

    private fun connectPrinterSilent(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }
        printerHelper.connectPrinter(address) { }
    }

    // --- FUNGSI PRINT STRUK (VERSI LENGKAP) ---
    private fun printStruk(trx: com.sysdos.kasirpintar.data.model.Transaction) {
        try {
            val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
            val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)

            val storeName = prefs.getString("name", "WARUNG BERKAH POS")
            val storeAddress = prefs.getString("address", "Indonesia")
            val storePhone = prefs.getString("phone", "-")
            val storeEmail = prefs.getString("email", "Terima Kasih!")
            val kasirName = session.getString("username", "Admin")

            val dateFormat = java.text.SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(java.util.Date(trx.timestamp))

            printerHelper.setAlign(1) // Center
            printerHelper.setBold(true)
            printerHelper.printText("$storeName\n")
            printerHelper.setBold(false)
            printerHelper.printText("$storeAddress\n")
            printerHelper.printText("Telp: $storePhone\n")
            printerHelper.printText("--------------------------------\n")

            printerHelper.setAlign(0) // Left
            printerHelper.printText("ID : #${trx.id}\n")
            printerHelper.printText("Tgl: $dateStr\n")
            printerHelper.printText("Kasir: $kasirName\n")
            printerHelper.printText("--------------------------------\n")

            printerHelper.printText(trx.itemsSummary)
            printerHelper.printText("--------------------------------\n")

            // --- [BAGIAN INI YANG KEMARIN HILANG] ---
            printerHelper.printText(formatRow("Subtotal", formatRupiah(trx.subtotal)) + "\n")

            // Cek Diskon
            if (trx.discount > 0) {
                printerHelper.printText(formatRow("Diskon", "-" + formatRupiah(trx.discount)) + "\n")
            }

            // Cek Pajak
            if (trx.tax > 0) {
                printerHelper.printText(formatRow("Pajak 10%", "+" + formatRupiah(trx.tax)) + "\n")
            }

            printerHelper.printText("--------------------------------\n")

            // Total Akhir
            printerHelper.setBold(true)
            printerHelper.printText(formatRow("TOTAL", formatRupiah(trx.totalAmount)) + "\n")
            printerHelper.setBold(false)

            // Info Pembayaran (Tunai / QRIS / Transfer)
            if (trx.paymentMethod == "Tunai") {
                printerHelper.printText(formatRow("Tunai", formatRupiah(trx.cashReceived)) + "\n")
                printerHelper.printText(formatRow("Kembali", formatRupiah(trx.changeAmount)) + "\n")
            } else {
                // Kalau QRIS / Transfer
                printerHelper.printText(formatRow("Bayar", trx.paymentMethod) + "\n")
            }

            printerHelper.setAlign(1)
            printerHelper.printText("--------------------------------\n")
            printerHelper.printText("$storeEmail\n")
            printerHelper.feedPaper()

        } catch (e: Exception) {
            Toast.makeText(this, "Error Print: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { printerHelper.close() } catch (e: Exception) {}
    }
}