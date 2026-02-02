package com.sysdos.kasirpintar

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast // 🔥 TAMBAHAN
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class UpdateManager(private val activity: Activity) {

    // GANTI URL INI DENGAN URL FILE JSON ANDA
    // ✅ GANTI JADI INI:
    private val UPDATE_URL = "https://backend.sysdos.my.id/public/updates/update_info.json"

    fun checkForUpdate(isManual: Boolean = false) {
        thread {
            try {
                val jsonString = URL(UPDATE_URL).readText()
                val json = JSONObject(jsonString)

                val remoteVersionCode = json.getInt("versionCode")
                val apkUrl = json.getString("url")
                val changeLog = json.getString("changelog")

                // Ambil versi aplikasi saat ini
                val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toInt()
                } else {
                    pInfo.versionCode
                }

                // Cek: Apakah versi di internet LEBIH BESAR dari versi HP?
                if (remoteVersionCode > currentVersionCode) {
                    activity.runOnUiThread {
                        showUpdateDialog(apkUrl, changeLog)
                    }
                } else {
                    // 🔥 Feedback jika manual check tapi tidak ada update
                    if (isManual) {
                        activity.runOnUiThread {
                            android.widget.Toast.makeText(activity, "✅ Aplikasi sudah versi terbaru!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 🔥 Feedback jika error (misal offline atau json salah)
                if (isManual) {
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "Gagal cek update: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    private fun showUpdateDialog(apkUrl: String, changeLog: String) {
        AlertDialog.Builder(activity)
            .setTitle("🚀 Update Tersedia!")
            .setMessage("Versi baru telah rilis.\n\nApa yang baru:\n$changeLog\n\nYuk update sekarang biar makin lancar!")
            .setPositiveButton("UPDATE SEKARANG") { _, _ ->
                downloadAndInstall(apkUrl)
            }
            .setNegativeButton("Nanti Saja", null)
            .setCancelable(false)
            .show()
    }

    private fun downloadAndInstall(url: String) {
        try {
            val fileName = "update_kasir.apk"
            
            // 🔥 HAPUS FILE LAMA DULU
            val file = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (file.exists()) file.delete()

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Mendownload Update...")
                .setDescription("Mohon tunggu sebentar")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName) // 🔥 DIGANTI AGAR TIDAK PERLU PERMISSION STORAGE
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            // Tunggu sampai download selesai, baru install
            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try {
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = dm.query(query)
                            if (cursor.moveToFirst()) {
                                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val status = cursor.getInt(statusIndex)

                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    installAPK(fileName)
                                } else {
                                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reason = cursor.getInt(reasonIndex)
                                    val reasonText = when(reason) {
                                        DownloadManager.ERROR_CANNOT_RESUME -> "Cannot Resume"
                                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Device Not Found"
                                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File Already Exists"
                                        DownloadManager.ERROR_FILE_ERROR -> "File Error"
                                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP Data Error"
                                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Memori Penuh"
                                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too Many Redirects"
                                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP Code"
                                        DownloadManager.ERROR_UNKNOWN -> "Unknown Error"
                                        404 -> "File Tidak Ditemukan (404)"
                                        403 -> "Akses Ditolak (403)"
                                        else -> "Error Code $reason"
                                    }
                                    Toast.makeText(activity, "Gagal Download: $reasonText", Toast.LENGTH_LONG).show()
                                }
                            }
                            cursor.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            try {
                                activity.unregisterReceiver(this)
                            } catch (e: Exception) {}
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                activity.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        
        } catch (e: Exception) {
            e.printStackTrace()
            // Gunakan runOnUiThread jaga-jaga kalau dipanggil dari thread lain (meski harusnya UI thread)
            activity.runOnUiThread {
                Toast.makeText(activity, "Gagal Download: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun installAPK(fileName: String) {
        try {
            // 🔥 PATH SESUAI DENGAN YANG DI DOWNLOAD (APP SPECIFIC)
            val file = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

            if (file.exists()) {
                val intent = Intent(Intent.ACTION_VIEW)
                
                // Menggunakan FileProvider untuk Android 7.0+
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)

                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, "File Update Gagal Ditemukan!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, "Error Install: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}