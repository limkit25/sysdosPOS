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
import com.sysdos.kasirpintar.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class StoreSettingsActivity : AppCompatActivity() {

    // NAMA DB
    private val DB_NAME = "sysdos_pos_db"

    // UI Store
    private lateinit var etStoreName: EditText
    private lateinit var etStoreAddress: EditText
    private lateinit var etStorePhone: EditText
    private lateinit var etStoreFooter: EditText
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

    // 🔥 KARTU-KARTU (Container) 🔥
    private lateinit var cardSectionStore: CardView
    private lateinit var cardSectionPrinter: CardView
    private lateinit var cardSectionLicense: CardView // INI YANG BARU

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

        // 1. CEK INTENT
        isInitialSetup = intent.getBooleanExtra("IS_INITIAL_SETUP", false)
        val target = intent.getStringExtra("TARGET")

        // 2. BIND VIEW
        tvTitle = findViewById(R.id.tvSettingsTitle)
        btnBack = findViewById(R.id.btnBack)

        // Kartu Sections
        cardSectionStore = findViewById(R.id.cardSectionStore)
        cardSectionPrinter = findViewById(R.id.cardSectionPrinter)
        cardSectionLicense = findViewById(R.id.cardSectionLicense) // Bind ID baru

        layoutSaveBtn = findViewById(R.id.layoutSaveBtn)

        etStoreName = findViewById(R.id.etStoreName)
        etStoreAddress = findViewById(R.id.etStoreAddress)
        etStorePhone = findViewById(R.id.etStorePhone)
        etStoreFooter = findViewById(R.id.etStoreFooter)

        btnSave = findViewById(R.id.btnSaveStore)
        btnScanPrinter = findViewById(R.id.btnScanPrinter)
        lvPrinters = findViewById(R.id.lvPrinters)

        btnBackup = findViewById(R.id.btnBackupDB)
        btnRestore = findViewById(R.id.btnRestoreDB)

        tvLicenseStatus = findViewById(R.id.tvLicenseStatus)
        btnActivate = findViewById(R.id.btnActivateLicense)

        // 3. LOAD DATA
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        etStoreName.setText(prefs.getString("name", ""))
        etStoreAddress.setText(prefs.getString("address", ""))
        etStorePhone.setText(prefs.getString("phone", ""))
        etStoreFooter.setText(prefs.getString("email", "Terima Kasih!"))

        // 4. SETUP UI
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        lvPrinters.adapter = listAdapter

        setupUI(target) // 🔥 DISINI LOGIKA SEMBUNYIKAN KARTU

        checkLicenseStatus()

        // 5. LISTENERS
        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveStoreSettings() }
        btnActivate.setOnClickListener { showActivationDialog() }

        // Bluetooth
        btnScanPrinter.setOnClickListener {
            if (checkPermission()) startDiscovery() else requestBTPermissions()
        }

        lvPrinters.setOnItemClickListener { _, _, position, _ ->
            if (checkPermission() && bluetoothAdapter?.isDiscovering == true) bluetoothAdapter.cancelDiscovery()
            val selectedMac = deviceMacs[position]
            val rawName = deviceList[position].split("\n")[0].replace(" [✅ AKTIF]", "")
            prefs.edit().putString("printer_mac", selectedMac).apply()
            Toast.makeText(this, "Printer Utama: $rawName", Toast.LENGTH_SHORT).show()
            showPairedDevices()
        }

        // Backup Restore
        setupBackupRestoreLogic()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        if (target == "PRINTER" && checkPermission()) showPairedDevices()
    }

    // --- SETUP TAMPILAN (LOGIKA UTAMA) ---
    private fun setupUI(target: String?) {
        if (isInitialSetup) {
            // SETUP AWAL
            tvTitle.text = "Setup Profil Toko"
            btnBack.visibility = View.GONE
            cardSectionPrinter.visibility = View.GONE
            cardSectionLicense.visibility = View.GONE // Sembunyikan Lisensi dulu

            btnSave.text = "SIMPAN & MASUK DASHBOARD"
            btnBackup.visibility = View.GONE
            btnRestore.visibility = View.VISIBLE
        }
        else if (target == "PRINTER") {
            // MENU PRINTER
            tvTitle.text = "Koneksi Printer"
            cardSectionStore.visibility = View.GONE   // Sembunyikan Toko
            cardSectionLicense.visibility = View.GONE // 🔥 SEMBUNYIKAN LISENSI 🔥

            layoutSaveBtn.visibility = View.GONE
            btnBackup.visibility = View.GONE
            btnRestore.visibility = View.GONE
        }
        else {
            // MENU TOKO (DEFAULT)
            tvTitle.text = "Pengaturan Toko"
            cardSectionPrinter.visibility = View.GONE // Sembunyikan Printer
            cardSectionLicense.visibility = View.VISIBLE // TAMPILKAN LISENSI DISINI
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
        } else {
            tvLicenseStatus.text = "Status: TRIAL (Terbatas) ⏳"
            tvLicenseStatus.setTextColor(android.graphics.Color.parseColor("#E65100"))
            btnActivate.visibility = View.VISIBLE
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
                // Rumus: Request + 11111
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

    // --- SETUP BACKUP & RESTORE ---
    private fun setupBackupRestoreLogic() {
        val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri ->
            if (uri != null) {
                try {
                    val dbFile = getDatabasePath(DB_NAME)
                    val inputStream = FileInputStream(dbFile)
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        inputStream.copyTo(outputStream); inputStream.close(); outputStream.close()
                        Toast.makeText(this, "Backup Berhasil! ✅", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) { Toast.makeText(this, "Gagal Backup", Toast.LENGTH_SHORT).show() }
            }
        }

        val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                AlertDialog.Builder(this)
                    .setTitle("Konfirmasi Restore")
                    .setMessage("Data saat ini akan DITIMPA. Aplikasi akan restart.\nLanjutkan?")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Ya") { _, _ ->
                        try {
                            val dbFile = getDatabasePath(DB_NAME)
                            val dbWal = File(dbFile.parent, "$DB_NAME-wal"); if (dbWal.exists()) dbWal.delete()
                            val dbShm = File(dbFile.parent, "$DB_NAME-shm"); if (dbShm.exists()) dbShm.delete()

                            val inputStream = contentResolver.openInputStream(uri)
                            val outputStream = FileOutputStream(dbFile)
                            if (inputStream != null) {
                                inputStream.copyTo(outputStream); inputStream.close(); outputStream.close()
                                Toast.makeText(this, "Berhasil! Restarting...", Toast.LENGTH_LONG).show()
                                val intent = packageManager.getLaunchIntentForPackage(packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(intent); exitProcess(0)
                            }
                        } catch (e: Exception) { Toast.makeText(this, "Gagal Restore", Toast.LENGTH_SHORT).show() }
                    }.show()
            }
        }

        btnBackup.setOnClickListener {
            AppDatabase.getDatabase(this).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
            val timeStamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            backupLauncher.launch("Backup_Kasir_$timeStamp.db")
        }

        btnRestore.setOnClickListener {
            restoreLauncher.launch(arrayOf("application/*", "application/x-sqlite3", "application/octet-stream"))
        }
    }

    private fun saveStoreSettings() {
        val name = etStoreName.text.toString()
        if (name.isEmpty()) { Toast.makeText(this, "Nama Toko Wajib Diisi!", Toast.LENGTH_SHORT).show(); return }

        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("name", name)
            putString("address", etStoreAddress.text.toString())
            putString("phone", etStorePhone.text.toString())
            putString("email", etStoreFooter.text.toString())
            apply()
        }

        if (isInitialSetup) {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent); finish()
        } else {
            Toast.makeText(this, "Info Toko Disimpan!", Toast.LENGTH_SHORT).show(); finish()
        }
    }

    // --- BLUETOOTH METHODS ---
    private fun showPairedDevices() {
        if (!checkPermission()) return
        deviceList.clear(); deviceMacs.clear()
        val activeMac = getSharedPreferences("store_prefs", Context.MODE_PRIVATE).getString("printer_mac", "")
        val pairedDevices = bluetoothAdapter?.bondedDevices
        if (!pairedDevices.isNullOrEmpty()) {
            for (device in pairedDevices) {
                val name = device.name ?: "Unknown"; val mac = device.address
                val displayName = if (mac == activeMac) "$name [✅ AKTIF]" else name
                deviceList.add("$displayName\n$mac"); deviceMacs.add(mac)
            }
        }
        listAdapter.notifyDataSetChanged()
    }

    private fun startDiscovery() {
        if (bluetoothAdapter == null) return
        Toast.makeText(this, "Mencari...", Toast.LENGTH_SHORT).show()
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        bluetoothAdapter.startDiscovery()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (checkPermission()) {
                        val name = it.name; val mac = it.address
                        if (name != null && !deviceMacs.contains(mac)) {
                            deviceList.add("$name\n$mac"); deviceMacs.add(mac)
                            listAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED else ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBTPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 101) else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 102)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }
}