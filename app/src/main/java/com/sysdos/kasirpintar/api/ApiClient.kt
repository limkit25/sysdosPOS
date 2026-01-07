package com.sysdos.kasirpintar.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // PENTING: IP ini adalah alamat laptop Anda.
    // Jangan lupa tanda garis miring (slash) di belakang port!
    private const val BASE_URL = "http://192.168.1.15:3000/"

    // Inisialisasi Retrofit (Singleton Pattern)
    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}