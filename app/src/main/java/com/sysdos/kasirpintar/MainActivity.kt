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
        val currentSubtotal = viewModel.totalPrice.value ?: 0.0
        if (currentSubtotal <= 0) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // --- BINDING VIEW ---
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPaymentTitle)
        val etReceived = dialogView.findViewById<EditText>(R.id.etAmountReceived)

        val btnBack = dialogView.findViewById<View>(R.id.btnBackPayment)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmPayment)
        val spMethod = dialogView.findViewById<Spinner>(R.id.spPaymentMethod)

        // [BARU] Grid Tombol Uang Cepat (yang sudah dikasih ID tadi)
        val gridQuickCash = dialogView.findViewById<View>(R.id.gridQuickCash)

        // Tombol Opsi & Uang Cepat
        val btnDisc = dialogView.findViewById<Button>(R.id.btnDisc)
        val btnTax = dialogView.findViewById<Button>(R.id.btnTax)
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

        // Setup Spinner
        val methods = arrayOf("Tunai", "QRIS", "Debit", "Transfer")
        spMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)

        // --- FUNGSI HITUNG REALTIME ---
        fun recalculate() {
            // Hitung Diskon & Pajak
            discValue = if (isDiscActive) currentSubtotal * 0.10 else 0.0
            val afterDisc = currentSubtotal - discValue
            taxValue = if (isTaxActive) afterDisc * 0.10 else 0.0
            grandTotal = afterDisc + taxValue

            tvTitle.text = "Total : ${formatRupiah(grandTotal)}"

            // Update Tampilan Tombol Tax/Disc
            val activeBg = R.drawable.bg_solid_primary // Biru
            val inactiveBg = R.drawable.bg_outline_primary // Putih
            val white = android.graphics.Color.WHITE
            val blue = android.graphics.Color.parseColor("#1976D2")

            btnTax.setBackgroundResource(if (isTaxActive) activeBg else inactiveBg)
            btnTax.setTextColor(if (isTaxActive) white else blue)
            btnTax.text = if (isTaxActive) "Tax : 10%" else "Tax : 0%"

            btnDisc.setBackgroundResource(if (isDiscActive) activeBg else inactiveBg)
            btnDisc.setTextColor(if (isDiscActive) white else blue)
            btnDisc.text = if (isDiscActive) "Disc : 10%" else "Disc : 0%"

            // [LOGIKA PINTAR] Update Nominal Otomatis jika BUKAN Tunai
            // Misal: Lagi QRIS, terus tekan Pajak -> Nominal harus update ikut naik
            val currentMethod = spMethod.selectedItem?.toString() ?: "Tunai"
            if (currentMethod != "Tunai") {
                etReceived.setText(grandTotal.toInt().toString())
            }
        }

        // --- LISTENER SPINNER (INTI PERUBAHANNYA DISINI) ---
        spMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMethod = methods[position]

                if (selectedMethod == "Tunai") {
                    // JIKA TUNAI:
                    gridQuickCash.visibility = View.VISIBLE // Munculkan tombol uang
                    etReceived.isEnabled = true // Kasir bisa ketik manual
                    etReceived.setText("0") // Reset ke 0 biar kasir input uang pelanggan
                    etReceived.requestFocus()
                } else {
                    // JIKA NON-TUNAI (QRIS/DEBIT):
                    gridQuickCash.visibility = View.GONE // Hilangkan tombol uang
                    etReceived.isEnabled = false // Kunci input (biar gak salah ketik)
                    etReceived.setText(grandTotal.toInt().toString()) // OTOMATIS ISI HARGA PAS
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- LOGIKA LAINNYA ---
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

            // Validasi Tunai
            if (method == "Tunai" && cashReceived < grandTotal) {
                Toast.makeText(this, "⚠️ Uang Kurang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Jika Non-Tunai, pastikan amount = grandTotal (double check)
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
                    dialog.dismiss() // Tutup Dialog Bayar

                    // --- [PERBAIKAN] OTOMATIS PRINT ---
                    val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
                    val printerMac = prefs.getString("printer_mac", "") // Ambil MAC Printer

                    if (!printerMac.isNullOrEmpty()) {
                        printStruk(transaction) // <--- INI YANG TADI HILANG!
                        Toast.makeText(this, "Mencetak Struk...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Printer belum disetting!", Toast.LENGTH_SHORT).show()
                    }

                    // Tampilkan Dialog Sukses
                    AlertDialog.Builder(this)
                        .setTitle("✅ Transaksi Berhasil")
                        .setMessage(if (changeAmount > 0) "Kembalian: ${formatRupiah(changeAmount)}" else "Pembayaran Lunas")
                        .setPositiveButton("TUTUP") { _, _ ->
                            // Keranjang otomatis bersih
                        }
                        .setNeutralButton("CETAK LAGI") { _, _ ->
                            // Opsi buat jaga-jaga kalau kertas macet
                            if (!printerMac.isNullOrEmpty()) printStruk(transaction)
                        }
                        .show()
                } else {
                    Toast.makeText(this, "Gagal Transaksi", Toast.LENGTH_SHORT).show()
                }
            }
        }

        recalculate() // Jalankan hitungan awal
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
            Toast.makeText(this, "Keranjang masih kosong", Toast.LENGTH_SHORT).show()
            return
        }

        // --- [UBAH DI SINI] PAKAI DIALOG FULLSCREEN ---
        // Kita pakai Theme_Material_Light_NoActionBar_Fullscreen agar penuh satu layar
        val dialog = android.app.Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_cart_preview)

        val container = dialog.findViewById<LinearLayout>(R.id.llCartItems)
        val tvTotalTop = dialog.findViewById<TextView>(R.id.tvPreviewTotalTop)
        val btnClose = dialog.findViewById<View>(R.id.btnCloseCart) // Tombol X di kiri atas
        val btnAddMore = dialog.findViewById<Button>(R.id.btnAddMore)
        val btnProceed = dialog.findViewById<Button>(R.id.btnProceedToPay)

        // Update Total di Header
        val currentTotal = viewModel.totalPrice.value ?: 0.0
        tvTotalTop.text = formatRupiah(currentTotal)

        // Loop isi keranjang
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

            // Logic Tombol (+) (-) (Hapus) SAMA PERSIS SEPERTI SEBELUMNYA
            btnPlus.setOnClickListener {
                val originalProduct = fullList.find { it.id == item.id }
                if (originalProduct != null) {
                    viewModel.addToCart(originalProduct) { msg ->
                        if (msg.contains("Stok", true)) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                    showCartPreview() // Refresh Fullscreen
                }
            }

            btnMinus.setOnClickListener {
                viewModel.decreaseCartItem(item)
                dialog.dismiss()
                showCartPreview()
            }

            // Set icon minus jika belum ada
            val imgMinus = btnMinus as? ImageButton
            imgMinus?.setImageResource(android.R.drawable.btn_minus)

            btnRemove.setOnClickListener {
                viewModel.removeCartItem(item)
                dialog.dismiss()
                showCartPreview()
            }

            container.addView(itemView)
        }

        // TOMBOL-TOMBOL AKSI
        btnClose.setOnClickListener { dialog.dismiss() } // Tombol X header
        btnAddMore.setOnClickListener { dialog.dismiss() } // Tombol Tambah Menu

        btnProceed.setOnClickListener {
            dialog.dismiss()
            showPaymentDialog() // Lanjut ke Pembayaran
        }

        dialog.show()
    }
    // --- FUNGSI CETAK STRUK (BLUETOOTH) ---
    private fun printStruk(trx: com.sysdos.kasirpintar.data.model.Transaction) {
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val printerMac = prefs.getString("printer_mac", "")
        val storeName = prefs.getString("name", "Toko Saya")
        val storeAddress = prefs.getString("address", "Alamat Toko")
        val storeFooter = prefs.getString("email", "Terima Kasih")

        if (printerMac.isNullOrEmpty()) return

        // Jalankan di Background Thread biar UI gak macet
        Thread {
            try {
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val device = bluetoothAdapter.getRemoteDevice(printerMac)

                // Cek Permission Bluetooth (Standar Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        return@Thread
                    }
                }

                val socket = device.createRfcommSocketToServiceRecord(java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket.connect()
                val outputStream = socket.outputStream

                // --- FORMAT STRUK ---
                val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
                val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
                val TEXT_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
                val TEXT_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
                val TEXT_SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
                val TEXT_SIZE_LARGE = byteArrayOf(0x1D, 0x21, 0x11)
                val FEED_PAPER = byteArrayOf(0x0A, 0x0A, 0x0A) // 3x Enter

                // 1. HEADER
                outputStream.write(ESC_ALIGN_CENTER)
                outputStream.write(TEXT_BOLD_ON)
                outputStream.write(TEXT_SIZE_LARGE)
                outputStream.write("$storeName\n".toByteArray())
                outputStream.write(TEXT_SIZE_NORMAL)
                outputStream.write(TEXT_BOLD_OFF)
                outputStream.write("$storeAddress\n".toByteArray())
                outputStream.write("--------------------------------\n".toByteArray())

                // 2. INFO TRANSAKSI
                outputStream.write(ESC_ALIGN_LEFT)
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                val dateStr = sdf.format(java.util.Date(trx.timestamp))
                outputStream.write("Tgl : $dateStr\n".toByteArray())
                outputStream.write("No  : TRX-${trx.id}\n".toByteArray())
                outputStream.write("--------------------------------\n".toByteArray())

                // 3. ITEM BELANJA
                // Kita butuh detail item. Karena Transaction object biasanya cuma simpan total,
                // idealnya kita ambil dari 'trx.items' kalau Bapak sudah relasikan.
                // TAPI, untuk saat ini kita ambil dari viewModel.cart.value SEBELUM DIHAPUS
                // (Catatan: Ini trik cepat. Idealnya ambil dari database relation)

                val itemsToPrint = viewModel.cart.value ?: emptyList()
                for (item in itemsToPrint) {
                    outputStream.write("${item.name}\n".toByteArray())
                    val qtyPrice = "${item.stock} x ${item.price.toInt()}"
                    val subtotal = item.stock * item.price

                    // Format rata kanan manual sederhana
                    val line = String.format("%-20s %10s\n", qtyPrice, subtotal.toInt().toString())
                    outputStream.write(line.toByteArray())
                }

                outputStream.write("--------------------------------\n".toByteArray())

                // 4. TOTAL & PEMBAYARAN
                outputStream.write(ESC_ALIGN_LEFT)
                outputStream.write("Total    : ${trx.totalAmount.toInt()}\n".toByteArray())
                if (trx.discount > 0) outputStream.write("Diskon   : -${trx.discount.toInt()}\n".toByteArray())
                if (trx.tax > 0) outputStream.write("Pajak    : +${trx.tax.toInt()}\n".toByteArray())

                outputStream.write(TEXT_BOLD_ON)
                outputStream.write("TAGIHAN  : ${trx.totalAmount.toInt()}\n".toByteArray())
                outputStream.write(TEXT_BOLD_OFF)

                outputStream.write("Bayar    : ${trx.cashReceived.toInt()}\n".toByteArray())
                outputStream.write("Kembali  : ${trx.changeAmount.toInt()}\n".toByteArray())

                outputStream.write("--------------------------------\n".toByteArray())

                // 5. FOOTER
                outputStream.write(ESC_ALIGN_CENTER)
                outputStream.write("$storeFooter\n".toByteArray())
                outputStream.write("Powered by Sysdos POS\n".toByteArray())
                outputStream.write(FEED_PAPER) // Spasi bawah biar gampang sobek

                // SELESAI
                outputStream.flush()
                socket.close()

                // Bersihkan keranjang setelah struk tercetak (Opsional, atau di tombol Tutup)
                runOnUiThread {
                    viewModel.clearCart()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Gagal Print: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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