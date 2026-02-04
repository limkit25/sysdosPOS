package com.sysdos.kasirpintar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.AppDatabase
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.client.http.FileContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class StoreSettingsActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    // NAMA DATABASE
    private val DB_NAME = "sysdos_pos_db"

    // UI Store
    private lateinit var etStoreName: EditText
    private lateinit var etStoreAddress: EditText
    private lateinit var etStorePhone: EditText
    private lateinit var etStoreFooter: EditText
    private lateinit var etStoreTax: EditText

    // 🔥 2. VARIABEL MARKUP ONLINE
    private var markupGoFood: Double = 20.0
    private var markupGrab: Double = 20.0
    private var markupShopee: Double = 20.0

    // 🔥 1. VARIABEL BARU: SWITCH STOK 🔥
    private lateinit var switchStock: androidx.appcompat.widget.SwitchCompat

    private lateinit var btnSave: Button
    private lateinit var layoutSaveBtn: LinearLayout

    // UI Printer
    private lateinit var btnScanPrinter: Button
    private lateinit var lvPrinters: ListView

    // UI Backup
    private lateinit var btnBackup: Button
    private lateinit var btnRestore: Button
    private lateinit var btnBackupDrive: Button

    // UI Lisensi
    private lateinit var tvLicenseStatus: TextView
    private lateinit var btnActivate: Button

    // CONTAINER KARTU
    private lateinit var cardSectionStore: CardView
    private lateinit var cardSectionPrinter: CardView
    private lateinit var cardSectionLicense: CardView

    // UI Umum
    private lateinit var tvTitle: TextView
    // private lateinit var btnBack: ImageButton (Removed)

    // Bluetooth Var
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val deviceList = ArrayList<String>()
    private val deviceMacs = ArrayList<String>()
    private lateinit var listAdapter: ArrayAdapter<String>
    // Drawer Removed


    private var isInitialSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store_settings)

        // =============================================================
        // 🔥 1. SETUP MENU SAMPING (DRAWER) -> REMOVED
        // =============================================================

        // Keep session check if needed for other things, but removing drawer logic
        val session = getSharedPreferences("session_kasir", android.content.Context.MODE_PRIVATE)
        val realName = session.getString("fullname", "Admin")
        val role = session.getString("role", "admin")


        // =============================================================
        // 🔥 2. KODINGAN LAMA MAS HERU (LANJUT DI BAWAH)
        // =============================================================

        // INIT VIEWMODEL
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // CEK INTENT
        isInitialSetup = intent.getBooleanExtra("IS_INITIAL_SETUP", false)
        val target = intent.getStringExtra("TARGET")

        // BIND VIEW
        tvTitle = findViewById(R.id.tvSettingsTitle)

        // ❌ HAPUS BARIS INI: btnBack = findViewById(R.id.btnBack) ❌
        // Karena tombolnya sudah tidak ada di XML.

        cardSectionStore = findViewById(R.id.cardSectionStore)
        cardSectionPrinter = findViewById(R.id.cardSectionPrinter)
        cardSectionLicense = findViewById(R.id.cardSectionLicense)

        layoutSaveBtn = findViewById(R.id.layoutSaveBtn)

        etStoreName = findViewById(R.id.etStoreName)
        etStoreAddress = findViewById(R.id.etStoreAddress)
        etStorePhone = findViewById(R.id.etStorePhone)
        etStoreFooter = findViewById(R.id.etStoreFooter)
        etStoreTax = findViewById(R.id.etStoreTax)

        switchStock = findViewById(R.id.switchStockSystem)
        
        // 🔥 INIT TOMBOL MARKUP
        findViewById<Button>(R.id.btnMarkupSettings).setOnClickListener { showMarkupDialog() }

        btnSave = findViewById(R.id.btnSaveStore)
        btnScanPrinter = findViewById(R.id.btnScanPrinter)
        lvPrinters = findViewById(R.id.lvPrinters)

        btnBackup = findViewById(R.id.btnBackupDB)
        btnRestore = findViewById(R.id.btnRestoreDB)
        btnBackupDrive = findViewById(R.id.btnBackupDrive)

        tvLicenseStatus = findViewById(R.id.tvLicenseStatus)
        btnActivate = findViewById(R.id.btnActivateLicense)

        // LOAD DATA EXISTING
        viewModel.storeConfig.observe(this) { config ->
            if (config != null) {
                etStoreName.setText(config.storeName)
                etStoreAddress.setText(config.storeAddress)
                etStorePhone.setText(config.storePhone)
                etStoreFooter.setText(config.strukFooter)
                etStoreTax.setText(config.taxPercentage.toString().removeSuffix(".0"))
                
                // 🔥 LOAD MARKUP DATA
                markupGoFood = config.markupGoFood
                markupGrab = config.markupGrab
                markupShopee = config.markupShopee
            }
        }

        // LOAD PENGATURAN STOK

        val prefs = getSharedPreferences("store_prefs", android.content.Context.MODE_PRIVATE)
        val isStockActive = prefs.getBoolean("use_stock_system", true)
        switchStock.isChecked = isStockActive

        // SETUP ADAPTER & UI
        listAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        lvPrinters.adapter = listAdapter

        setupUI(target)
        setupUI(target)
        checkLicenseStatus()

        // 🔥 AUTO-UPDATE LICENCE FROM SERVER
        val sessionEmail = getSharedPreferences("session_kasir", Context.MODE_PRIVATE).getString("username", "") ?: ""
        if (sessionEmail.isNotEmpty()) {
            viewModel.checkServerLicense(sessionEmail)
        }

        viewModel.licenseStatus.observe(this) { msg: String ->
            // Update UI Realtime
            checkLicenseStatus()
        }

        // LISTENERS
        // ❌ HAPUS BARIS INI: btnBack.setOnClickListener { finish() } ❌

        btnSave.setOnClickListener { saveStoreSettings() }
        btnActivate.setOnClickListener { showActivationDialog() }
        btnBackupDrive.setOnClickListener { checkPremiumAndSyncDrive() }

        // Bluetooth Listener
        btnScanPrinter.setOnClickListener {
            if (checkPermission()) startDiscovery() else requestBTPermissions()
        }

        lvPrinters.setOnItemClickListener { _, _, position, _ ->
            if (checkPermission() && bluetoothAdapter?.isDiscovering == true) bluetoothAdapter?.cancelDiscovery()

            val selectedMac = deviceMacs[position]
            val rawName = deviceList[position].split("\n")[0].replace(" [✅ AKTIF]", "")

            prefs.edit().putString("printer_mac", selectedMac).apply()

            android.widget.Toast.makeText(this, "Printer Utama: $rawName", android.widget.Toast.LENGTH_SHORT).show()
            showPairedDevices()
        }

        setupBackupRestoreLogic()

        val filter = android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        if (target == "PRINTER" && checkPermission()) showPairedDevices()
    }
    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun setupUI(target: String?) {
        if (isInitialSetup) {
            tvTitle.text = "Lengkapi Profil Toko"
            // btnBack removed
            cardSectionPrinter.visibility = View.GONE
            cardSectionLicense.visibility = View.GONE
            btnSave.text = "SIMPAN & MASUK APLIKASI"
            btnBackup.visibility = View.GONE
            btnRestore.visibility = View.VISIBLE
        }
        else if (target == "PRINTER") {
            tvTitle.text = "Koneksi Printer"
            cardSectionStore.visibility = View.GONE
            cardSectionLicense.visibility = View.GONE
            layoutSaveBtn.visibility = View.GONE
            btnBackup.visibility = View.GONE
            btnRestore.visibility = View.GONE
        }
        else {
            tvTitle.text = "Pengaturan Toko"
            cardSectionPrinter.visibility = View.GONE
            cardSectionLicense.visibility = View.VISIBLE
        }
    }

    private fun checkLicenseStatus() {
        val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
        val isFull = prefs.getBoolean("is_full_version", false)
        val expInfo = prefs.getString("expiration_info", "Full Version (Premium)")

        if (isFull) {
            // Tampilkan Detail Expiry (misal: "Aktif s/d ....")
            tvLicenseStatus.text = "Status: $expInfo ✅"
            tvLicenseStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            btnActivate.visibility = View.GONE
            btnBackup.isEnabled = true
            btnBackup.alpha = 1.0f
            btnBackup.text = "BACKUP DATABASE"
            btnBackupDrive.isEnabled = true
            btnBackupDrive.alpha = 1.0f
        } else {
            val trialMsg = prefs.getString("license_msg", "TRIAL (Terbatas) ⏳")
            tvLicenseStatus.text = "Status: $trialMsg"
            tvLicenseStatus.setTextColor(android.graphics.Color.parseColor("#E65100"))
            btnActivate.visibility = View.VISIBLE
            btnBackup.isEnabled = false
            btnBackup.alpha = 0.5f
            btnBackup.text = "BACKUP 🔒 (Premium)"
            btnBackupDrive.isEnabled = false
            btnBackupDrive.alpha = 0.5f
        }
    }

    private fun showActivationDialog() {
        val requestCode = (10000..99999).random()
        val input = EditText(this)
        input.hint = "Masukkan Token Jawaban"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Aktivasi Full Version 🔓")
            .setMessage("Kode Request HP Ini: $requestCode\n\nSilakan kirim kode di atas ke Developer.")
            .setView(input)
            .setPositiveButton("Aktifkan") { _, _ ->
                val tokenInput = input.text.toString().trim()
                val validToken = (requestCode + 11111).toString()

                if (tokenInput == validToken) {
                    val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_full_version", true).apply()
                    Toast.makeText(this, "Aktivasi Berhasil! Terima Kasih. 🎉", Toast.LENGTH_LONG).show()
                    checkLicenseStatus()
                } else {
                    Toast.makeText(this, "Token Salah!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupBackupRestoreLogic() {
        val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri ->
            if (uri != null) {
                try {
                    val dbFile = getDatabasePath(DB_NAME)
                    val inputStream = FileInputStream(dbFile)
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        Toast.makeText(this, "Backup Berhasil Disimpan! ✅", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal Backup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                AlertDialog.Builder(this)
                    .setTitle("Konfirmasi Restore")
                    .setMessage("PERINGATAN: Data saat ini akan DITIMPA/DIHAPUS dan diganti dengan data backup.\n\nAplikasi akan restart otomatis.\nLanjutkan?")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Ya, Timpa Data") { _, _ ->
                        try {
                            val dbFile = getDatabasePath(DB_NAME)
                            val dbWal = File(dbFile.parent, "$DB_NAME-wal")
                            val dbShm = File(dbFile.parent, "$DB_NAME-shm")
                            if (dbWal.exists()) dbWal.delete()
                            if (dbShm.exists()) dbShm.delete()

                            val inputStream = contentResolver.openInputStream(uri)
                            val outputStream = FileOutputStream(dbFile)

                            if (inputStream != null) {
                                inputStream.copyTo(outputStream)
                                inputStream.close()
                                outputStream.close()
                                Toast.makeText(this, "Restore Berhasil! Restarting...", Toast.LENGTH_LONG).show()
                                val intent = packageManager.getLaunchIntentForPackage(packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(intent)
                                exitProcess(0)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Gagal Restore: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }.show()
            }
        }

        // LAUNCHERS EXPORT & IMPORT
        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        viewModel.allTransactions.observe(this) { list ->
                             if (list != null) {
                                 Toast.makeText(this, "Mengekspor ${list.size} Transaksi...", Toast.LENGTH_SHORT).show()
                                 
                                 Thread {
                                     try {
                                         com.sysdos.kasirpintar.utils.CsvExportHelper.exportTransactions(outputStream, list)
                                         runOnUiThread { Toast.makeText(this, "✅ Export Berhasil!", Toast.LENGTH_LONG).show() }
                                     } catch(e: Exception) {
                                         runOnUiThread { Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show() }
                                     }
                                 }.start()

                                 viewModel.allTransactions.removeObservers(this) // Stop observing after first trigger
                             }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                         Toast.makeText(this, "Mengimpor Produk...", Toast.LENGTH_SHORT).show()
                         viewModel.importProducts(inputStream) { msg ->
                             Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                         }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal Import: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnExportReport).setOnClickListener {
            val timeStamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            exportLauncher.launch("Laporan_Transaksi_$timeStamp.csv")
        }

        findViewById<Button>(R.id.btnImportProduct).setOnClickListener {
            importLauncher.launch(arrayOf("text/csv", "application/vnd.ms-excel"))
        }

        btnBackup.setOnClickListener {
            AppDatabase.getDatabase(this).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            backupLauncher.launch("Backup_Kasir_$timeStamp.db")
        }

        btnRestore.setOnClickListener {
            restoreLauncher.launch(arrayOf("application/*", "application/x-sqlite3", "application/octet-stream"))
        }
    }

    // 🔥 DIALOG ATUR MARKUP OLINE
    private fun showMarkupDialog() {
        // Layout Custom untuk Dialog
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        fun createInput(label: String, initialVal: Double): EditText {
            val tv = TextView(this).apply { 
                text = label
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val et = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(initialVal.toString().removeSuffix(".0"))
                hint = "Contoh: 20"
            }
            layout.addView(tv)
            layout.addView(et)
            // Kasih jarak dikit
            layout.addView(View(this@StoreSettingsActivity).apply { layoutParams = LinearLayout.LayoutParams(-1, 20) }) 
            return et
        }

        val etGoFood = createInput("Markup GoFood (%)", markupGoFood)
        val etGrab = createInput("Markup GrabFood (%)", markupGrab)
        val etShopee = createInput("Markup ShopeeFood (%)", markupShopee)

        AlertDialog.Builder(this)
            .setTitle("Atur Markup Online")
            .setView(layout)
            .setPositiveButton("SIMPAN SEMENTARA") { _, _ ->
                markupGoFood = etGoFood.text.toString().toDoubleOrNull() ?: 0.0
                markupGrab = etGrab.text.toString().toDoubleOrNull() ?: 0.0
                markupShopee = etShopee.text.toString().toDoubleOrNull() ?: 0.0
                
                Toast.makeText(this, "Markup diubah (Klik SIMPAN PENGATURAN untuk permanen)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveStoreSettings() {
        val name = etStoreName.text.toString().trim()
        val address = etStoreAddress.text.toString().trim()
        val phone = etStorePhone.text.toString().trim()
        val footer = etStoreFooter.text.toString().trim()
        val taxStr = etStoreTax.text.toString().trim()
        val tax = taxStr.toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) {
            etStoreName.error = "Nama Toko Wajib Diisi"
            return
        }

        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("name", name)
        editor.putString("address", address)
        editor.putString("phone", phone)
        editor.putString("email", footer)

        // 🔥 4. SIMPAN STATUS SWITCH KE SHARED PREFERENCES 🔥
        editor.putBoolean("use_stock_system", switchStock.isChecked)

        val printerMac = prefs.getString("printer_mac", "")
        editor.putString("printer_mac", printerMac)

        editor.apply()

        // 🔥 PASS SEMUA PARAMETER KE VIEWMODEL
        viewModel.saveStoreSettings(name, address, phone, footer, printerMac, tax, markupGoFood, markupGrab, markupShopee)

        if (isInitialSetup) {
            Toast.makeText(this, "Setup Selesai! Selamat Datang.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Pengaturan Disimpan!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showPairedDevices() {
        if (!checkPermission()) return
        deviceList.clear()
        deviceMacs.clear()

        val activeMac = getSharedPreferences("store_prefs", Context.MODE_PRIVATE).getString("printer_mac", "")
        val pairedDevices = bluetoothAdapter?.bondedDevices

        if (!pairedDevices.isNullOrEmpty()) {
            for (device in pairedDevices) {
                val name = device.name ?: "Unknown"
                val mac = device.address
                val displayName = if (mac == activeMac) "$name [✅ AKTIF]" else name
                deviceList.add("$displayName\n$mac")
                deviceMacs.add(mac)
            }
        } else {
            deviceList.add("Tidak ada perangkat Bluetooth tersimpan")
            deviceMacs.add("")
        }
        listAdapter.notifyDataSetChanged()
    }

    private fun startDiscovery() {
        if (bluetoothAdapter == null) return
        Toast.makeText(this, "Mencari Printer...", Toast.LENGTH_SHORT).show()
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        bluetoothAdapter.startDiscovery()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (checkPermission()) {
                        val name = it.name
                        val mac = it.address
                        if (name != null && !deviceMacs.contains(mac)) {
                            deviceList.add("$name\n$mac")
                            deviceMacs.add(mac)
                            listAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }

    // 🔥 PERBAIKAN: PENGECEKAN LEBIH LENGKAP 🔥
    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Wajib Connect DAN Scan
            val connect = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val scan = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            return connect && scan
        } else {
            // Android 11 ke bawah: Wajib Location
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBTPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), 101)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ), 102)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }
    // 🔥 GOOGLE DRIVE LOGIC 🔥
    
    private val driveSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            processDriveUpload()
        } else {
            Toast.makeText(this, "Login Google Gagal / Dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPremiumAndSyncDrive() {
        val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_full_version", false)) {
            Toast.makeText(this, "Fitur ini khusus Premium! 🔒", Toast.LENGTH_SHORT).show()
            return
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            processDriveUpload()
        } else {
            requestDriveSignIn()
        }
    }

    private fun requestDriveSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        driveSignInLauncher.launch(client.signInIntent)
    }

    private fun processDriveUpload() {
        val progress = android.app.ProgressDialog(this)
        progress.setMessage("Mengupload Database ke Google Drive...")
        progress.setCancelable(false)
        progress.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(this@StoreSettingsActivity)
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@StoreSettingsActivity, Collections.singleton(DriveScopes.DRIVE_FILE)
                )
                credential.selectedAccount = account?.account

                val driveService = Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory(),
                    credential
                ).setApplicationName("Kasir Pintar").build()

                val dbFile = getDatabasePath(DB_NAME)
                
                // FORCE WAL CHECKPOINT
                AppDatabase.getDatabase(this@StoreSettingsActivity).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")

                val fileMetadata = com.google.api.services.drive.model.File()
                fileMetadata.name = "backup_kasir_${System.currentTimeMillis()}.db"
                fileMetadata.mimeType = "application/x-sqlite3"

                val mediaContent = FileContent("application/x-sqlite3", dbFile)

                val file = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()

                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(this@StoreSettingsActivity, "Backup Terupload! ✅\nID: ${file.id}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace() // 🔥 PRINT LOG KE LOGCAT
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(this@StoreSettingsActivity, "Gagal Upload: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 🔥 WAJIB TAMBAH INI BIAR GAK CRASH 🔥
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101 || requestCode == 102) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Izin diberikan, coba load devices
                Toast.makeText(this, "Izin Diberikan! ✅", Toast.LENGTH_SHORT).show()
                showPairedDevices()
            } else {
                Toast.makeText(this, "Izin Ditolak! Tidak bisa scan printer.", Toast.LENGTH_LONG).show()
            }
        }
    }
}