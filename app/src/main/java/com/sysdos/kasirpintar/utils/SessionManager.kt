package com.sysdos.kasirpintar.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    // Simpan IP Server
    fun saveServerUrl(url: String) {
        // Pastikan formatnya benar (harus diakhiri /)
        var finalUrl = url
        if (!finalUrl.startsWith("http://")) finalUrl = "http://$finalUrl"
        if (!finalUrl.endsWith("/")) finalUrl = "$finalUrl/"

        prefs.edit().putString("SERVER_URL", finalUrl).apply()
    }

    // Ambil IP Server
    fun getServerUrl(): String {
        var url = prefs.getString("SERVER_URL", "http://192.168.1.15:9000/") ?: "http://192.168.1.15:9000/"
        
        // 🔥 AUTO-FIX: Jika masih pakai Port 3000, ganti ke 9000 otomatis
        if (url.contains(":3000")) {
            url = url.replace(":3000", ":9000")
            saveServerUrl(url) // Simpan yang baru
        }
        return url
    }
}