package com.sysdos.kasirpintar.api

import retrofit2.Call
// 👇 PERHATIKAN BAGIAN INI: Import harus lengkap
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT  // <--- INI YANG TADI KURANG
import retrofit2.http.Body

interface ApiService {

    // 1. Ambil Produk
    @GET("api/products")
    fun getProducts(): Call<List<ProductResponse>>

    // 2. Kirim Transaksi
    @POST("api/transactions")
    fun uploadTransaction(@Body data: TransactionUploadRequest): Call<Void>

    // 3. Update Produk (Edit Harga/Stok dari HP)
    @PUT("api/products")
    fun updateProduct(@Body data: ProductResponse): Call<Void>

    // 🔥 TAMBAHKAN INI (Untuk ambil daftar kategori) 🔥
    @GET("api/categories")
    fun getCategories(): Call<List<CategoryResponse>>
}

// --- MODEL DATA ---

data class ProductResponse(
    val id: Int,
    val name: String,
    val category: String?,
    val price: Int,
    val cost_price: Int,
    val stock: Int,
    val image_path: String? // Bisa null kalau tidak ada gambar
)

data class TransactionUploadRequest(
    val total_amount: Double,
    val profit: Double,
    val items_summary: String,
    val items: List<TransactionItemRequest>
)

data class TransactionItemRequest(
    val product_id: Int,
    val quantity: Int
)
data class CategoryResponse(
    val id: Int,
    val name: String
)