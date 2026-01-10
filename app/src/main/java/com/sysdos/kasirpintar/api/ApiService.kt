package com.sysdos.kasirpintar.api

import com.sysdos.kasirpintar.data.model.User
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.* // <--- PENTING: Ini meng-import Query, GET, POST, Body, dll

interface ApiService {

    // 1. GET PRODUCTS
    @GET("api/products")
    fun getProducts(): Call<List<ProductResponse>>

    // 2. GET CATEGORIES
    @GET("api/categories")
    fun getCategories(): Call<List<CategoryResponse>>

    // 3. UPLOAD TRANSAKSI
    @POST("api/transactions")
    fun uploadTransaction(@Body data: TransactionUploadRequest): Call<ResponseBody>

    // 4. IMPORT CSV
    @Multipart
    @POST("api/products/import")
    fun importCsv(@Part file: MultipartBody.Part): Call<ResponseBody>

    // 5. UPDATE PRODUCT (Reverse Sync)
    @PUT("api/products")
    fun updateProduct(@Body product: ProductResponse): Call<ResponseBody>

    // 6. REGISTER USER (Hybrid - Cloud)
    @POST("api/users/register") // Sesuaikan endpoint backend Anda
    fun registerUser(@Body user: User): Call<ResponseBody>

    // 7. 🔥 SCAN BARCODE (Pencarian Spesifik)
    // Ini yang tadi error karena kurang import Query
    @GET("api/products")
    fun getProductByBarcode(@Query("barcode") barcode: String): Call<List<ProductResponse>>
}

// --- DATA CLASSES UNTUK RESPONSE API ---

data class ProductResponse(
    val id: Int,
    val name: String,
    val category: String?,
    val cost_price: Int,
    val price: Int,
    val stock: Int,
    val image_path: String?
)

data class CategoryResponse(
    val id: Int,
    val name: String
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