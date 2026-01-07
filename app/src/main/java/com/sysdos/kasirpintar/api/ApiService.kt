package com.sysdos.kasirpintar.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body

interface ApiService {

    @GET("api/products")
    fun getProducts(): Call<List<ProductResponse>>

    @POST("api/transactions")
    fun uploadTransaction(@Body data: TransactionUploadRequest): Call<Void>
}

// --- MODEL DATA ---

data class ProductResponse(
    val id: Int,
    val name: String,
    val price: Int,
    val stock: Int
)

// 🔥 MODEL BARU YANG LEBIH LENGKAP 🔥
data class TransactionUploadRequest(
    val total_amount: Double,
    val items_summary: String,
    val items: List<TransactionItemRequest> // Tambahan List Item
)

data class TransactionItemRequest(
    val product_id: Int,
    val quantity: Int
)