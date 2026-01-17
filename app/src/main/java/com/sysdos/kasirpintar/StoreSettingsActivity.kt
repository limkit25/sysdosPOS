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

class StoreSettingsActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    // NAMA DATABASE
    private val DB_NAME = "sysdos_pos_db"

    // UI Store
    private lateinit var etStoreName: EditText
    private lateinit var etStoreAddress: EditText
    private lateinit var etStorePhone: EditText
    private lateinit var etStoreFooter: EditText
    private lateinit var etStoreTax: EditText // 🔥 1. VARIABEL BARU

    private lateinit var btnSave: Button
    private lateinit var layoutSaveBtn: LinearLayout

    // UI Printer
    private lateinit var btnScanPrinter: Button
    private lateinit var lvPrinters: ListView

    // UI Backup
    private lateinit var btnBackup: Button
    private lateinit var btnRestore: Button

    // UI Lisensi
    private lateinit var tvLicenseStatus: TextView
    private lateinit var btnActivate: Button

    // CONTAINER KARTU
    private lateinit var cardSectionStore: CardView
    private lateinit var cardSectionPrinter: CardView
    private lateinit var cardSectionLicense: CardView

    // UI Umum
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton

    // Bluetooth Var
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val deviceList = ArrayList<String>()
    private val deviceMacs = ArrayList<String>()
    private lateinit var listAdapter: ArrayAdapter<String>

    private var isInitialSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store_settings)

        // 1. INIT VIEWMODEL
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 2. CEK INTENT (Apakah ini pendaftaran awal?)
        isInitialSetup = intent.getBooleanExtra("IS_INITIAL_SETUP", false)
        val target = intent.getStringExtra("TARGET")

        // 3. BIND VIEW
        tvTitle = findViewById(R.id.tvSettingsTitle)
        btnBack = findViewById(R.id.btnBack)

        cardSectionStore = findViewById(R.id.cardSectionStore)
        cardSectionPrinter = findViewById(R.id.cardSectionPrinter)
        cardSectionLicense = findViewById(R.id.cardSectionLicense)

        layoutSaveBtn = findViewById(R.id.layoutSaveBtn)

        etStoreName = findViewById(R.id.etStoreName)
        etStoreAddress = findViewById(R.id.etStoreAddress)
        etStorePhone = findViewById(R.id.etStorePhone)
        etStoreFooter = findViewById(R.id.etStoreFooter)
        etStoreTax = findViewById(R.id.etStoreTax) // 🔥 2. BIND VIEW PAJAK

        btnSave = findViewById(R.id.btnSaveStore)
        btnScanPrinter = findViewById(R.id.btnScanPrinter)
        lvPrinters = findViewById(R.id.lvPrinters)

        btnBackup = findViewById(R.id.btnBackupDB)
        btnRestore = findViewById(R.id.btnRestoreDB)

        tvLicenseStatus = findViewById(R.id.tvLicenseStatus)
        btnActivate = findViewById(R.id.btnActivateLicense)

        // 4. LOAD DATA EXISTING (Jika ada)
        viewModel.storeConfig.observe(this) { config ->
            if (config != null) {
                etStoreName.setText(config.storeName)
                etStoreAddress.setText(config.storeAddress)
                etStorePhone.setText(config.storePhone)
                etStoreFooter.setText(config.strukFooter)

                // 🔥 3. LOAD DATA PAJAK DARI DB
                // Hapus '.0' di belakang angka biar rapi (Misal 11.0 jadi 11)
                etStoreTax.setText(config.taxPercentage.toString().removeSuffix(".0"))
            }
        }

        // 5. SETUP ADAPTER & UI
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        lvPrinters.adapter = listAdapter

        setupUI(target) // Atur tampilan berdasarkan mode (Setup Awal / Setting Biasa)
        checkLicenseStatus()

        // 6. LISTENERS
        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveStoreSettings() }
        btnActivate.setOnClickListener { showActivationDialog() }

        // Bluetooth Listener
        btnScanPrinter.setOnClickListener {
            if (checkPermission()) startDiscovery() else requestBTPermissions()
        }

        lvPrinters.setOnItemClickListener { _, _, position, _ ->
            if (checkPermission() && bluetoothAdapter?.isDiscovering == true) bluetoothAdapter.cancelDiscovery()

            val selectedMac = deviceMacs[position]
            val rawName = deviceList[position].split("\n")[0].replace(" [✅ AKTIF]", "")

            // Simpan Printer Mac ke Prefs
            val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("printer_mac", selectedMac).apply()

            Toast.makeText(this, "Printer Utama: $rawName", Toast.LENGTH_SHORT).show()
            showPairedDevices()
        }

        // Setup Backup/Restore
        setupBackupRestoreLogic()

        // Register Bluetooth Receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        // Jika mode hanya setting printer, langsung load device
        if (target == "PRINTER" && checkPermission()) showPairedDevices()
    }

    // --- LOGIKA TAMPILAN UI ---
    private fun setupUI(target: String?) {
        if (isInitialSetup) {
            // MODE: SETUP AWAL SETELAH REGISTER
            tvTitle.text = "Lengkapi Profil Toko"
            btnBack.visibility = View.GONE // Tidak boleh kembali, wajib isi
            cardSectionPrinter.visibility = View.GONE
            cardSectionLicense.visibility = View.GONE

            btnSave.text = "SIMPAN & MASUK APLIKASI"

            // Sembunyikan Backup, Tampilkan Restore (Kali aja mau restore data lama)
            btnBackup.visibility = View.GONE
            btnRestore.visibility = View.VISIBLE
        }
        else if (target == "PRINTER") {
            // MODE: HANYA SETTING PRINTER
            tvTitle.text = "Koneksi Printer"
            cardSectionStore.visibility = View.GONE
            cardSectionLicense.visibility = View.GONE
            layoutSaveBtn.visibility = View.GONE // Tidak perlu tombol simpan besar
            btnBackup.visibility = View.GONE
            btnRestore.visibility = View.GONE
        }
        else {
            // MODE: SETTING NORMAL DARI DASHBOARD
            tvTitle.text = "Pengaturan Toko"
            cardSectionPrinter.visibility = View.GONE // Printer ada menu sendiri biasanya
            cardSectionLicense.visibility = View.VISIBLE
        }
    }

    // --- LOGIKA LISENSI ---
    private fun checkLicenseStatus() {
        val prefs = getSharedPreferences("app_license", Context.MODE_PRIVATE)
        val isFull = prefs.getBoolean("is_full_version", false)

        if (isFull) {
            tvLicenseStatus.text = "Status: FULL VERSION (Premium) ✅"
            tvLicenseStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            btnActivate.visibility = View.GONE

            btnBackup.isEnabled = true
            btnBackup.alpha = 1.0f
            btnBackup.text = "BACKUP DATABASE"
        } else {
            tvLicenseStatus.text = "Status: TRIAL (Terbatas) ⏳"
            tvLicenseStatus.setTextColor(android.graphics.Color.parseColor("#E65100"))
            btnActivate.visibility = View.VISIBLE

            // Backup dikunci jika trial
            btnBackup.isEnabled = false
            btnBackup.alpha = 0.5f
            btnBackup.text = "BACKUP 🔒 (Premium)"
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
                val validToken = (requestCode + 11111).toString() // Logika sederhana

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

    // --- LOGIKA BACKUP & RESTORE ---
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

                            // Hapus file WAL & SHM (Temporary file SQLite) agar bersih
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

                                // Restart Aplikasi
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

        btnBackup.setOnClickListener {
            // Checkpoint DB dulu agar semua data masuk ke file utama .db
            AppDatabase.getDatabase(this).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            backupLauncher.launch("Backup_Kasir_$timeStamp.db")
        }

        btnRestore.setOnClickListener {
            restoreLauncher.launch(arrayOf("application/*", "application/x-sqlite3", "application/octet-stream"))
        }
    }

    // 🔥 LOGIKA SIMPAN & REDIRECT DASHBOARD 🔥
    private fun saveStoreSettings() {
        val name = etStoreName.text.toString().trim()
        val address = etStoreAddress.text.toString().trim()
        val phone = etStorePhone.text.toString().trim()
        val footer = etStoreFooter.text.toString().trim()

        // 🔥 4. AMBIL INPUT PAJAK
        val taxStr = etStoreTax.text.toString().trim()
        val tax = taxStr.toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) {
            etStoreName.error = "Nama Toko Wajib Diisi"
            return
        }

        // ============================================================
        // 🛠️ PERBAIKAN UTAMA: SIMPAN JUGA KE SHARED PREFERENCES 🛠️
        // Agar bisa dibaca oleh MainActivity & HistoryActivity saat nge-print
        // ============================================================
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Kunci (Key) ini HARUS SAMA dengan yang dipanggil di PrintStruk MainActivity
        editor.putString("name", name)       // Di printer panggilnya "name"
        editor.putString("address", address) // Di printer panggilnya "address"
        editor.putString("phone", phone)     // Di printer panggilnya "phone"
        editor.putString("email", footer)    // Di printer panggilnya "email" (dipakai buat footer)

        // Simpan Mac Address juga biar sekalian rapi (opsional, krn udah disave pas klik list)
        val printerMac = prefs.getString("printer_mac", "")
        editor.putString("printer_mac", printerMac)

        editor.apply() // 🔥 WAJIB DI-APPLY BIAR KESIMPAN
        // ============================================================

        // Simpan ke Database (ViewModel) - Ini untuk backup data jangka panjang
        viewModel.saveStoreSettings(name, address, phone, footer, printerMac, tax)

        if (isInitialSetup) {
            // 🔥 JIKA USER BARU DAFTAR -> MASUK DASHBOARD
            Toast.makeText(this, "Setup Selesai! Selamat Datang.", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            // JIKA HANYA EDIT SETTING BIASA -> TUTUP HALAMAN
            Toast.makeText(this, "Pengaturan Disimpan!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- BLUETOOTH HELPER ---
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
            deviceMacs.add("") // Dummy
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

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBTPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 101)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 102)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }
}