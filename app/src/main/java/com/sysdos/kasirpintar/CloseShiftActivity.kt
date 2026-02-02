package com.sysdos.kasirpintar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.text.SimpleDateFormat // 🔥 IMPORTANT IMPORT
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class CloseShiftActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private var allTrx: List<Transaction> = emptyList()

    // Data Shift
    private var modalAwal = 0.0
    private var totalTunai = 0.0
    private var totalQris = 0.0
    private var totalDebit = 0.0
    private var totalTransfer = 0.0
    private var piutangLunas = 0.0
    private var piutangBelumLunas = 0.0
    private var expectedCash = 0.0

    // Count
    private var countTunai = 0
    private var countQris = 0
    private var countDebit = 0
    private var countTransfer = 0
    private var countPiutang = 0
    
    // Void Data
    private var voidTrxCount = 0
    private var voidItemCount = 0

    // UI
    private lateinit var tvDifference: TextView
    private lateinit var etActual: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_close_shift)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // INIT VIEW
        val tvInfo = findViewById<TextView>(R.id.tvShiftInfo)
        val tvModal = findViewById<TextView>(R.id.tvModal)

        val tvTunai = findViewById<TextView>(R.id.tvTunai)
        val tvQris = findViewById<TextView>(R.id.tvQris)
        val tvDebit = findViewById<TextView>(R.id.tvDebit)
        val tvTransfer = findViewById<TextView>(R.id.tvTransfer)
        val tvPiutang = findViewById<TextView>(R.id.tvPiutang)
        val tvVoid = findViewById<TextView>(R.id.tvVoid)

        val tvExpected = findViewById<TextView>(R.id.tvExpected)

        etActual = findViewById(R.id.etActualCash)
        tvDifference = findViewById(R.id.tvDifference)

        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnClose = findViewById<Button>(R.id.btnCloseShift)

        // LOAD DATA
        loadShiftData {
            // Update UI
            val fmt = { d: Double -> String.format(Locale("id", "ID"), "Rp %,d", d.toLong()).replace(',', '.') }
            val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
            val name = session.getString("fullname", "User")
            val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
            val startTime = shiftPrefs.getLong("START_TIME_GLOBAL", System.currentTimeMillis())
            val dateStr = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(startTime))

            tvInfo.text = "Kasir: $name\nBuka: $dateStr\nTutup: Sekarang"
            tvModal.text = fmt(modalAwal)

            tvTunai.text = "${fmt(totalTunai)} ($countTunai Trx)"
            tvQris.text = "${fmt(totalQris)} ($countQris Trx)"
            tvDebit.text = "${fmt(totalDebit)} ($countDebit Trx)"
            tvTransfer.text = "${fmt(totalTransfer)} ($countTransfer Trx)"
            tvPiutang.text = "${fmt(piutangBelumLunas)} ($countPiutang Trx)"

            tvExpected.text = fmt(expectedCash)
        }
        
        // 🔥 LOAD VOID DATA SECARA TERPISAH
        loadVoidData { uniqueTrx, totalItems ->
             voidTrxCount = uniqueTrx
             voidItemCount = totalItems
             tvVoid.text = "$uniqueTrx Trx ($totalItems Item)"
        }

        // LISTENER HITUNG SELISIH
        etActual.addTextChangedListener {
            val input = it.toString().toDoubleOrNull() ?: 0.0
            val diff = input - expectedCash
            val fmtDiff = String.format(Locale("id", "ID"), "Rp %,d", diff.toLong()).replace(',', '.')
            if (diff == 0.0) {
                tvDifference.text = "Selisih: KLOP (OK) ✅"
                tvDifference.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            } else if (diff < 0) {
                tvDifference.text = "Selisih: KURANG $fmtDiff ❌"
                tvDifference.setTextColor(android.graphics.Color.RED)
            } else {
                tvDifference.text = "Selisih: LEBIH +$fmtDiff ⚠️"
                tvDifference.setTextColor(android.graphics.Color.parseColor("#FF9800"))
            }
        }

        btnCancel.setOnClickListener { finish() }
        
        btnClose.setOnClickListener {
            performCloseShift()
        }
    }

    


    private fun loadVoidData(onResult: (Int, Int) -> Unit) {
        val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val shiftStartTime = shiftPrefs.getLong("START_TIME_GLOBAL", 0L)
        val startOfDay = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val filterTime = if (shiftStartTime > 0) shiftStartTime else startOfDay
        
        // Observe StockLogs untuk mencari VOID
        viewModel.stockLogs.observe(this) { logs ->
            val voidLogs = logs.filter { it.type == "VOID" && it.timestamp >= filterTime }
            
            // Hitung Jumlah Transaksi Unik (Berdasarkan Invoice Number "VOID-XXX")
            val uniqueVoidTrx = voidLogs.map { it.invoiceNumber }.distinct().count()
            
            // Hitung Total Item
            val totalVoidItems = voidLogs.sumOf { it.quantity }
            
            onResult(uniqueVoidTrx, totalVoidItems)
        }
    }

    private fun loadShiftData(onComplete: () -> Unit) {
        val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val shiftStartTime = shiftPrefs.getLong("START_TIME_GLOBAL", 0L)
        modalAwal = shiftPrefs.getFloat("MODAL_AWAL_GLOBAL", 0f).toDouble()
        val startOfDay = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val filterTime = if (shiftStartTime > 0) shiftStartTime else startOfDay

        viewModel.allTransactions.observe(this) { transactions ->
             // 1. Ambil Data Transaksi Shift Ini
            val myTrx = transactions.filter { it.timestamp >= filterTime }
            allTrx = myTrx // Simpan untuk print

            totalTunai = 0.0; countTunai = 0
            totalQris = 0.0; countQris = 0
            totalDebit = 0.0; countDebit = 0
            totalTransfer = 0.0; countTransfer = 0
            piutangLunas = 0.0
            piutangBelumLunas = 0.0; countPiutang = 0

            for (trx in myTrx) {
                val method = trx.paymentMethod.lowercase()
                val isLunas = trx.note.contains("LUNAS", ignoreCase = true)
                when {
                    method.contains("tunai") -> { totalTunai += trx.totalAmount; countTunai++ }
                    method.contains("qris") -> { totalQris += trx.totalAmount; countQris++ }
                    method.contains("debit") -> { totalDebit += trx.totalAmount; countDebit++ }
                    method.contains("transfer") -> { totalTransfer += trx.totalAmount; countTransfer++ }
                    method.contains("piutang") -> {
                        if(isLunas) piutangLunas += trx.totalAmount 
                        else { piutangBelumLunas += trx.totalAmount; countPiutang++ }
                    }
                    else -> { totalTunai += trx.totalAmount; countTunai++ }
                }
            }
            
            expectedCash = modalAwal + totalTunai + piutangLunas
            onComplete()
            // Hapus observer agar tidak terpanggil berulang kali
            viewModel.allTransactions.removeObservers(this)
        }
    }

    private fun performCloseShift() {
        val actualCash = etActual.text.toString().toDoubleOrNull() ?: 0.0
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val userName = session.getString("username", "User") ?: "User"
        val realName = session.getString("fullname", userName) ?: userName
        val shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val startTime = shiftPrefs.getLong("START_TIME_GLOBAL", System.currentTimeMillis())

        Toast.makeText(this, "Menutup & Mencetak...", Toast.LENGTH_SHORT).show()

        // 1. Simpan Log Shift
        viewModel.closeShift(userName, expectedCash, actualCash)

        // 2. Print Laporan
        printShiftReport(realName, startTime, modalAwal, totalTunai, totalQris, totalDebit, totalTransfer, piutangBelumLunas, expectedCash, actualCash, allTrx)

        // 3. Clear Session & Logout Logic
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // 🔥 CLEAR SEMUA DATA PENTING
            shiftPrefs.edit().clear().apply()
            session.edit().clear().apply()
            getSharedPreferences("app_license", Context.MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("store_prefs", Context.MODE_PRIVATE).edit().clear().apply()

            // 🔥 RESET DATABASE & SIGN OUT
            viewModel.logoutAndReset {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
                    val i = Intent(this, LoginActivity::class.java)
                    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(i); finishAffinity()
                }
            }
        }, 3000)
    }

    private fun printShiftReport(
        kasirName: String, startTime: Long, modal: Double, tunai: Double,
        qris: Double, debit: Double, trf: Double, piutang: Double, // 🔥 UPDATED PARAMS
        expected: Double, actual: Double,
        trxList: List<Transaction>
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

                fun formatRupiah(amount: Double): String = String.format(Locale("id", "ID"), "Rp %,d", amount.toLong()).replace(',', '.')
                fun row(kiri: String, valDbl: Double): String {
                    val kanan = formatRupiah(valDbl).replace("Rp ", "")
                    val maxChars = 32
                    val maxKiri = maxChars - kanan.length - 1
                    val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri
                    val sisa = maxChars - textKiri.length - kanan.length
                    return "$textKiri${" ".repeat(if(sisa>0) sisa else 1)}$kanan\n"
                }
                fun rowText(kiri: String, kanan: String): String {
                    val maxChars = 32
                    val maxKiri = maxChars - kanan.length - 1
                    val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri
                    val sisa = maxChars - textKiri.length - kanan.length
                    return "$textKiri${" ".repeat(if(sisa>0) sisa else 1)}$kanan\n"
                }

                // 1. HITUNG OMZET & DETAIL
                var shiftGrandTotal = 0.0
                // Count ulang disini untuk akurasi print (atau pakai var global, tapi safe hitung ulang)
                var cTunai = 0; var cQris = 0; var cDebit = 0; var cTrf = 0; var cPiutang = 0

                for (trx in trxList) {
                    shiftGrandTotal += trx.totalAmount
                    val m = trx.paymentMethod.lowercase()
                    val isLunas = trx.note.contains("LUNAS", ignoreCase = true)
                    when {
                        m.contains("tunai") -> cTunai++
                        m.contains("qris") -> cQris++
                        m.contains("debit") -> cDebit++
                        m.contains("transfer") -> cTrf++
                        m.contains("piutang") -> if(!isLunas) cPiutang++
                        else -> cTunai++
                    }
                }

                val p = StringBuilder()

                // === HEADER ===
                p.append("\u001B\u0061\u0001") // Center
                p.append("\u001B\u0045\u0001$storeName\n\u001B\u0045\u0000")
                p.append("LAPORAN TUTUP SHIFT\n")
                p.append("--------------------------------\n")
                p.append("\u001B\u0061\u0000") // Left
                p.append("Kasir : $kasirName\n")
                p.append("Buka  : $openTimeStr\n")
                p.append("Tutup : $closeTimeStr\n")
                p.append("--------------------------------\n")

                // === 1. LACI ===
                p.append("LACI KASIR (FISIK):\n")
                p.append(row("Modal Awal", modal))
                p.append(row("Penjualan Tunai", tunai))
                p.append("--------------------------------\n")
                p.append(row("Total Sistem", modal + tunai))
                p.append(row("Fisik Laci", actual))

                val selisih = actual - (modal + tunai)
                if (selisih == 0.0) p.append("Selisih: KLOP (OK)\n")
                else p.append(row("Selisih", selisih))
                p.append("--------------------------------\n")

                // === 2. RINCIAN METODE ===
                p.append("RINCIAN METODE:\n")
                if(cTunai>0)   p.append("${row("Tunai", tunai)}   ($cTunai Trx)\n")
                if(cQris>0)    p.append("${row("QRIS", qris)}   ($cQris Trx)\n")
                if(cDebit>0)   p.append("${row("Debit", debit)}   ($cDebit Trx)\n")
                if(cTrf>0)     p.append("${row("Transfer", trf)}   ($cTrf Trx)\n")
                if(cPiutang>0) p.append("${row("Piutang", piutang)}   ($cPiutang Trx)\n")
                p.append("--------------------------------\n")

                // === 3. SUMMARY VOID ===
                if (voidTrxCount > 0) {
                     p.append("BATAL / VOID:\n")
                     p.append(rowText("Total Void", "$voidTrxCount Trx"))
                     p.append(rowText("Jml Item", "$voidItemCount Item"))
                     p.append("--------------------------------\n")
                }

                // === 4. GRAND TOTAL ===
                p.append("\u001B\u0045\u0001${row("GRAND TOTAL", shiftGrandTotal)}\u001B\u0045\u0000")
                p.append("--------------------------------\n")
                p.append("\n\n\n")

                outputStream.write(p.toString().toByteArray())
                outputStream.flush()
                Thread.sleep(2500)
                socket.close()

            } catch (e: Exception) {
                try { socket?.close() } catch (x: Exception) {}
            }
        }.start()
    }
}

