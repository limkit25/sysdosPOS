package com.sysdos.kasirpintar.api

import android.content.Context
import com.sysdos.kasirpintar.utils.SessionManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // Variabel Retrofit yang bisa berubah
    private var retrofit: Retrofit? = null

    // Fungsi untuk mendapatkan Instance API
    // Sekarang butuh Context untuk membaca SessionManager
    fun getInstance(context: Context): ApiService {
        val session = SessionManager(context)
        val dynamicUrl = session.getServerUrl()

        // Jika retrofit belum ada atau URL berubah, bikin baru
        if (retrofit == null || retrofit?.baseUrl().toString() != dynamicUrl) {
            retrofit = Retrofit.Builder()
                .baseUrl(dynamicUrl) // <--- Pake URL dari Simpanan
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }
}