package com.sysdos.kasirpintar

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import com.sysdos.kasirpintar.viewmodel.TransactionAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HistoryActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var viewModel: ProductViewModel
    private lateinit var adapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        val rvHistory = findViewById<RecyclerView>(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)

        adapter = TransactionAdapter { trx ->
            showDetailDialog(trx)
        }
        rvHistory.adapter = adapter

        viewModel.allTransactions.observe(this) { list ->
            adapter.submitList(list)
        }
    }

    private fun showDetailDialog(trx: Transaction) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(trx.timestamp))
        val totalStr = formatRupiah(trx.totalAmount)

        val message = """
            Waktu: $dateStr
            Metode: ${trx.paymentMethod}
            
            DETAIL:
            ${trx.itemsSummary}
            
            -------------------------
            Subtotal: ${formatRupiah(trx.subtotal)}
            Diskon: -${formatRupiah(trx.discount)}
            Pajak: +${formatRupiah(trx.tax)}
            -------------------------
            TOTAL: $totalStr
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Transaksi #${trx.id}")
            .setMessage(message)
            .setPositiveButton("CETAK ULANG") { _, _ -> doReprint(trx) }
            .setNegativeButton("Tutup", null)
            .show()
    }

    // --- FUNGSI REPRINT LENGKAP (HistoryActivity.kt) ---
    private fun doReprint(trx: Transaction) {
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE) // Ambil sesi kasir
        val targetMac = prefs.getString("printer_mac", "")

        if (targetMac.isNullOrEmpty()) {
            Toast.makeText(this, "Printer belum diatur!", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, StoreSettingsActivity::class.java))
            return
        }

        Thread {
            var socket: BluetoothSocket? = null
            try {
                val device = bluetoothAdapter?.getRemoteDevice(targetMac)
                if (device == null) {
                    runOnUiThread { Toast.makeText(this, "Printer Error!", Toast.LENGTH_SHORT).show() }
                    return@Thread
                }

                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()

                val outputStream = socket.outputStream

                // DATA TOKO & KASIR
                val storeName = prefs.getString("name", "Toko Saya")
                val storeAddress = prefs.getString("address", "Indonesia")
                val storePhone = prefs.getString("phone", "")
                val kasirName = session.getString("username", "Admin") // Ambil Nama Kasir

                // FORMAT TANGGAL
                val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                val dateStr = sdf.format(Date(trx.timestamp))

                val p = StringBuilder()

                // --- 1. HEADER ---
                p.append("\u001B\u0061\u0001") // Rata Tengah
                p.append("$storeName\n")
                p.append("$storeAddress\n")
                if(storePhone!!.isNotEmpty()) p.append("Telp: $storePhone\n")
                p.append("--------------------------------\n")

                p.append("\u001B\u0045\u0001") // Bold ON
                p.append("[ COPY / REPRINT ]\n")
                p.append("\u001B\u0045\u0000") // Bold OFF

                // --- 2. INFO TRANSAKSI (ID, Tgl, Kasir) ---
                p.append("\u001B\u0061\u0000") // Rata Kiri
                p.append("ID : #${trx.id}\n")
                p.append("Tgl: $dateStr\n")
                p.append("Kasir: $kasirName\n")
                p.append("--------------------------------\n")

                // --- 3. ITEM BARANG ---
                p.append(trx.itemsSummary) // Pastikan format itemsSummary di Transaction model sudah ada \n
                p.append("--------------------------------\n")

                // --- 4. TOTALAN (Rata Kanan Kiri Aman) ---
                // Fungsi local biar tidak perlu bikin function baru di luar
                fun row(kiri: String, valDbl: Double, isMinus: Boolean = false): String {
                    val formattedVal = String.format(Locale("id","ID"), "Rp %,d", valDbl.toLong()).replace(',', '.')
                    val kanan = if(isMinus) "-$formattedVal" else formattedVal

                    val maxChars = 30 // Batas aman
                    val maxKiri = maxChars - kanan.length - 1
                    val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri

                    val sisa = maxChars - textKiri.length - kanan.length
                    val spasi = if (sisa > 0) " ".repeat(sisa) else " "
                    return "$textKiri$spasi$kanan\n"
                }

                p.append(row("Subtotal", trx.subtotal))
                if (trx.discount > 0) p.append(row("Diskon", trx.discount, true))
                if (trx.tax > 0) p.append(row("Pajak", trx.tax))

                p.append("--------------------------------\n")

                // TOTAL BESAR
                p.append("\u001B\u0045\u0001") // Bold ON
                p.append(row("TOTAL", trx.totalAmount))
                p.append("\u001B\u0045\u0000") // Bold OFF

                // PEMBAYARAN
                if (trx.paymentMethod.contains("Tunai")) {
                    p.append(row("Tunai", trx.cashReceived))
                    p.append(row("Kembali", trx.changeAmount))
                } else {
                    p.append("Bayar: ${trx.paymentMethod}\n")
                }

                // --- 5. FOOTER ---
                p.append("\u001B\u0061\u0001") // Rata Tengah
                p.append("--------------------------------\n")
                p.append("Terima Kasih!\n\n\n\n")

                // KIRIM KE PRINTER
                outputStream.write(p.toString().toByteArray())
                outputStream.flush()

                Thread.sleep(1500)
                socket.close()

                runOnUiThread { Toast.makeText(this, "Reprint Sukses!", Toast.LENGTH_SHORT).show() }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Gagal Print: Cek Koneksi Printer", Toast.LENGTH_LONG).show()
                }
                try { socket?.close() } catch (e: Exception) {}
            }
        }.start()
    }

    // --- FUNGSI AJAIB UNTUK RATA KANAN KIRI ---
    private fun printRow(label: String, value: Double): String {
        val priceStr = formatRupiah(value)
        val maxLine = 32 // Lebar kertas standar 58mm

        val spaceNeeded = maxLine - label.length - priceStr.length
        val spaces = if (spaceNeeded > 0) " ".repeat(spaceNeeded) else " "

        return "$label$spaces$priceStr\n"
    }

    private fun formatRupiah(amount: Double): String {
        return String.format(Locale("id", "ID"), "Rp %,d", amount.toLong())
            .replace(',', '.') // Ubah koma jadi titik (format indo)
    }
}