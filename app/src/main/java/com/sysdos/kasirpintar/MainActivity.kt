package com.sysdos.kasirpintar

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.utils.PrinterHelper
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var productAdapter: ProductAdapter

    // --- [BARU 1] VARIABEL SEARCH ---
    private lateinit var svSearch: androidx.appcompat.widget.SearchView // View Pencarian
    private var fullList: List<Product> = ArrayList() // Penampung Data MASTER (Semua Barang)

    // UI Elements Existing
    private lateinit var tvTotalBill: TextView
    private lateinit var etDiscount: EditText
    private lateinit var cbTax: CheckBox
    private lateinit var spPaymentMethod: Spinner
    private lateinit var layoutCash: LinearLayout
    private lateinit var etCashReceived: EditText
    private lateinit var tvChange: TextView

    // Keuangan
    private var finalTotalBayar: Double = 0.0
    private var nilaiDiskon: Double = 0.0
    private var nilaiPajak: Double = 0.0
    private var uangDiterima: Double = 0.0
    private var uangKembali: Double = 0.0

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
        if (selectedPrinterAddress != null) {
            connectPrinterSilent(selectedPrinterAddress!!)
        }

        // 2. BIND UI
        // --- [BARU 2] Bind ID Search View ---
        svSearch = findViewById(R.id.svSearch) // Pastikan di XML sudah ada ID ini

        tvTotalBill = findViewById(R.id.tvTotalBill)
        etDiscount = findViewById(R.id.etDiscount)
        cbTax = findViewById(R.id.cbTax)
        spPaymentMethod = findViewById(R.id.spPaymentMethod)
        layoutCash = findViewById(R.id.layoutCash)
        etCashReceived = findViewById(R.id.etCashReceived)
        tvChange = findViewById(R.id.tvChange)

        val btnScan = findViewById<Button>(R.id.btnScan)
        val btnCheckout = findViewById<Button>(R.id.btnCheckout)
        val rvProducts = findViewById<RecyclerView>(R.id.rvProducts)

        // 3. SETUP RECYCLER VIEW
        productAdapter = ProductAdapter(
            onItemClick = { product ->
                viewModel.addToCart(product) { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            },
            onItemLongClick = { product ->
                showCartActionDialog(product)
            }
        )

        rvProducts.layoutManager = GridLayoutManager(this, 2)
        rvProducts.adapter = productAdapter

        // 4. OBSERVERS (MODIFIKASI UNTUK SEARCH)
        viewModel.allProducts.observe(this) { products ->
            // Simpan ke Full List (Master Data)
            fullList = products

            // Cek apakah user sedang mencari sesuatu?
            val query = svSearch.query.toString()
            if (query.isNotEmpty()) {
                filterList(query) // Kalau ada ketikan, filter ulang
            } else {
                productAdapter.submitList(products) // Kalau kosong, tampilkan semua
            }
        }

        viewModel.cart.observe(this) { productAdapter.updateCartCounts(it) }
        viewModel.totalPrice.observe(this) { hitungTotalAkhir() }

        // --- [BARU 3] LOGIKA KETIK PENCARIAN ---
        svSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText) // Filter setiap ketik huruf
                return true
            }
        })

        // 5. LISTENER SCANNER
        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setPrompt("Scan Barcode")
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
            barcodeLauncher.launch(options)
        }

        // 6. LOGIKA PEMBAYARAN
        spPaymentMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (spPaymentMethod.selectedItem.toString().contains("Tunai")) {
                    layoutCash.visibility = View.VISIBLE
                    etCashReceived.requestFocus()
                } else {
                    layoutCash.visibility = View.GONE
                    uangDiterima = 0.0
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Hitung Kembalian
        etCashReceived.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                uangDiterima = if (text.isNotEmpty()) text.toDouble() else 0.0
                uangKembali = uangDiterima - finalTotalBayar
                if (uangKembali >= 0) {
                    tvChange.text = "Kembali: ${formatRupiah(uangKembali)}"
                    tvChange.setTextColor(android.graphics.Color.parseColor("#388E3C"))
                } else {
                    tvChange.text = "Kurang: ${formatRupiah(Math.abs(uangKembali))}"
                    tvChange.setTextColor(android.graphics.Color.RED)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etDiscount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { hitungTotalAkhir() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        cbTax.setOnCheckedChangeListener { _, _ -> hitungTotalAkhir() }

        // 7. CHECKOUT
        btnCheckout.setOnClickListener {
            if (viewModel.totalPrice.value == 0.0) {
                Toast.makeText(this, "Keranjang Kosong!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val paymentMethod = spPaymentMethod.selectedItem.toString()
            if (paymentMethod.contains("Tunai") && uangDiterima < finalTotalBayar) {
                Toast.makeText(this, "❌ Uang Kurang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!paymentMethod.contains("Tunai")) {
                uangDiterima = finalTotalBayar
                uangKembali = 0.0
            }

            viewModel.checkout(
                subtotal = viewModel.totalPrice.value ?: 0.0,
                discount = nilaiDiskon,
                tax = nilaiPajak,
                paymentMethod = paymentMethod,
                cashReceived = uangDiterima,
                changeAmount = uangKembali
            ) { transaction ->
                if (transaction != null) {
                    Toast.makeText(this, "✅ Transaksi Sukses!", Toast.LENGTH_SHORT).show()
                    if (selectedPrinterAddress != null) printStruk(transaction)
                    else Toast.makeText(this, "⚠️ Printer belum disetting", Toast.LENGTH_SHORT).show()
                    resetUI()

                    // Reset Search juga
                    svSearch.setQuery("", false)
                    svSearch.clearFocus()
                } else {
                    Toast.makeText(this, "❌ Gagal Transaksi", Toast.LENGTH_SHORT).show()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    // --- [BARU 4] FUNGSI FILTER LIST ---
    private fun filterList(query: String?) {
        if (query != null) {
            val filtered = fullList.filter {
                it.name.lowercase().contains(query.lowercase())
            }
            // Kirim list yang sudah disaring ke adapter
            productAdapter.submitList(filtered)
        }
    }

    private fun resetUI() {
        etDiscount.text.clear()
        etCashReceived.text.clear()
        cbTax.isChecked = false
        spPaymentMethod.setSelection(0)
    }

    private fun hitungTotalAkhir() {
        val subtotal = viewModel.totalPrice.value ?: 0.0
        val diskonStr = etDiscount.text.toString()
        nilaiDiskon = if (diskonStr.isNotEmpty()) diskonStr.toDouble() else 0.0

        var afterDisc = subtotal - nilaiDiskon
        if (afterDisc < 0) afterDisc = 0.0

        nilaiPajak = if (cbTax.isChecked) afterDisc * 0.10 else 0.0
        finalTotalBayar = afterDisc + nilaiPajak
        tvTotalBill.text = formatRupiah(finalTotalBayar)

        if (::etCashReceived.isInitialized && etCashReceived.text.isNotEmpty()) {
            uangDiterima = etCashReceived.text.toString().toDouble()
            uangKembali = uangDiterima - finalTotalBayar
            if (uangKembali >= 0) tvChange.text = "Kembali: ${formatRupiah(uangKembali)}"
            else tvChange.text = "Kurang: ${formatRupiah(Math.abs(uangKembali))}"
        }
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

            printerHelper.setAlign(1)
            printerHelper.setBold(true)
            printerHelper.printText("$storeName\n")
            printerHelper.setBold(false)
            printerHelper.printText("$storeAddress\n")
            printerHelper.printText("Telp: $storePhone\n")
            printerHelper.printText("--------------------------------\n")

            printerHelper.setAlign(0)
            printerHelper.printText("ID : #${trx.id}\n")
            printerHelper.printText("Tgl: $dateStr\n")
            printerHelper.printText("Kasir: $kasirName\n")
            printerHelper.printText("--------------------------------\n")

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
            printerHelper.printText("--------------------------------\n")
            printerHelper.printText("$storeEmail\n")
            printerHelper.feedPaper()

            try { Thread.sleep(2000) } catch (e: Exception) {}

        } catch (e: Exception) {
            Toast.makeText(this, "Error Print: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

    private fun formatRow(kiri: String, kanan: String): String {
        val maxChars = 30
        val maxKiri = maxChars - kanan.length - 1
        val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri
        val textKanan = kanan
        val sisaSpasi = maxChars - textKiri.length - textKanan.length
        val spasi = if (sisaSpasi > 0) " ".repeat(sisaSpasi) else " "
        return "$textKiri$spasi$textKanan"
    }

    private fun formatRupiah(number: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')
    }

    override fun onDestroy() {
        super.onDestroy()
        try { printerHelper.close() } catch (e: Exception) {}
    }
}