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
    // Drawer Removed


    // 1. LAUNCHER PILIH FILE
    private val pickCsvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadCsvFromUri(it) }
    }

    // 🔥 2. LAUNCHER IZIN NOTIFIKASI (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Izin diberikan, coba tampilkan notifikasi terakhir yang tertunda
            // (Opsional, atau biarkan user klik lagi)
            Toast.makeText(this, "Izin diberikan! Notifikasi akan muncul.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Izin notifikasi ditolak. File tetap tersimpan di Download.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // =============================================================
        // 🔥 BAGIAN BARU: LOGIKA MENU SAMPING (DRAWER) -> REMOVED IN FAVOR OF BOTTOM NAV
        // =============================================================
        // Drawer Code Removed.



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

        // 🔥 SETUP BOTTOM NAV
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        com.sysdos.kasirpintar.utils.BottomNavHelper.setup(this, bottomNav, viewModel)
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
        val header = "Nama Produk,Kategori,Harga Modal,Harga Jual,Stok,Satuan,Barcode,Nama Induk (Khusus Varian)"
        val row1 = "Nasi Goreng,Makanan,10000,15000,100,Porsi,89911,"
        val row2 = "Pedas,,10500,16000,0,,,"
        val row3 = "Telur,Topping,2000,3000,50,Pcs,,Nasi Goreng"

        val csvContent = "$header\n$row1\n$row2\n$row3"
        val fileName = "template_produk_sysdos.csv"

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

        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                progressDialog.dismiss()
                Toast.makeText(this, "Gagal membuka file!", Toast.LENGTH_SHORT).show()
                return
            }

            // 🔥 PANGGIL VIEWMODEL untuk IMPORT (Background)
            viewModel.importProducts(inputStream) { resultMsg ->
                progressDialog.dismiss()
                Toast.makeText(this, resultMsg, Toast.LENGTH_LONG).show()
                
                // Refresh jika sukses
                if (resultMsg.contains("Sukses")) {
                    viewModel.syncData()
                }
            }

        } catch (e: Exception) {
            progressDialog.dismiss()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                // MINTA IZIN DULU
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // Sekarang baris ini tidak akan merah lagi
        notificationManager.notify(notificationId, builder.build())
    }
    override fun onResume() {
        super.onResume()
        // Panggil fungsi yang sudah kita ubah tadi
        viewModel.checkAndSync()
    }
    override fun onBackPressed() {
        super.onBackPressed()
    }
}