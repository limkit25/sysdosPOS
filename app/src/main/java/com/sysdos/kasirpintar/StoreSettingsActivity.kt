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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat

class StoreSettingsActivity : AppCompatActivity() {

    private lateinit var etStoreName: EditText
    private lateinit var etStoreAddress: EditText
    private lateinit var etStorePhone: EditText
    private lateinit var etStoreFooter: EditText

    private lateinit var btnSave: Button
    private lateinit var btnScanPrinter: Button
    private lateinit var lvPrinters: ListView
    private lateinit var tvTitle: TextView

    private lateinit var cardSectionStore: CardView
    private lateinit var cardSectionPrinter: CardView

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val deviceList = ArrayList<String>()
    private val deviceMacs = ArrayList<String>()
    private lateinit var listAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store_settings)

        // Bind View
        tvTitle = findViewById(R.id.tvSettingsTitle)
        cardSectionStore = findViewById(R.id.cardSectionStore)
        cardSectionPrinter = findViewById(R.id.cardSectionPrinter)

        etStoreName = findViewById(R.id.etStoreName)
        etStoreAddress = findViewById(R.id.etStoreAddress)
        etStorePhone = findViewById(R.id.etStorePhone)
        etStoreFooter = findViewById(R.id.etStoreFooter)

        btnSave = findViewById(R.id.btnSaveStore)
        btnScanPrinter = findViewById(R.id.btnScanPrinter)
        lvPrinters = findViewById(R.id.lvPrinters)

        // --- CEK MODE TAMPILAN ---
        val target = intent.getStringExtra("TARGET")
        if (target == "STORE") {
            cardSectionPrinter.visibility = View.GONE
            tvTitle.text = "Info & Struk Toko"
        } else if (target == "PRINTER") {
            cardSectionStore.visibility = View.GONE
            tvTitle.text = "Koneksi Printer"
        }

        // Load Data Toko
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        etStoreName.setText(prefs.getString("name", ""))
        etStoreAddress.setText(prefs.getString("address", ""))
        etStorePhone.setText(prefs.getString("phone", ""))
        etStoreFooter.setText(prefs.getString("email", "Terima Kasih!"))

        // Setup List
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        lvPrinters.adapter = listAdapter

        // Tampilkan printer (hanya jika mode printer aktif atau default)
        if (target == "PRINTER" || target == null) {
            if (checkPermission()) showPairedDevices()
        }

        // SIMPAN INFO TOKO
        btnSave.setOnClickListener {
            prefs.edit().apply {
                putString("name", etStoreName.text.toString())
                putString("address", etStoreAddress.text.toString())
                putString("phone", etStorePhone.text.toString())
                putString("email", etStoreFooter.text.toString())
                apply()
            }
            Toast.makeText(this, "Info Toko & Struk Disimpan!", Toast.LENGTH_SHORT).show()
        }

        // CARI PRINTER
        btnScanPrinter.setOnClickListener {
            if (checkPermission()) {
                startDiscovery()
            } else {
                requestBTPermissions()
            }
        }

        // --- [MODIFIKASI] KLIK PILIH PRINTER ---
        lvPrinters.setOnItemClickListener { _, _, position, _ ->
            if (checkPermission() && bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }

            val selectedMac = deviceMacs[position]
            // Ambil nama dari list, buang tanda [✅] kalau ada biar bersih
            val rawName = deviceList[position].split("\n")[0].replace(" [✅ AKTIF]", "")

            // Simpan ke memori
            prefs.edit().putString("printer_mac", selectedMac).apply()

            Toast.makeText(this, "Printer Utama: $rawName", Toast.LENGTH_SHORT).show()

            // REFRESH LIST AGAR TANDA CENTANG PINDAH
            showPairedDevices()
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
    }

    // --- [MODIFIKASI] TAMPILKAN LIST DENGAN PENANDA ---
    private fun showPairedDevices() {
        if (!checkPermission()) return
        deviceList.clear()
        deviceMacs.clear()

        // Ambil MAC yang tersimpan saat ini
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val activeMac = prefs.getString("printer_mac", "")

        val pairedDevices = bluetoothAdapter?.bondedDevices
        if (!pairedDevices.isNullOrEmpty()) {
            for (device in pairedDevices) {
                val name = device.name ?: "Unknown"
                val mac = device.address

                // LOGIKA PENANDA: Jika mac sama, tambah teks [✅ AKTIF]
                var displayName = name
                if (mac == activeMac) {
                    displayName = "$name [✅ AKTIF]"
                }

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
                            // Cek juga untuk hasil scan
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