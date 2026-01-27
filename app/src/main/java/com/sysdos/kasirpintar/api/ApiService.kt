package com.sysdos.kasirpintar.api

import com.sysdos.kasirpintar.data.model.User
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*
import com.google.gson.annotations.SerializedName
import com.sysdos.kasirpintar.data.model.StockLog

interface ApiService {

    // =================================================================
    // ☁️ JALUR 1: CLOUD / WEB PUSAT (backend.sysdos.my.id)
    // Digunakan oleh: ApiClient.webClient
    // =================================================================

    // 1. REGISTER AWAL (Kirim data toko ke Cloud/VPS)
    // Sesuai backend Go: /api/register-lead
    @POST("api/register-lead")
    fun registerLead(@Body data: LeadRequest): Call<ResponseBody>

    // 2. CEK LISENSI (Wajib Cloud)
    @GET("api/check-license")
    fun checkLicense(
        @Query("email") email: String,
        @Query("device_id") deviceId: String,
        @Query("device_model") deviceModel: String // <--- TAMBAHAN
    ): Call<LicenseCheckResponse>

    // 3. PEMBAYARAN MIDTRANS (Wajib Cloud)
    @POST("api/payment/create")
    fun createPayment(@Query("email") email: String): Call<PaymentResponse>


    // =================================================================
    // 🏠 JALUR 2: LOKAL / SERVER TOKO (IP Dinamis: 192.168.x.x)
    // Digunakan oleh: ApiClient.getLocalClient(context)
    // =================================================================

    // 1. GET PRODUCTS
    @GET("api/products")
    fun getProducts(@Query("user_id") userId: Int): Call<List<ProductResponse>>

    // 2. GET CATEGORIES
    @GET("api/categories")
    fun getCategories(@Query("user_id") userId: Int): Call<List<CategoryResponse>>

    // 3. UPLOAD TRANSAKSI
    @POST("api/transactions")
    fun uploadTransaction(@Body data: TransactionUploadRequest): Call<ResponseBody>

    // 4. IMPORT CSV
    @Multipart
    @POST("api/products/import")
    fun importCsv(@Part file: MultipartBody.Part): Call<ResponseBody>

    // 5. UPDATE PRODUCT
    @PUT("api/products")
    fun updateProduct(@Body product: ProductResponse): Call<ResponseBody>

    // 6. REGISTER USER LOKAL (Untuk kasir/karyawan di database lokal)
    @POST("api/users/register")
    fun registerLocalUser(@Body user: User): Call<ResponseBody>
    @PUT("api/transactions/update-status")
    fun updateTransactionStatus(@Body req: StatusUpdateRequest): Call<ResponseBody>

    // 7. SCAN BARCODE
    @GET("api/products")
    fun getProductByBarcode(
        @Query("barcode") barcode: String,
        @Query("user_id") userId: Int
    ): Call<List<ProductResponse>>

    // 8. GET ALL USERS (SYNC DARI WEB)
    @GET("api/users")
    fun getAllUsers(): Call<List<User>>
    // 9. UPLOAD STOCK LOGS (BELANJA / VOID)
    @POST("api/stock-logs/sync")
    fun syncStockLogs(@Body logs: List<StockLog>): Call<ResponseBody>
}

// =================================================================
// 📦 DATA CLASSES (RESPONSE & REQUEST)
// =================================================================
// Request untuk Update Status
data class StatusUpdateRequest(
    val android_id: Int,
    val status: String, // "VOID" atau "SUCCESS" (untuk Lunas tetap success tapi note berubah) atau "REFUND"
    val note: String
)
// Untuk menangkap link bayar dari Cloud
data class PaymentResponse(
    val payment_url: String,
    val order_id: String
)

// Untuk cek status lisensi dari Cloud
data class LicenseCheckResponse(
    val status: String,    // "TRIAL", "EXPIRED", "PREMIUM"
    val days_left: Int,
    val message: String
)

// Untuk pendaftaran Toko Awal (Lead) ke Cloud
data class LeadRequest(
    val name: String,
    val store_name: String,
    val store_address: String,
    val store_phone: String,
    val phone: String,
    val email: String
)

data class ProductResponse(
    val id: Int,
    val name: String,
    val category: String?,

    // 🔥 WAJIB PAKAI @SerializedName KARENA GOLANG PAKAI "cost"
    @SerializedName("cost")
    val cost_price: Double, // Gunakan Double (karena di Golang float64)

    // 🔥 Gunakan Double untuk harga jual juga
    val price: Double,

    val stock: Int,

    // 🔥 WAJIB PAKAI @SerializedName KARENA GOLANG PAKAI "imagePath"
    @SerializedName("imagePath")
    val image_path: String?
)

// Response Kategori (Lokal)
data class CategoryResponse(
    val id: Int,
    val name: String
)

// Request Upload Transaksi (Lokal)
data class TransactionUploadRequest(
    val total_amount: Double,
    val profit: Double,
    val items_summary: String,
    val user_id: Int,
    // 🔥 TAMBAHAN BARU:
    val tax: Double = 0.0,
    val discount: Double = 0.0,
    val note: String = "",
    val items: List<TransactionItemRequest>
)

data class TransactionItemRequest(
    val product_id: Int,
    val quantity: Int
)