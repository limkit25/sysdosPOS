package com.sysdos.kasirpintar.api

import android.content.Context
import com.sysdos.kasirpintar.utils.SessionManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // ==========================================
    // 1. JALUR KHUSUS PENDAFTARAN (CLOUD / WEB)
    // ==========================================
    // Ganti dengan URL Hosting/VPS Anda nanti.
    // User baru TIDAK PERLU setting IP, langsung tembak sini.
    private const val WEB_URL = "http://192.168.1.7:3000/"

    val webClient: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WEB_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // ==========================================
    // 2. JALUR KHUSUS OPERASIONAL TOKO (LOCAL)
    // ==========================================
    // Ini berubah-ubah sesuai IP Komputer Kasir (192.168.x.x)
    private var localRetrofit: Retrofit? = null

    fun getLocalClient(context: Context): ApiService {
        val session = SessionManager(context)
        val dynamicUrl = session.getServerUrl() // Ambil dari SharedPreferences

        // Cek: Bikin baru jika null ATAU jika user baru saja ganti IP di setting
        if (localRetrofit == null || localRetrofit?.baseUrl().toString() != dynamicUrl) {
            localRetrofit = Retrofit.Builder()
                .baseUrl(dynamicUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return localRetrofit!!.create(ApiService::class.java)
    }
}