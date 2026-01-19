package com.sysdos.kasirpintar

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope // 🔥 Wajib ada ini
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.StockLog
import com.sysdos.kasirpintar.data.model.Supplier
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers // 🔥 Pastikan cuma satu ini
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class ProductListActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navView: com.google.android.material.navigation.NavigationView

    // 1. LAUNCHER PILIH FILE
    private val pickCsvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadCsvFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // =============================================================
        // 🔥 BAGIAN BARU: LOGIKA MENU SAMPING (DRAWER)
        // =============================================================

        // 1. Inisialisasi
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        // Perhatikan ID-nya harus sesuai XML baru (btnMenuDrawer)
        val btnMenu = findViewById<android.view.View>(R.id.btnMenuDrawer)

        // 2. Klik Tombol Burger -> Buka Menu
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        // 3. Setup Header Menu (Nama User)
        val session = getSharedPreferences("session_kasir", android.content.Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")

        if (navView.headerCount > 0) {
            val header = navView.getHeaderView(0)
            header.findViewById<android.widget.TextView>(R.id.tvHeaderName).text = realName
            header.findViewById<android.widget.TextView>(R.id.tvHeaderRole).text = "Role: ${role?.uppercase()}"
        }

        // 4. Logika Pindah Halaman saat Menu Diklik
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(android.content.Intent(this, DashboardActivity::class.java))
                    finish()
                }
                R.id.nav_kasir -> {
                    startActivity(android.content.Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_stok -> {
                    // Kita sudah di halaman Stok, tutup drawer aja
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                }
                R.id.nav_laporan -> startActivity(android.content.Intent(this, SalesReportActivity::class.java))
                R.id.nav_user -> startActivity(android.content.Intent(this, UserListActivity::class.java))
                // ... tambahkan menu lain jika perlu ...
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        // =============================================================
        // 🔥 KODINGAN LAMA ANDA (IMPORT & TABS) - LANJUT DISINI
        // =============================================================

        // Tombol Import
        val btnImport = findViewById<android.widget.ImageButton>(R.id.btnImportCsv)
        btnImport?.setOnClickListener {
            showImportOptionDialog()
        }

        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)

        // Adapter Tab
        viewPager.adapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): androidx.fragment.app.Fragment {
                return when (position) {
                    0 -> ProductFragment()
                    1 -> ReportFragment() // Pastikan Fragment ini ada
                    else -> ProductFragment()
                }
            }
        }

        // Judul Tab
        com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "PRODUK"
                1 -> "ASET/STOK"
                else -> ""
            }
        }.attach()

        // Buka Tab Spesifik
        val targetTab = intent.getIntExtra("OPEN_TAB_INDEX", -1)
        if (targetTab != -1 && targetTab < 2) {
            viewPager.setCurrentItem(targetTab, false)
        }
    }

    // =================================================================
    // 🔥 3. DIALOG PILIHAN (Download Template / Upload)
    // =================================================================
    private fun showImportOptionDialog() {
        val options = arrayOf("⬇️ Download Template CSV", "📂 Upload File CSV")

        AlertDialog.Builder(this)
            .setTitle("Import Barang")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveTemplateToDownloads() // Download Template
                    1 -> pickCsvLauncher.launch("text/*") // Upload File
                }
            }
            .show()
    }

    // =================================================================
    // 🔥 FUNGSI DOWNLOAD TEMPLATE (UPDATE: PAKAI NOTIFIKASI)
    // =================================================================
    private fun saveTemplateToDownloads() {
        val header = "Nama Produk,Kategori,Harga Modal,Harga Jual,Stok,Barcode,Nama Induk (Khusus Varian)"
        val row1 = "Nasi Goreng,Makanan,10000,15000,100,89911,"
        val row2 = "Telur,Topping,2000,3000,50,,Nasi Goreng"

        val csvContent = "$header\n$row1\n$row2"
        val fileName = "template_produk_varian_sysdos.csv"

        try {
            var fileUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // --- ANDROID 10 KE ATAS ---
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                    }
                    fileUri = uri // Simpan URI untuk notifikasi
                }
            } else {
                // --- ANDROID 9 KE BAWAH ---
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }

                // Konversi File ke URI pakai FileProvider (Biar aman & bisa dibuka dari notif)
                fileUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )
            }

            // 🔥 TAMPILKAN NOTIFIKASI JIKA SUKSES
            if (fileUri != null) {
                showDownloadNotification(fileUri!!, fileName)
                Toast.makeText(this, "✅ Template tersimpan! Cek Notifikasi diatas.", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal simpan template: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }


    // =================================================================
    // 🔥 5. FUNGSI UPLOAD CERDAS (SUPPORT VARIAN)
    // =================================================================
    private fun uploadCsvFromUri(uri: Uri) {
        // Tampilkan Loading
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage("Sedang mengimport data...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Jalankan di Background biar HP ga nge-lag
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))

                var line: String?
                var successCount = 0
                var failCount = 0

                // --- BACA ISI CSV BARIS PER BARIS ---
                while (reader.readLine().also { line = it } != null) {
                    try {
                        // Lewati baris header jika ada kata "Nama Produk"
                        if (line!!.contains("Nama Produk") && line!!.contains("Harga")) continue

                        // Pisahkan pakai Koma (Standar CSV)
                        val tokens = line!!.split(",")

                        // Pastikan kolomnya cukup (Minimal 6 kolom utama)
                        if (tokens.size >= 5) { // Sesuaikan dengan template Anda
                            val name = tokens[0].trim()

                            // Skip jika nama kosong
                            if (name.isEmpty()) continue

                            // Parse Angka (Handle error jika bukan angka)
                            val category = tokens[1].trim()
                            val cost = tokens[2].trim().toDoubleOrNull() ?: 0.0
                            val price = tokens[3].trim().toDoubleOrNull() ?: 0.0
                            val stock = tokens[4].trim().toIntOrNull() ?: 0

                            val barcode = if (tokens.size >= 6) tokens[5].trim() else ""

                            // 🔥 KOLOM 7: NAMA INDUK (Untuk Varian)
                            val parentName = if (tokens.size >= 7) tokens[6].trim() else ""

                            // --- LOGIKA VARIAN ---
                            var parentId = 0 // Default 0 = Induk

                            if (parentName.isNotEmpty()) {
                                // Kalau kolom Induk diisi, berarti ini VARIAN
                                // Cari ID Bapaknya di database berdasarkan Nama
                                val db = com.sysdos.kasirpintar.data.AppDatabase.getDatabase(this@ProductListActivity)
                                val parentProduct = db.productDao().getProductByName(parentName)

                                if (parentProduct != null) {
                                    parentId = parentProduct.id
                                } else {
                                    // Bapaknya belum ada? Ya sudah jadi Induk sendiri aja
                                    parentId = 0
                                }
                            }

                            // Buat Objek Produk Baru
                            val newProduct = Product(
                                name = name,
                                price = price,
                                costPrice = cost,
                                stock = stock,
                                category = if (category.isEmpty()) "Umum" else category,
                                barcode = barcode,
                                parentId = parentId, // 0 = Induk, >0 = Varian
                                imagePath = null
                            )

                            // Simpan ke Database
                            viewModel.insert(newProduct)
                            successCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failCount++
                    }
                }
                reader.close()
                inputStream?.close()

                // Update UI setelah selesai
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ProductListActivity,
                        "✅ Selesai! Berhasil: $successCount, Gagal: $failCount",
                        Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ProductListActivity, "❌ Gagal Import: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- FUNGSI RESTOCK MANUAL (Biarkan tetap ada) ---
    fun showRestockDialog(product: Product) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        // 1. SPINNER SUPPLIER
        val spnSupplier = Spinner(this)
        var supplierList: List<Supplier> = emptyList()

        viewModel.allSuppliers.observe(this) { suppliers ->
            supplierList = suppliers
            val names = suppliers.map { it.name }.toMutableList()
            names.add(0, "-- Pilih Supplier --")
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            spnSupplier.adapter = adapter
        }

        // 2. INPUT JUMLAH & HARGA
        val etQty = EditText(this).apply {
            hint = "Jumlah Masuk (Qty)"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etCost = EditText(this).apply {
            hint = "Harga Beli Baru"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(product.costPrice.toInt().toString())
        }

        layout.addView(TextView(this).apply { text = "Supplier:"; textSize = 12f })
        layout.addView(spnSupplier)
        layout.addView(TextView(this).apply { text = "Jumlah:"; textSize = 12f; setPadding(0,20,0,0) })
        layout.addView(etQty)
        layout.addView(TextView(this).apply { text = "Harga Modal (Update):"; textSize = 12f; setPadding(0,20,0,0) })
        layout.addView(etCost)

        AlertDialog.Builder(this)
            .setTitle("Restock: ${product.name}")
            .setView(layout)
            .setPositiveButton("SIMPAN") { _, _ ->
                val qtyStr = etQty.text.toString()
                if (qtyStr.isNotEmpty()) {
                    val qtyIn = qtyStr.toInt()
                    val newCost = etCost.text.toString().toDoubleOrNull() ?: product.costPrice

                    val selectedPos = spnSupplier.selectedItemPosition
                    val supplierName = if (selectedPos > 0 && selectedPos - 1 < supplierList.size) {
                        supplierList[selectedPos - 1].name
                    } else {
                        product.supplier ?: "Umum"
                    }

                    val newProduct = product.copy(
                        stock = product.stock + qtyIn,
                        costPrice = newCost,
                        supplier = supplierName
                    )
                    viewModel.update(newProduct)

                    val generatedPurchaseId = System.currentTimeMillis()
                    val log = StockLog(
                        purchaseId = generatedPurchaseId,
                        timestamp = generatedPurchaseId,
                        productName = product.name,
                        supplierName = supplierName,
                        quantity = qtyIn,
                        costPrice = newCost,
                        totalCost = qtyIn * newCost,
                        type = "IN"
                    )
                    viewModel.recordPurchase(log)

                    Toast.makeText(this, "Stok Berhasil Ditambah! 📦", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Jumlah wajib diisi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    // =================================================================
    // 🔥 6. FUNGSI MENAMPILKAN NOTIFIKASI DI STATUS BAR
    // =================================================================
    private fun showDownloadNotification(uri: Uri, fileName: String) {
        val channelId = "download_channel_sysdos"
        val notificationId = 101

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Buat Channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "File Unduhan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat download template selesai"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Buat Intent
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/csv")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Rakit Notifikasi
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Selesai")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 🔥 4. CEK IZIN (SOLUSI MERAHNYA) 🔥
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Kalau izin belum dikasih, kita skip dulu notifikasinya (biar gak crash)
                // Atau idealnya kita minta izin disini, tapi skip dulu biar simpel.
                return
            }
        }

        // Sekarang baris ini tidak akan merah lagi
        notificationManager.notify(notificationId, builder.build())
    }
    override fun onBackPressed() {
        if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}