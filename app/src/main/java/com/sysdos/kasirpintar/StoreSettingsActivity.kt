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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat

class StoreSettingsActivity : AppCompatActivity() {

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

    // UI Umum
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var cardSectionStore: CardView
    private lateinit var cardSectionPrinter: CardView

    // Bluetooth Var
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val deviceList = ArrayList<String>()
    private val deviceMacs = ArrayList<String>()
    private lateinit var listAdapter: ArrayAdapter<String>

    // Logic Vars
    private var isInitialSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store_settings)

        // 1. CEK INTENT (Dari mana asal user?)
        isInitialSetup = intent.getBooleanExtra("IS_INITIAL_SETUP", false)
        val target = intent.getStringExtra("TARGET") // "STORE", "PRINTER", atau null

        // 2. BIND VIEW
        tvTitle = findViewById(R.id.tvSettingsTitle)
        btnBack = findViewById(R.id.btnBack)

        cardSectionStore = findViewById(R.id.cardSectionStore)
        cardSectionPrinter = findViewById(R.id.cardSectionPrinter)
        layoutSaveBtn = findViewById(R.id.layoutSaveBtn)

        etStoreName = findViewById(R.id.etStoreName)
        etStoreAddress = findViewById(R.id.etStoreAddress)
        etStorePhone = findViewById(R.id.etStorePhone)
        etStoreFooter = findViewById(R.id.etStoreFooter)

        btnSave = findViewById(R.id.btnSaveStore)
        btnScanPrinter = findViewById(R.id.btnScanPrinter)
        lvPrinters = findViewById(R.id.lvPrinters)

        // 3. LOAD DATA TOKO
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        etStoreName.setText(prefs.getString("name", ""))
        etStoreAddress.setText(prefs.getString("address", ""))
        etStorePhone.setText(prefs.getString("phone", ""))
        etStoreFooter.setText(prefs.getString("email", "Terima Kasih!"))

        // 4. SETUP LIST PRINTER
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        lvPrinters.adapter = listAdapter

        // 5. ATUR TAMPILAN BERDASARKAN KONDISI
        setupUI(target)

        // 6. LISTENER TOMBOL
        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener { saveStoreSettings() }

        btnScanPrinter.setOnClickListener {
            if (checkPermission()) startDiscovery() else requestBTPermissions()
        }

        // 7. LISTENER KLIK LIST PRINTER
        lvPrinters.setOnItemClickListener { _, _, position, _ ->
            if (checkPermission() && bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }

            val selectedMac = deviceMacs[position]
            val rawName = deviceList[position].split("\n")[0].replace(" [✅ AKTIF]", "")

            // Simpan MAC Printer Utama
            prefs.edit().putString("printer_mac", selectedMac).apply()
            Toast.makeText(this, "Printer Utama: $rawName", Toast.LENGTH_SHORT).show()

            // Refresh List agar centang pindah
            showPairedDevices()
        }

        // Register Bluetooth Receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        // Auto Load Paired jika di menu printer
        if (target == "PRINTER") {
            if (checkPermission()) showPairedDevices()
        }
    }

    private fun setupUI(target: String?) {
        if (isInitialSetup) {
            // MODE: SETUP AWAL (Login Pertama)
            tvTitle.text = "Setup Profil Toko"
            btnBack.visibility = View.GONE // Wajib isi, gak boleh back
            cardSectionPrinter.visibility = View.GONE // Fokus isi data toko dulu
            btnSave.text = "SIMPAN & MASUK DASHBOARD"
        }
        else if (target == "PRINTER") {
            // MODE: SETTING PRINTER (Dari Dashboard)
            tvTitle.text = "Koneksi Printer"
            cardSectionStore.visibility = View.GONE
            layoutSaveBtn.visibility = View.GONE // Printer auto save saat diklik
        }
        else {
            // MODE: EDIT INFO TOKO (Dari Dashboard)
            tvTitle.text = "Informasi Toko"
            cardSectionPrinter.visibility = View.GONE
            btnSave.text = "SIMPAN PERUBAHAN"
        }
    }

    private fun saveStoreSettings() {
        val name = etStoreName.text.toString()
        if (name.isEmpty()) {
            Toast.makeText(this, "Nama Toko Wajib Diisi!", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("name", name)
            putString("address", etStoreAddress.text.toString())
            putString("phone", etStorePhone.text.toString())
            putString("email", etStoreFooter.text.toString())
            apply()
        }

        if (isInitialSetup) {
            Toast.makeText(this, "Selamat Datang di Kasir Pintar! 🚀", Toast.LENGTH_LONG).show()
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Info Toko Disimpan!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- LOGIKA BLUETOOTH ---
    private fun showPairedDevices() {
        if (!checkPermission()) return
        deviceList.clear()
        deviceMacs.clear()

        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val activeMac = prefs.getString("printer_mac", "")

        val pairedDevices = bluetoothAdapter?.bondedDevices
        if (!pairedDevices.isNullOrEmpty()) {
            for (device in pairedDevices) {
                val name = device.name ?: "Unknown"
                val mac = device.address

                var displayName = name
                if (mac == activeMac) displayName = "$name [✅ AKTIF]"

                deviceList.add("$displayName\n$mac")
                deviceMacs.add(mac)
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
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (checkPermission()) {
                        val name = it.name
                        val mac = it.address
                        if (name != null && !deviceMacs.contains(mac)) {
                            val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
                            val activeMac = prefs.getString("printer_mac", "")

                            var displayName = name
                            if (mac == activeMac) displayName = "$name [✅ AKTIF]"

                            deviceList.add("$displayName\n$mac")
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
        try {
            if (checkPermission() && bluetoothAdapter?.isDiscovering == true) bluetoothAdapter.cancelDiscovery()
            unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }
}