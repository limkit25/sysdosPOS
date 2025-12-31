package com.sysdos.kasirpintar.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.OutputStream
import java.util.UUID

class PrinterHelper(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // UUID Standar untuk Perangkat Serial (Printer biasanya pakai ini)
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // 1. Cek apakah Izin Bluetooth diberikan?
    private fun hasPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android lama biasanya otomatis boleh
        }
    }

    // 2. Ambil daftar perangkat yang SUDAH DIPAIRING di Setting HP
    fun getPairedDevices(): List<String> {
        if (!hasPermission()) {
            Toast.makeText(context, "Izin Bluetooth belum aktif!", Toast.LENGTH_SHORT).show()
            return emptyList()
        }

        val devices = bluetoothAdapter?.bondedDevices
        val list = mutableListOf<String>()
        devices?.forEach { device ->
            list.add("${device.name}\n${device.address}")
        }
        return list
    }

    // 3. Konek ke Printer
    fun connectPrinter(deviceAddress: String, onResult: (Boolean) -> Unit) {
        if (!hasPermission()) return

        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            socket = device?.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()
            outputStream = socket?.outputStream
            onResult(true)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(false)
        }
    }

    // 4. Perintah Cetak Teks
    fun printText(text: String) {
        try {
            outputStream?.write(text.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 5. Perintah Khusus (Rata Tengah, Tebal, dll)
    fun setAlign(align: Int) { // 0: Kiri, 1: Tengah, 2: Kanan
        val command = byteArrayOf(0x1B, 0x61, align.toByte())
        outputStream?.write(command)
    }

    fun setBold(isBold: Boolean) {
        val command = byteArrayOf(0x1B, 0x45, if (isBold) 1 else 0)
        outputStream?.write(command)
    }

    fun feedPaper() {
        printText("\n\n") // Enter 2 kali biar kertas keluar dikit
    }

    fun close() {
        socket?.close()
    }
}