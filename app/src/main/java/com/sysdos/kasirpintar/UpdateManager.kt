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
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class UpdateManager(private val activity: Activity) {

    // GANTI URL INI DENGAN URL FILE JSON ANDA
    // ✅ GANTI JADI INI:
    private val UPDATE_URL = "https://backend.sysdos.my.id/updates/update_info.json"

    fun checkForUpdate() {
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        val fileName = "update_kasir.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Mendownload Update...")
            .setDescription("Mohon tunggu sebentar")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // Tunggu sampai download selesai, baru install
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId) {
                    installAPK(fileName)
                    activity.unregisterReceiver(this)
                }
            }
        }
        activity.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun installAPK(fileName: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (file.exists()) {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)

            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            activity.startActivity(intent)
        }
    }
}