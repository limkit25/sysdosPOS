package com.sysdos.kasirpintar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class DashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private var allTrx: List<Transaction> = emptyList()

    // 🔥 AUTO SLIDE HANDLER
    private val sliderHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sliderRunnable = object : Runnable {
        override fun run() {
            val vp = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.vpRevenueSlider)
            if (vp?.adapter != null && vp.adapter!!.itemCount > 0) {
                val nextItem = (vp.currentItem + 1) % vp.adapter!!.itemCount
                vp.currentItem = nextItem
            }
            sliderHandler.postDelayed(this, 5000) // 5 Detik
        }
    }

    override fun onPause() {
        super.onPause()
        sliderHandler.removeCallbacks(sliderRunnable)
    }

    override fun onResume() {
        super.onResume()
        cekKeamananHP()
        sliderHandler.postDelayed(sliderRunnable, 5000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard) // XML SUDAH DIGANTI YANG BARU

        UpdateManager(this).checkForUpdate()
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // ❌ HAPUS KODE DRAWER / MENU SAMPING DARI SINI ❌
        // Kita hanya pakai logika Dashboard biasa

        // =================================================================
        // 🏠 SETUP DASHBOARD
        // =================================================================

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvRole = findViewById<TextView>(R.id.tvRole)

        val btnLogout = findViewById<ImageView>(R.id.btnLogout)
        val mainGrid = findViewById<GridLayout>(R.id.mainGrid)

        // Init Card
        val cardPOS = findViewById<CardView>(R.id.cardPOS)
        val cardProduct = findViewById<CardView>(R.id.cardProduct)
        val cardCategory = findViewById<CardView>(R.id.cardCategory) // 🔥 NEW
        val cardPurchase = findViewById<CardView>(R.id.cardPurchase)
        val cardReport = findViewById<CardView>(R.id.cardReport)
        val cardUser = findViewById<CardView>(R.id.cardUser)
        val cardStore = findViewById<CardView>(R.id.cardStore)
        val cardPrinter = findViewById<CardView>(R.id.cardPrinter)
        val cardStockOpname = findViewById<CardView>(R.id.cardStockOpname) // 🔥 NEW
        val cardExpense = findViewById<CardView>(R.id.cardExpense) // 🔥 NEW
        val cardShift = findViewById<CardView>(R.id.cardShift)
        val cardAbout = findViewById<CardView>(R.id.cardAbout)

        val tvLowStock = findViewById<TextView>(R.id.tvLowStockCount)
        val cardLowStockInfo = findViewById<CardView>(R.id.cardLowStockInfo)

        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "Admin")
        val realName = session.getString("fullname", username) ?: "Admin"
        val role = session.getString("role", "kasir")

        tvGreeting.text = "Halo, $realName"
        
        // 🔥 OBSERVE LIVE DATA NAMA CABANG
        viewModel.branchName.observe(this) { bName ->
            val finalBranchName = bName ?: "Pusat"
            tvRole.text = "Role: ${role?.uppercase()}\nLokasi: $finalBranchName"
        }

        if (role == "kasir") {
            cardLowStockInfo.visibility = View.GONE
        } else {
            cardLowStockInfo.visibility = View.VISIBLE
        }

        // 🔥 OBSERVE TRANSAKSI (HITUNG OMZET HARI INI)
        // 🔥 OBSERVE TRANSAKSI (HITUNG OMZET SLIDER)
        viewModel.allTransactions.observe(this) { transactions ->
            allTrx = transactions
            
            val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            
            val now = Date()
            val todayStr = sdfDay.format(now)
            val thisMonthStr = sdfMonth.format(now)
            
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.MONTH, -1)
            val lastMonthStr = sdfMonth.format(cal.time)

            var totalHariIni = 0.0
            var totalBulanIni = 0.0
            var totalBulanLalu = 0.0

            for (trx in transactions) {
                val date = Date(trx.timestamp)
                val dStr = sdfDay.format(date)
                val mStr = sdfMonth.format(date)

                if (dStr == todayStr) totalHariIni += trx.totalAmount
                if (mStr == thisMonthStr) totalBulanIni += trx.totalAmount
                if (mStr == lastMonthStr) totalBulanLalu += trx.totalAmount
            }
            
            // SETUP SLIDER (Safe Mode)
            try {
                val vpSlider = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.vpRevenueSlider)
                if (vpSlider != null) {
                    val items = listOf(
                        RevenueItem("Omzet Hari Ini", totalHariIni, "#4CAF50"),
                        RevenueItem("Omzet Bulan Ini", totalBulanIni, "#1976D2"),
                        RevenueItem("Omzet Bulan Kemarin", totalBulanLalu, "#FF5722")
                    )
                    vpSlider.adapter = RevenueSliderAdapter(items)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val imgAlert = findViewById<ImageView>(R.id.imgAlertStock)
        viewModel.allProducts.observe(this) { products ->
            val lowStockCount = products.count { it.stock < 5 }
            if (lowStockCount > 0) {
                tvLowStock.text = "$lowStockCount Item"
                tvLowStock.setTextColor(android.graphics.Color.RED)
                imgAlert.visibility = View.VISIBLE
                imgAlert.setColorFilter(android.graphics.Color.RED)
            } else {
                tvLowStock.text = "Aman"
                tvLowStock.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                imgAlert.visibility = View.VISIBLE
                imgAlert.setImageResource(android.R.drawable.checkbox_on_background)
                imgAlert.setColorFilter(android.graphics.Color.parseColor("#2E7D32"))
            }
        }

        // --- SORTING MENU (HP vs TABLET) ---
        mainGrid.removeAllViews()
        val authorizedCards = mutableListOf<View>()

        authorizedCards.add(cardPOS)
        if (role == "admin") {
            authorizedCards.add(cardProduct); authorizedCards.add(cardCategory); authorizedCards.add(cardStockOpname); 
            authorizedCards.add(cardPurchase); authorizedCards.add(cardExpense); authorizedCards.add(cardReport)
            authorizedCards.add(cardUser); authorizedCards.add(cardStore); authorizedCards.add(cardPrinter)
        } else if (role == "manager") {
            authorizedCards.add(cardProduct); authorizedCards.add(cardCategory); authorizedCards.add(cardStockOpname);
            authorizedCards.add(cardPurchase); authorizedCards.add(cardExpense); authorizedCards.add(cardReport)
            authorizedCards.add(cardPrinter)
        } else {
            // 🔥 ROLE: KASIR (Hanya POS, About)
            authorizedCards.add(cardReport) 
            authorizedCards.add(cardPrinter)
        }
        authorizedCards.add(cardAbout)

        // RESPONSIVE TABLET & ADD VIEWS TO GRID
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        if (screenWidthDp >= 800) {
            mainGrid.columnCount = 4
        } else if (screenWidthDp >= 600) {
            mainGrid.columnCount = 3
        } else {
            mainGrid.columnCount = 2
        }

        for (card in authorizedCards) {
            mainGrid.addView(card)
            card.visibility = View.VISIBLE
            val params = card.layoutParams as GridLayout.LayoutParams
            params.width = 0
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            card.layoutParams = params
        }


        // --- CLICK LISTENERS ---
        cardPOS.setOnClickListener {
            val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
            val isFullVersion = licensePrefs.getBoolean("is_full_version", false)
            val isExpired = licensePrefs.getBoolean("is_expired", false)

            if (isExpired && !isFullVersion) {
                // 🔥 AMBIL PESAN ASLI DARI SERVER (BIAR GAK SELALU 'TRIAL')
                val msg = licensePrefs.getString("license_msg", "Masa Aktif Habis") ?: "Masa Aktif Habis"

                AlertDialog.Builder(this)
                    .setTitle("⚠️ MASA AKTIF HABIS")
                    .setMessage("$msg\n\nSilakan hubungi Admin untuk perpanjangan.")
                    .setPositiveButton("HUBUNGI ADMIN") { _, _ ->
                        try {
                            // 1. GANTI NOMOR WA ANDA DISINI (Wajib pakai kode negara 62)
                            val nomorAdmin = "628179842043"

                            // 2. Pesan otomatis
                            val pesan = "Halo Admin, lisensi saya habis ($msg). Saya mau perpanjang."

                            // 3. Buka WhatsApp
                            val url = "https://api.whatsapp.com/send?phone=$nomorAdmin&text=${java.net.URLEncoder.encode(pesan, "UTF-8")}"
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = android.net.Uri.parse(url)
                            startActivity(intent)

                        } catch (e: Exception) {
                            Toast.makeText(this, "Gagal membuka WhatsApp, pastikan terinstall.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Tutup", null)
                    .show()
            } else {
                // checkModalBeforePOS() -> Diganti pakai Helper
                com.sysdos.kasirpintar.utils.BottomNavHelper.checkAndOpenPOS(this, viewModel)
            }
        }

        cardProduct.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java)) }
        cardCategory.setOnClickListener { startActivity(Intent(this, CategoryActivity::class.java)) } // 🔥 NEW
        cardPurchase.setOnClickListener { startActivity(Intent(this, PurchaseActivity::class.java)) }
        cardExpense.setOnClickListener { startActivity(Intent(this, ExpenseActivity::class.java)) } // 🔥 NEW
        cardStockOpname.setOnClickListener { startActivity(Intent(this, StockOpnameActivity::class.java)) } // 🔥 CLICK LISTENER
        cardReport.setOnClickListener { 
            startActivity(Intent(this, ReportCenterActivity::class.java)) 
        }
        cardUser.setOnClickListener { startActivity(Intent(this, UserListActivity::class.java)) }
        cardStore.setOnClickListener { startActivity(Intent(this, StoreSettingsActivity::class.java).apply { putExtra("TARGET", "STORE") }) }
        cardPrinter.setOnClickListener { startActivity(Intent(this, StoreSettingsActivity::class.java).apply { putExtra("TARGET", "PRINTER") }) }
        cardShift?.setOnClickListener { startActivity(Intent(this, ShiftHistoryActivity::class.java)) }
        cardLowStockInfo.setOnClickListener { startActivity(Intent(this, ProductListActivity::class.java).apply { putExtra("OPEN_TAB_INDEX", 1) }) }
        cardAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        // 🔥 SETUP BOTTOM NAV
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        com.sysdos.kasirpintar.utils.BottomNavHelper.setup(this, bottomNav, viewModel)

        // --- LOGOUT LOGIC ---
        btnLogout.setOnClickListener {
            val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
            val isShiftOpen = shiftPrefs.getBoolean("IS_OPEN_GLOBAL_SESSION", false)
            val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
            val isFullVersion = licensePrefs.getBoolean("is_full_version", false)

            if (role == "kasir") {
                val options = if (isShiftOpen) arrayOf("💰 Tutup Kasir & Cetak Laporan", "🚪 Log Out Biasa") else arrayOf("🚪 Log Out Biasa")
                AlertDialog.Builder(this)
                    .setTitle("Menu Keluar (Kasir)")
                    .setItems(options) { _, which ->
                        if (options[which].contains("Tutup Kasir")) showCloseSessionDialog(session)
                        else performLogout(session, false)
                    }
                    .setNegativeButton("Batal", null).show()
            } else {
                val menuList = mutableListOf<String>()
                if (isShiftOpen) menuList.add("💰 Tutup Shift & Cetak")
                menuList.add("🚪 Log Out Biasa")
                if (!isFullVersion) menuList.add("🗑️ Log Out & HAPUS DATA (Reset)")
                val optionsArray = menuList.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Menu Keluar ($role)")
                    .setItems(optionsArray) { _, which ->
                        when (optionsArray[which]) {
                            "💰 Tutup Shift & Cetak" -> showCloseSessionDialog(session)
                            "🚪 Log Out Biasa" -> performLogout(session, false)
                            "🗑️ Log Out & HAPUS DATA (Reset)" -> showResetConfirmation(session)
                        }
                    }
                    .setNegativeButton("Batal", null).show()
            }
        }

        // TRIAL DISPLAY CHECK
        val licensePrefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
        val isFull = licensePrefs.getBoolean("is_full_version", false)
        val tvTrial = findViewById<TextView>(R.id.tvTrialStatus)
        if (isFull) {
            tvTrial.visibility = View.GONE
        } else {
            val firstRunDate = licensePrefs.getLong("first_install_date", 0L)
            if (firstRunDate != 0L) {
                val now = System.currentTimeMillis()
                val diffMillis = now - firstRunDate
                val daysUsed = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
                val daysLeft = 7 - daysUsed
                if (daysLeft > 0) {
                    tvTrial.text = "Sisa Trial: $daysLeft Hari"
                    tvTrial.visibility = View.VISIBLE
                } else {
                    tvTrial.text = "TRIAL HABIS!"
                    tvTrial.setTextColor(android.graphics.Color.RED)
                    tvTrial.visibility = View.VISIBLE
                }
            }
        }
    }

    // =================================================================
    // 👮‍♂️ KEAMANAN & HELPER (TIDAK PERLU DIUBAH)
    // =================================================================

    // override fun onResume() { ... } // SUDAH DIGABUNG DI ATAS

    private fun cekKeamananHP() {
        // ❌ HAPUS ATAU KOMENTARI BARIS DI BAWAH INI SAAT MAU TES DI HP SENDIRI
        if (BuildConfig.DEBUG) return

        try {
            // 1. Cek USB Debugging (ADB) - Ini yang paling krusial
            val isAdb = android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) != 0

            // 2. Cek Developer Options (Opsi Pengembang)
            val isDevMode = android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0

            // Jika salah satu nyala, langsung blokir
            if (isAdb || isDevMode) {
                tampilkanPeringatanKeamanan()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun tampilkanPeringatanKeamanan() {
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("⛔ AKSES DITOLAK")
                .setMessage("Matikan Developer Options / USB Debugging.")
                .setCancelable(false)
                .setPositiveButton("KELUAR") { _, _ -> finishAffinity(); System.exit(0) }
                .show()
        }
    }

    // private fun checkModalBeforePOS() ... (SUDAH DIPINDAHKAN KE HELPER)
    // private fun showInputModalDialogForPOS() ... (SUDAH DIPINDAHKAN KE HELPER)

    private fun showCloseSessionDialog(session: android.content.SharedPreferences) {
        // 🔥 VERSI BARU: BUKA FULL SCREEN ACTIVITY
        startActivity(Intent(this, CloseShiftActivity::class.java))
    }

    private fun showInputCashDialog(
        expectedCash: Double, totalOmzet: Double, userName: String, startTime: Long,
        modal: Double, tunai: Double, qris: Double, trf: Double, debit: Double, piutang: Double,
        trxList: List<Transaction>
    ) {
        val input = EditText(this); input.inputType = InputType.TYPE_CLASS_NUMBER; input.hint = "Uang fisik di laci"

        AlertDialog.Builder(this).setTitle("Verifikasi Fisik").setView(input)
            .setPositiveButton("TUTUP & PRINT") { _, _ ->
                val actual = input.text.toString().toDoubleOrNull() ?: 0.0

                // 1. Simpan ke Database (Tetap pakai Username/Email biar aman/unik)
                viewModel.closeShift(userName, expectedCash, actual)

                // 2. 🔥 AMBIL NAMA ASLI UNTUK DI STRUK 🔥
                val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                // Kalau ada fullname pakai fullname, kalau kosong terpaksa pakai username
                val realName = session.getString("fullname", userName) ?: userName

                // 3. Kirim 'realName' ke fungsi print (Bukan userName lagi)
                printShiftReport(realName, startTime, modal, tunai, qris, trf, debit, piutang, expectedCash, actual, trxList)

                // 4. Hapus Shift & Logout
                val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
                shiftPrefs.edit().remove("IS_OPEN_GLOBAL_SESSION").remove("MODAL_AWAL_GLOBAL").remove("START_TIME_GLOBAL").apply()
                performLogout(getSharedPreferences("session_kasir", Context.MODE_PRIVATE), false)
            }.show()
    }

    private fun showResetConfirmation(session: android.content.SharedPreferences) {
        AlertDialog.Builder(this).setTitle("⚠️ RESET DATA").setMessage("Semua data akan dihapus permanen!")
            .setPositiveButton("HAPUS SEMUA") { _, _ -> performLogout(session, true) }
            .setNegativeButton("Batal", null).show()
    }

    private fun performLogout(session: android.content.SharedPreferences, resetData: Boolean) {
        session.edit().clear().apply()
        
        // 🔥 TAMBAHAN: CLEAR LICENSE & STORE CONFIG AGAR TIDAK BOCOR KE AKUN LAIN
        getSharedPreferences("app_license", Context.MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("store_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        if (resetData) {
            getSharedPreferences("shift_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            
            // 🔥 RESET CONFIG AGAR WELCOME SCREEN MUNCUL LAGI
            getSharedPreferences("app_config", Context.MODE_PRIVATE).edit().clear().apply()

            viewModel.logoutAndReset { signOutGoogleAndExit() }
        } else {
            // Walaupun tidak reset DB, kita harus refresh ViewModel/Repo agar memori bersih
            viewModel.logoutAndReset { signOutGoogleAndExit() }
        }
    }

    private fun signOutGoogleAndExit() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
            val i = Intent(this, LoginActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(i); finish()
        }
    }

    private fun printShiftReport(
        kasirName: String, startTime: Long, modal: Double, tunai: Double,
        qris: Double, trf: Double, debit: Double, piutang: Double,
        expected: Double, actual: Double,
        trxList: List<Transaction> // Pastikan parameter ini ada
    ) {
        val storePrefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val targetMac = storePrefs.getString("printer_mac", "")
        if (targetMac.isNullOrEmpty()) {
            Toast.makeText(this, "Printer belum disetting!", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            var socket: android.bluetooth.BluetoothSocket? = null
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return@Thread
                }

                val device = bluetoothAdapter.getRemoteDevice(targetMac)
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()

                val outputStream = socket!!.outputStream
                val storeName = storePrefs.getString("name", "Toko Saya")
                val openTimeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(startTime))
                val closeTimeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())

                // 1. HITUNG RINGKASAN KEUANGAN DARI LIST TRANSAKSI
                var shiftSubtotal = 0.0
                var shiftDiscount = 0.0
                var shiftTax = 0.0
                var shiftGrandTotal = 0.0

                // Hitung jumlah transaksi per metode
                var countTunai = 0; var countQris = 0; var countDebit = 0; var countTrf = 0; var countPiutang = 0
                
                // 🔥 ONLINE ORDER COUNTERS
                var goFoodTotal = 0.0; var goFoodCount = 0
                var grabTotal = 0.0; var grabCount = 0
                var shopeeTotal = 0.0; var shopeeCount = 0

                for (trx in trxList) {
                    shiftSubtotal += trx.subtotal
                    shiftDiscount += trx.discount
                    shiftTax += trx.tax
                    shiftGrandTotal += trx.totalAmount

                    val m = trx.paymentMethod.lowercase()

                    when {
                        m.contains("tunai") -> countTunai++
                        m.contains("qris") -> countQris++
                        m.contains("debit") -> countDebit++
                        m.contains("transfer") -> countTrf++
                        m.contains("piutang") -> countPiutang++
                        
                        // 🔥 Skip karena sudah dihitung di Order Online
                        m.contains("gofood") || m.contains("grab") || m.contains("shopee") -> {}
                        
                        else -> countTunai++
                    }
                    
                    // 🔥 HITUNG ORDER ONLINE
                    when (trx.orderType) {
                        "GoFood" -> { goFoodTotal += trx.totalAmount; goFoodCount++ }
                        "GrabFood" -> { grabTotal += trx.totalAmount; grabCount++ }
                        "ShopeeFood" -> { shopeeTotal += trx.totalAmount; shopeeCount++ }
                    }
                }

                val p = StringBuilder()

                // === HEADER ===
                p.append("\u001B\u0061\u0001") // Tengah
                p.append("\u001B\u0045\u0001$storeName\n\u001B\u0045\u0000")
                p.append("LAPORAN TUTUP SHIFT\n")
                p.append("--------------------------------\n")
                p.append("\u001B\u0061\u0000") // Kiri
                p.append("Kasir : $kasirName\n")
                p.append("Buka  : $openTimeStr\n")
                p.append("Tutup : $closeTimeStr\n")
                p.append("--------------------------------\n")

                // Helper Rupiah Ringkas
                fun row(kiri: String, valDbl: Double): String {
                    val kanan = formatRupiah(valDbl).replace("Rp ", "")
                    val maxChars = 32
                    val maxKiri = maxChars - kanan.length - 1
                    val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri
                    val sisa = maxChars - textKiri.length - kanan.length
                    return "$textKiri${" ".repeat(if(sisa>0) sisa else 1)}$kanan\n"
                }

                // === 1. LAPORAN LACI (CASH DRAWER) ===
                p.append("LACI KASIR (FISIK):\n")
                p.append(row("Modal Awal", modal))
                p.append(row("Penjualan Tunai", tunai))
                // Piutang Lunas (optional, jika ada logika pelunasan tunai di shift ini)
                p.append("--------------------------------\n")
                p.append(row("Total Sistem", modal + tunai))
                p.append(row("Fisik Laci", actual))

                val selisih = actual - (modal + tunai)
                if (selisih == 0.0) p.append("Selisih: KLOP (OK)\n")
                else p.append(row("Selisih", selisih))

                p.append("--------------------------------\n")

                // === 2. RINCIAN PEMBAYARAN (METODE) ===
                p.append("PEMBAYARAN DITERIMA:\n")
                if (tunai > 0) p.append(row("Tunai ($countTunai Trx)", tunai))
                if (qris > 0) p.append(row("QRIS ($countQris Trx)", qris))
                if (debit > 0) p.append(row("Debit ($countDebit Trx)", debit))
                if (trf > 0) p.append(row("Transfer ($countTrf Trx)", trf))
                if (piutang > 0) p.append(row("Piutang ($countPiutang Trx)", piutang))
                p.append("--------------------------------\n")
                
                // === 3. RINCIAN ORDER ONLINE (JIKA ADA) ===
                if (goFoodCount > 0 || grabCount > 0 || shopeeCount > 0) {
                     p.append("ORDER ONLINE:\n")
                     if(goFoodCount > 0) p.append(row("GoFood ($goFoodCount)", goFoodTotal))
                     if(grabCount > 0) p.append(row("GrabFood ($grabCount)", grabTotal))
                     if(shopeeCount > 0) p.append(row("ShopeeFood ($shopeeCount)", shopeeTotal))
                     p.append("--------------------------------\n")
                }

                // === 3. RINGKASAN PENJUALAN (OMZET) ===
                // Ini yang diminta: Subtotal, Diskon, Pajak
                p.append(row("Subtotal Jual", shiftSubtotal))
                if (shiftDiscount > 0) p.append(row("Total Diskon", -shiftDiscount)) // Minus biar jelas
                if (shiftTax > 0) p.append(row("Total Pajak", shiftTax))

                p.append("--------------------------------\n")
                p.append("\u001B\u0045\u0001${row("GRAND TOTAL", shiftGrandTotal)}\u001B\u0045\u0000")
                p.append("--------------------------------\n")

                p.append("\n\n\n") // Feed Akhir

                outputStream.write(p.toString().toByteArray())
                outputStream.flush()
                Thread.sleep(2500)
                socket.close()

            } catch (e: Exception) {
                try { socket?.close() } catch (x: Exception) {}
            }
        }.start()
    }

    private fun formatRupiah(amount: Double): String = String.format(Locale("id", "ID"), "Rp %,d", amount.toLong()).replace(',', '.')

    // --- SLIDER ADAPTER CLASSES ---
    data class RevenueItem(val title: String, val amount: Double, val colorHex: String)

    inner class RevenueSliderAdapter(private val items: List<RevenueItem>) : androidx.recyclerview.widget.RecyclerView.Adapter<RevenueSliderAdapter.SliderViewHolder>() {
        
        inner class SliderViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvSlideTitle)
            val tvValue: TextView = view.findViewById(R.id.tvSlideValue)
            val btnLeft: View = view.findViewById(R.id.imgNavLeft)
            val btnRight: View = view.findViewById(R.id.imgNavRight)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SliderViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_dashboard_slider, parent, false)
            return SliderViewHolder(view)
        }

        override fun onBindViewHolder(holder: SliderViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.tvValue.text = formatRupiah(item.amount)
            
            // 🔥 SET COLOR BASED ON ITEM TYPE
            holder.tvValue.setTextColor(android.graphics.Color.parseColor(item.colorHex))

            // Navigasi Arrow Logic
            holder.btnLeft.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
            holder.btnRight.visibility = if (position == items.size - 1) View.INVISIBLE else View.VISIBLE
            
            val vp = (holder.itemView.parent as? androidx.recyclerview.widget.RecyclerView)?.parent as? androidx.viewpager2.widget.ViewPager2
            holder.btnLeft.setOnClickListener { vp?.currentItem = position - 1 }
            holder.btnRight.setOnClickListener { vp?.currentItem = position + 1 }
        }

        override fun getItemCount(): Int = items.size
    }
}