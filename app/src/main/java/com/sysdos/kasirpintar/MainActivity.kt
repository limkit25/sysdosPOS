package com.sysdos.kasirpintar

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.util.Log
import android.widget.Toast
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.ProductResponse
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
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
import androidx.recyclerview.widget.LinearLayoutManager // <--- IMPORT INI
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

    // UI Elements
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

    // PREFERENCE UNTUK SHIFT (MODAL & SESI)
    private lateinit var session: SharedPreferences
    private lateinit var shiftPrefs: SharedPreferences

    // SCANNER LAUNCHER
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

        // 1. INIT PREFERENCES
        session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)

        printerHelper = PrinterHelper(this)
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 2. AUTO CONNECT PRINTER
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        selectedPrinterAddress = prefs.getString("printer_mac", null)
        if (selectedPrinterAddress != null) connectPrinterSilent(selectedPrinterAddress!!)

        // 3. BIND VIEW
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

        // 🔥 UBAH DARI GRID KE LINEAR (LIST) AGAR RAPI 🔥
        rvProducts.layoutManager = LinearLayoutManager(this)
        rvProducts.adapter = productAdapter

        // 4. OBSERVE DATA
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

        // 5. SEARCH LISTENER
        svSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterData()
                return true
            }
        })

        // 6. TOMBOL SCAN & CHECKOUT
        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(ScanActivity::class.java)
            barcodeLauncher.launch(options)
        }

        val bottomBar = findViewById<androidx.cardview.widget.CardView>(R.id.bottomBar)
        bottomBar.setOnClickListener { showCartPreview() }

        btnCheckout.setOnClickListener {
            if (viewModel.totalPrice.value == 0.0) {
                Toast.makeText(this, "Keranjang Kosong!", Toast.LENGTH_SHORT).show()
            } else {
                showCartPreview()
            }
        }

        // 7. HANDLE BACK BUTTON (Menu Keluar / Tutup Shift)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Langsung tutup halaman POS dan balik ke Dashboard
                finish()
            }
        })

        // 8. CEK STATUS SHIFT SAAT PERTAMA BUKA
        checkShiftStatus()
        // ============================================================
        // 9. TES KONEKSI SERVER
        // ============================================================
        android.util.Log.d("Sysdos", "Mencoba menghubungi server...")

        // 🔥 PERBAIKAN DI SINI: Gunakan .getInstance(this) 🔥
        com.sysdos.kasirpintar.api.ApiClient.getInstance(this).getProducts().enqueue(object : retrofit2.Callback<List<com.sysdos.kasirpintar.api.ProductResponse>> {
            override fun onResponse(
                call: retrofit2.Call<List<com.sysdos.kasirpintar.api.ProductResponse>>,
                response: retrofit2.Response<List<com.sysdos.kasirpintar.api.ProductResponse>>
            ) {
                if (response.isSuccessful) {
                    val dataBarang = response.body()
                    android.widget.Toast.makeText(this@MainActivity, "Koneksi OK! Data: ${dataBarang?.size}", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(this@MainActivity, "Server Error: ${response.code()}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<List<com.sysdos.kasirpintar.api.ProductResponse>>, t: Throwable) {
                android.widget.Toast.makeText(this@MainActivity, "Gagal Konek. Cek IP di Login!", android.widget.Toast.LENGTH_LONG).show()
            }
        })
        // ============================================================
    }


    // ==========================================
    // 🔥 LOGIKA SHIFT SYSTEM (BUKA/TUTUP)
    // ==========================================

    private fun checkShiftStatus() {
        val isShiftOpen = shiftPrefs.getBoolean("IS_OPEN", false)
        if (!isShiftOpen) {
            showOpenShiftDialog() // Paksa Input Modal
        }
    }

    private fun showOpenShiftDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(64, 40, 64, 10)

        val etModal = EditText(this)
        etModal.hint = "Rp 0"
        etModal.inputType = InputType.TYPE_CLASS_NUMBER
        layout.addView(etModal)

        AlertDialog.Builder(this)
            .setTitle("☀️ Buka Shift (Input Modal)")
            .setMessage("Masukkan jumlah uang modal di laci kasir:")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("BUKA KASIR") { _, _ ->
                val modalStr = etModal.text.toString()
                if (modalStr.isNotEmpty()) {
                    val modal = modalStr.toDouble()
                    val kasirName = session.getString("username", "Kasir") ?: "Kasir"

                    // Simpan ke Prefs
                    shiftPrefs.edit()
                        .putBoolean("IS_OPEN", true)
                        .putFloat("MODAL_AWAL", modal.toFloat())
                        .putLong("START_TIME", System.currentTimeMillis())
                        .putFloat("CASH_SALES_TODAY", 0f)
                        .apply()

                    // Simpan ke Database
                    viewModel.openShift(kasirName, modal)

                    Toast.makeText(this, "Shift Dibuka! Semangat 🚀", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Isi 0 jika tanpa modal", Toast.LENGTH_SHORT).show()
                    showOpenShiftDialog()
                }
            }
            .setNegativeButton("Keluar App") { _, _ -> finish() }
            .show()
    }

    // ==========================================
    // 💰 PEMBAYARAN & CHECKOUT
    // ==========================================

    private fun showPaymentDialog() {
        val currentSubtotal = viewModel.totalPrice.value ?: 0.0
        if (currentSubtotal <= 0) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPaymentTitle)
        val etReceived = dialogView.findViewById<EditText>(R.id.etAmountReceived)
        val btnBack = dialogView.findViewById<View>(R.id.btnBackPayment)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmPayment)
        val spMethod = dialogView.findViewById<Spinner>(R.id.spPaymentMethod)
        val gridQuickCash = dialogView.findViewById<View>(R.id.gridQuickCash)

        val btnDisc = dialogView.findViewById<Button>(R.id.btnDisc)
        val btnTax = dialogView.findViewById<Button>(R.id.btnTax)

        // Tombol Uang Cepat
        val btnExact = dialogView.findViewById<Button>(R.id.btnExactMoney)
        val btn20k = dialogView.findViewById<Button>(R.id.btn20k)
        val btn50k = dialogView.findViewById<Button>(R.id.btn50k)
        val btn100k = dialogView.findViewById<Button>(R.id.btn100k)
        val btnOther = dialogView.findViewById<Button>(R.id.btnOtherAmount)

        var grandTotal = currentSubtotal
        var discValue = 0.0
        var taxValue = 0.0
        var isTaxActive = false
        var isDiscActive = false

        val methods = arrayOf("Tunai", "QRIS", "Debit", "Transfer")
        spMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)

        fun recalculate() {
            discValue = if (isDiscActive) currentSubtotal * 0.10 else 0.0
            val afterDisc = currentSubtotal - discValue
            taxValue = if (isTaxActive) afterDisc * 0.10 else 0.0
            grandTotal = afterDisc + taxValue

            tvTitle.text = "Total : ${formatRupiah(grandTotal)}"

            val activeBg = R.drawable.bg_solid_primary
            val inactiveBg = R.drawable.bg_outline_primary
            btnTax.setBackgroundResource(if (isTaxActive) activeBg else inactiveBg)
            btnTax.setTextColor(if (isTaxActive) Color.WHITE else Color.parseColor("#1976D2"))
            btnTax.text = if (isTaxActive) "Tax : 10%" else "Tax : 0%"

            btnDisc.setBackgroundResource(if (isDiscActive) activeBg else inactiveBg)
            btnDisc.setTextColor(if (isDiscActive) Color.WHITE else Color.parseColor("#1976D2"))
            btnDisc.text = if (isDiscActive) "Disc : 10%" else "Disc : 0%"

            if (spMethod.selectedItem?.toString() != "Tunai") {
                etReceived.setText(grandTotal.toInt().toString())
            }
        }

        spMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (methods[position] == "Tunai") {
                    gridQuickCash.visibility = View.VISIBLE
                    etReceived.isEnabled = true
                    etReceived.setText("0"); etReceived.requestFocus()
                } else {
                    gridQuickCash.visibility = View.GONE
                    etReceived.isEnabled = false
                    etReceived.setText(grandTotal.toInt().toString())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnTax.setOnClickListener { isTaxActive = !isTaxActive; recalculate() }
        btnDisc.setOnClickListener { isDiscActive = !isDiscActive; recalculate() }

        btnExact.setOnClickListener { etReceived.setText(grandTotal.toInt().toString()) }
        btn20k.setOnClickListener { etReceived.setText("20000") }
        btn50k.setOnClickListener { etReceived.setText("50000") }
        btn100k.setOnClickListener { etReceived.setText("100000") }
        btnOther.setOnClickListener { etReceived.setText(""); etReceived.requestFocus() }

        btnBack.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val method = spMethod.selectedItem.toString()
            val receivedStr = etReceived.text.toString()
            var cashReceived = if (receivedStr.isNotEmpty()) receivedStr.toDouble() else 0.0

            if (method == "Tunai" && cashReceived < grandTotal) {
                Toast.makeText(this, "⚠️ Uang Kurang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (method != "Tunai") cashReceived = grandTotal

            val changeAmount = cashReceived - grandTotal

            viewModel.checkout(
                subtotal = currentSubtotal,
                discount = discValue,
                tax = taxValue,
                paymentMethod = method,
                cashReceived = cashReceived,
                changeAmount = changeAmount
            ) { transaction ->
                if (transaction != null) {

                    // 🔥 UPDATE DATA SHIFT (Penjualan Tunai) 🔥
                    if (method == "Tunai") {
                        val currentCash = shiftPrefs.getFloat("CASH_SALES_TODAY", 0f)
                        val newTotal = currentCash + grandTotal.toFloat()
                        shiftPrefs.edit().putFloat("CASH_SALES_TODAY", newTotal).apply()
                    }

                    dialog.dismiss()

                    // Auto Print
                    val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
                    val printerMac = prefs.getString("printer_mac", "")
                    if (!printerMac.isNullOrEmpty()) {
                        printStruk(transaction)
                        Toast.makeText(this, "Mencetak Struk...", Toast.LENGTH_SHORT).show()
                    }

                    AlertDialog.Builder(this)
                        .setTitle("✅ Transaksi Berhasil")
                        .setMessage(if (changeAmount > 0) "Kembalian: ${formatRupiah(changeAmount)}" else "Lunas")
                        .setPositiveButton("TUTUP", null)
                        .setNeutralButton("CETAK LAGI") { _, _ ->
                            if (!printerMac.isNullOrEmpty()) printStruk(transaction)
                        }
                        .show()
                } else {
                    Toast.makeText(this, "Gagal Transaksi", Toast.LENGTH_SHORT).show()
                }
            }
        }

        recalculate()
        dialog.show()
    }

    // ==========================================
    // 🛠️ HELPER FUNCTIONS
    // ==========================================

    private fun showCartPreview() {
        val currentCart = viewModel.cart.value ?: emptyList()
        if (currentCart.isEmpty()) {
            Toast.makeText(this, "Keranjang masih kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_cart_preview)

        val container = dialog.findViewById<LinearLayout>(R.id.llCartItems)
        val tvTotalTop = dialog.findViewById<TextView>(R.id.tvPreviewTotalTop)
        val btnClose = dialog.findViewById<View>(R.id.btnCloseCart)
        val btnAddMore = dialog.findViewById<Button>(R.id.btnAddMore)
        val btnProceed = dialog.findViewById<Button>(R.id.btnProceedToPay)

        tvTotalTop.text = formatRupiah(viewModel.totalPrice.value ?: 0.0)

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

            btnPlus.setOnClickListener {
                val originalProduct = fullList.find { it.id == item.id }
                if (originalProduct != null) {
                    viewModel.addToCart(originalProduct) { }
                    dialog.dismiss(); showCartPreview()
                }
            }
            btnMinus.setOnClickListener {
                viewModel.decreaseCartItem(item)
                dialog.dismiss(); showCartPreview()
            }
            (btnMinus as? ImageButton)?.setImageResource(android.R.drawable.btn_minus)

            btnRemove.setOnClickListener {
                viewModel.removeCartItem(item)
                dialog.dismiss(); showCartPreview()
            }
            container.addView(itemView)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnAddMore.setOnClickListener { dialog.dismiss() }
        btnProceed.setOnClickListener { dialog.dismiss(); showPaymentDialog() }
        dialog.show()
    }

    private fun showCartActionDialog(product: Product) {
        val options = arrayOf("➖ Kurangi 1", "❌ Batal Item")
        AlertDialog.Builder(this).setTitle(product.name).setItems(options) { _, which ->
            when (which) {
                0 -> viewModel.decreaseCartItem(product)
                1 -> viewModel.removeCartItem(product)
            }
        }.show()
    }

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
        val params = LinearLayout.LayoutParams(-2, -2)
        params.setMargins(0, 0, 16, 0)
        btn.layoutParams = params

        if (selectedCategory == categoryName) {
            btn.setBackgroundResource(R.drawable.bg_rounded_white)
            btn.background.setTint(Color.WHITE)
            btn.setTextColor(Color.parseColor("#1976D2"))
            btn.setTypeface(null, android.graphics.Typeface.BOLD)
            btn.elevation = 8f
        } else {
            btn.setBackgroundResource(R.drawable.bg_rounded_white)
            btn.background.setTint(Color.WHITE)
            btn.setTextColor(Color.parseColor("#757575"))
            btn.elevation = 0f
        }

        btn.setOnClickListener {
            selectedCategory = categoryName
            setupCategoryButtons(viewModel.allCategories.value ?: emptyList())
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

    private fun connectPrinterSilent(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }
        printerHelper.connectPrinter(address) {}
    }

    private fun formatRupiah(number: Double): String = String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')

    private fun printStruk(trx: com.sysdos.kasirpintar.data.model.Transaction) {
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val targetMac = prefs.getString("printer_mac", "")
        if (targetMac.isNullOrEmpty()) return

        Thread {
            var socket: android.bluetooth.BluetoothSocket? = null
            var isConnected = false
            var attempt = 0
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

            while (attempt < 3 && !isConnected) {
                try {
                    attempt++
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@Thread
                    val device = bluetoothAdapter.getRemoteDevice(targetMac)
                    val uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket.connect()
                    isConnected = true
                } catch (e: Exception) { try { socket?.close() } catch (x: Exception) {}; if (attempt < 3) Thread.sleep(1000) }
            }

            if (!isConnected) return@Thread

            try {
                val outputStream = socket!!.outputStream
                val storeName = prefs.getString("name", "Toko Saya")
                val storeAddress = prefs.getString("address", "Indonesia")
                val storePhone = prefs.getString("phone", "")
                val kasirName = session.getString("username", "Admin")
                val dateStr = java.text.SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(java.util.Date(trx.timestamp))

                val p = StringBuilder()
                p.append("\u001B\u0061\u0001\u001B\u0045\u0001$storeName\n\u001B\u0045\u0000$storeAddress\n")
                if (!storePhone.isNullOrEmpty()) p.append("Telp: $storePhone\n")
                p.append("--------------------------------\n\u001B\u0061\u0000ID   : #${trx.id}\nTgl  : $dateStr\nKasir: $kasirName\n--------------------------------\n")

                fun row(kiri: String, valDbl: Double, isMinus: Boolean = false): String {
                    val formattedVal = String.format(Locale("id","ID"), "Rp %,d", valDbl.toLong()).replace(',', '.')
                    val kanan = if(isMinus) "-$formattedVal" else formattedVal
                    val maxChars = 32
                    val maxKiri = maxChars - kanan.length - 1
                    val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri
                    val sisa = maxChars - textKiri.length - kanan.length
                    return "$textKiri${" ".repeat(if(sisa>0) sisa else 1)}$kanan\n"
                }

                val items = trx.itemsSummary.split(";")
                for (itemStr in items) {
                    val parts = itemStr.split("|")
                    if (parts.size == 4) {
                        p.append("${parts[0]}\n")
                        p.append(row("  ${parts[1]} x ${formatRupiah(parts[2].toDouble())}", parts[3].toDouble()))
                    } else p.append("$itemStr\n")
                }
                p.append("--------------------------------\n")
                p.append(row("Subtotal", trx.subtotal))
                if (trx.discount > 0) p.append(row("Diskon", trx.discount, true))
                if (trx.tax > 0) p.append(row("Pajak", trx.tax))
                p.append("--------------------------------\n\u001B\u0045\u0001${row("TOTAL", trx.totalAmount)}\u001B\u0045\u0000")
                if (trx.paymentMethod.contains("Tunai")) {
                    p.append(row("Tunai", trx.cashReceived)); p.append(row("Kembali", trx.changeAmount))
                } else p.append("Metode: ${trx.paymentMethod}\n")
                p.append("\u001B\u0061\u0001--------------------------------\n${prefs.getString("email", "Terima Kasih!")}\n\n\n")

                outputStream.write(p.toString().toByteArray()); outputStream.flush(); Thread.sleep(1500); socket.close()
            } catch (e: Exception) { try { socket?.close() } catch (e: Exception) {} }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { printerHelper.close() } catch (e: Exception) {}
    }
}