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

    // Ambil IP Server (Default ke IP lama Anda buat jaga-jaga)
    fun getServerUrl(): String {
        return prefs.getString("SERVER_URL", "http://192.168.1.15:3000/") ?: "http://192.168.1.15:3000/"
    }
}