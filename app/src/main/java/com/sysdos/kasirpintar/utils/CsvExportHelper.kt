package com.sysdos.kasirpintar.utils

import com.sysdos.kasirpintar.data.model.Transaction
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportHelper {

    fun exportTransactions(outputStream: OutputStream, transactions: List<Transaction>) {
        val writer = outputStream.bufferedWriter()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // Header
        writer.write("ID,Tanggal,Total,Metode,Items,KasirID,WaktuDibuat,Profit,Pajak,Diskon\n")

        // Rows
        for (trx in transactions) {
            val dateStr = dateFormat.format(Date(trx.timestamp))
            // Bersihkan "," atau newline di item summary agar CSV tidak rusak
            val cleanItems = trx.itemsSummary.replace(",", ";").replace("\n", " | ")

            val line = StringBuilder()
                .append(trx.id).append(",")
                .append(dateStr).append(",")
                .append(trx.totalAmount).append(",")
                .append(trx.paymentMethod).append(",")
                .append("\"").append(cleanItems).append("\",") // Quote items
                .append(trx.userId).append(",") // Kasir ID
                .append(dateStr).append(",")
                .append(trx.profit).append(",")
                .append(trx.tax).append(",")
                .append(trx.discount)
                
            writer.write(line.toString())
            writer.newLine()
        }

        writer.flush()
        writer.close()
    }
    fun generateExpenseReport(context: android.content.Context, expenses: List<com.sysdos.kasirpintar.data.model.Expense>, start: Long, end: Long): java.io.File? {
        val fileName = "Laporan_Pengeluaran_${start}_${end}.csv"
        val file = java.io.File(context.cacheDir, fileName)
        
        try {
            val writer = file.bufferedWriter()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            // Header
            writer.write("ID,Tanggal,Kategori,Jumlah,Catatan,UserID\n")
            
            for (exp in expenses) {
                val dateStr = dateFormat.format(Date(exp.timestamp))
                val cleanNote = exp.note.replace(",", " ").replace("\n", " ")
                
                val line = StringBuilder()
                    .append(exp.id).append(",")
                    .append(dateStr).append(",")
                    .append(exp.category).append(",")
                    .append(exp.amount).append(",")
                    .append("\"").append(cleanNote).append("\",")
                    .append(exp.userId)
                
                writer.write(line.toString())
                writer.newLine()
            }
            writer.flush()
            writer.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
