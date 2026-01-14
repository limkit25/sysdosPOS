package com.sysdos.kasirpintar.api

import com.sysdos.kasirpintar.data.model.User
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.* // <--- PENTING: Ini meng-import Query, GET, POST, Body, dll

interface ApiService {

    // 1. GET PRODUCTS (Sekarang butuh user_id)
    @GET("api/products")
    fun getProducts(@Query("user_id") userId: Int): Call<List<ProductResponse>>

    // 2. GET CATEGORIES (Sekarang butuh user_id)
    @GET("api/categories")
    fun getCategories(@Query("user_id") userId: Int): Call<List<CategoryResponse>>

    // 3. UPLOAD TRANSAKSI (Request Body-nya yang berubah)
    @POST("api/transactions")
    fun uploadTransaction(@Body data: TransactionUploadRequest): Call<ResponseBody>

    // 4. IMPORT CSV
    @Multipart
    @POST("api/products/import")
    fun importCsv(@Part file: MultipartBody.Part): Call<ResponseBody>

    // 5. UPDATE PRODUCT (Perlu kirim User ID atau di body)
    @PUT("api/products")
    fun updateProduct(@Body product: ProductResponse): Call<ResponseBody>

    // 6. REGISTER USER (Hybrid - Cloud)
    @POST("api/users/register") // Sesuaikan endpoint backend Anda
    fun registerUser(@Body user: User): Call<ResponseBody>

    // 7. 🔥 SCAN BARCODE (Pencarian Spesifik)
    @GET("api/products")
    fun getProductByBarcode(
        @Query("barcode") barcode: String,
        @Query("user_id") userId: Int // <--- Tambahkan ini
    ): Call<List<ProductResponse>>
    // 🔥 ENDPOINT KHUSUS PROMO (Url-nya beda, nanti kita atur di Client)
    @POST
    fun sendLeadData(@Url url: String, @Body data: LeadRequest): Call<ResponseBody>

    @GET
    fun checkLicense(@Url url: String, @Query("email") email: String): Call<LicenseResponse>


    @GET("api/check-license")
    fun checkLicense(@Query("email") email: String): Call<LicenseCheckResponse>

    // 🔥 3. TAMBAHKAN ENDPOINT INI (Agar AboutActivity.kt tidak error)
    @POST("api/payment/create")
    fun createPayment(@Query("email") email: String): Call<PaymentResponse>
}

// 🔥 1. TAMBAHKAN CLASS INI (Untuk menangkap balasan link bayar dari server)
data class PaymentResponse(
    val payment_url: String,
    val order_id: String
)

// 🔥 2. TAMBAHKAN CLASS INI (Untuk menangkap status lisensi)
data class LicenseCheckResponse(
    val status: String,
    val days_left: Int,
    val message: String
)
// --- DATA CLASSES UNTUK RESPONSE API ---

data class LicenseResponse(
    val status: String,    // "TRIAL", "EXPIRED", "PREMIUM"
    val days_left: Int,
    val message: String
)
data class LeadRequest(
    val name: String,
    val store_name: String,
    val store_address: String, // Baru
    val store_phone: String,   // Baru
    val phone: String,
    val email: String
)

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

// 🔥 UPDATE DATA CLASS REQUEST:
data class TransactionUploadRequest(
    val total_amount: Double,
    val profit: Double,
    val items_summary: String,
    val user_id: Int, // <--- 🔥 TAMBAHAN WAJIB
    val items: List<TransactionItemRequest>
)

data class TransactionItemRequest(
    val product_id: Int,
    val quantity: Int
)