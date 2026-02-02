package com.sysdos.kasirpintar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class ActivationActivity : AppCompatActivity() {

    private lateinit var ivProof: ImageView
    private var selectedImageUri: Uri? = null

    // Params
    private var planName = "PREMIUM 1 BULAN"
    private var price = "Rp 50.000"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            ivProof.setImageURI(uri)
            ivProof.visibility = android.view.View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_activation)

            // Get Data
            planName = intent.getStringExtra("PLAN_NAME") ?: "PREMIUM"
            price = intent.getStringExtra("PRICE") ?: "Rp 0"
            val storeName = intent.getStringExtra("STORE_NAME") ?: "-"
            val storePhone = intent.getStringExtra("STORE_PHONE") ?: "-"
            val storeType = intent.getStringExtra("STORE_TYPE") ?: "-"

            // Bind View
            val etStoreName = findViewById<EditText>(R.id.etStoreName)
            val etStorePhone = findViewById<EditText>(R.id.etStorePhone)
            val tvStoreType = findViewById<TextView>(R.id.tvStoreType)
            // tvPlan removed because we use RadioGroup now
            val tvBill = findViewById<TextView>(R.id.tvTotalBill)
            val etNote = findViewById<EditText>(R.id.etNote)
            
            val rgPlan = findViewById<android.widget.RadioGroup>(R.id.rgPlanDuration)
            
            ivProof = findViewById<ImageView>(R.id.ivProofPreview)
            val btnSelectImage = findViewById<Button>(R.id.btnSelectImage)
            // ... (Other bindings)
            val btnSubmit = findViewById<Button>(R.id.btnSubmit)
            val btnCopy = findViewById<ImageView>(R.id.btnCopyRek)
            val btnLogout = findViewById<ImageView>(R.id.btnLogout)
            val btnBackToLogin = findViewById<Button>(R.id.btnBackToLogin)

            // Set Data Info Toko (Allow Editing)
            etStoreName.setText(storeName)
            etStorePhone.setText(storePhone)
            tvStoreType.text = storeType
            
            // Set Initial Price (From Intent or Default)
            tvBill.text = price

            // 🔥 LISTENER GANTI PAKET
            rgPlan.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.rb1Month) {
                    planName = "PREMIUM 1 BULAN"
                    price = "Rp 50.000"
                    tvBill.text = price
                } else if (checkedId == R.id.rb1Year) {
                    planName = "PREMIUM 1 TAHUN"
                    price = "Rp 450.000"
                    tvBill.text = price
                }
            }

            // 🔥 CEK APAKAH PAKET SUDAH DITENTUKAN DARI DEPAN? (Register)
            val isPlanFixed = intent.getBooleanExtra("IS_PLAN_FIXED", false)
            if (isPlanFixed) {
                // Sembunyikan Pilihan, Tampilkan Teks Saja
                rgPlan.visibility = android.view.View.GONE
                
                // Pastikan variabel planName & price sinkron dengan Intent
                // (Sudah di set di atas via intent.getStringExtra, jadi aman)
                
                // Opsional: Tampilkan Text pengganti RadioGroup agar user tau dia beli apa
                // Disini kita bisa replace visibility atau tambahkan TextView info
                val tvFixedPlan = TextView(this)
                tvFixedPlan.text = "Paket: $planName"
                tvFixedPlan.textSize = 18f
                tvFixedPlan.setTypeface(null, android.graphics.Typeface.BOLD)
                tvFixedPlan.setPadding(0, 0, 0, 16)
                
                // Insert ke layout di atas rgPlan (Parent View) -> Agak ribet cari parent index
                // Gampangnya: Disable saja RadioGroupnya? -> GONE lebih rapi sesuai request "masa ada button lagi"
                
                // Kita tambahkan info paket di atas Billing saja secara dinamis?
                // Atau biarkan user lihat "Tagihan: Rp ..." dan mereka paham.
                
                // Note: User bilang "kan sudah ada form langganan sendiri", jadi GONE is best.
            } else {
                // Default Upgrade Mode (AboutActivity) -> Set Radio Button sesuai Default
                 if (planName.contains("1 TAHUN", ignoreCase = true)) {
                     rgPlan.check(R.id.rb1Year)
                 } else {
                     rgPlan.check(R.id.rb1Month)
                 }
            }

            // Listeners
            btnSelectImage.setOnClickListener {
                pickImageLauncher.launch("image/*")
            }

            btnCopy.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("No Rekening", "3981326359")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "No. Rekening Disalin!", Toast.LENGTH_SHORT).show()
            }

            btnSubmit.setOnClickListener { val currentBtn = it as Button
                // Validasi Simple
                if (selectedImageUri == null) {
                    Toast.makeText(this, "Mohon unggah bukti pembayaran dulu.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                currentBtn.isEnabled = false
                currentBtn.text = "Mengupload..."
                
                // 1. Siapkan File
                val file = getFileFromUri(selectedImageUri!!)
                if (file == null) {
                     Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
                     currentBtn.isEnabled = true
                     currentBtn.text = "KIRIM BUKTI & AKTIFKAN"
                     return@setOnClickListener
                }

                // 2. Siapkan Multipart Request (Fixed OkHttp Usage)
                val reqFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("proof_file", file.name, reqFile)
                
                // 🔥 GUNAKAN DATA DARI EDIT TEXT YANG MUNGKIN SUDAH DIEDIT USER
                val finalStoreName = etStoreName.text.toString()
                val finalStorePhone = etStorePhone.text.toString()

                val namePart = createPart("store_name", finalStoreName)
                val phonePart = createPart("store_phone", finalStorePhone)
                val planPart = createPart("plan_name", planName)
                val amountPart = createPart("amount", price)
                val notePart = createPart("note", etNote.text.toString())

                // 3. Eksekusi Upload ke Cloud
                com.sysdos.kasirpintar.api.ApiClient.webClient.submitActivation(
                    namePart, phonePart, planPart, amountPart, notePart, body
                ).enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                    override fun onResponse(call: retrofit2.Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {
                        currentBtn.isEnabled = true
                        currentBtn.text = "KIRIM BUKTI & AKTIFKAN"

                        if (response.isSuccessful) {
                            showSuccessDialog()
                        } else {
                            Toast.makeText(this@ActivationActivity, "Gagal: ${response.message()} code:${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) {
                        currentBtn.isEnabled = true
                        currentBtn.text = "KIRIM BUKTI & AKTIFKAN"
                        Toast.makeText(this@ActivationActivity, "Error Koneksi: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            
            btnLogout.setOnClickListener { finish() }
            
            btnBackToLogin.setOnClickListener {
                 val intent = Intent(this, LoginActivity::class.java)
                 intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                 startActivity(intent)
                 finish()
            }
        
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = "Error Init: ${e.message}"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    // --- HELPER FUNCTIONS ---

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bukti Terkirim! \uD83C\uDF89")
            .setMessage("Data & Bukti Transfer berhasil dikirim ke server.\nNotifikasi email juga sudah dikirim ke Admin.\n\nAkun akan aktif maks 24 jam.")
            .setPositiveButton("Ke Halaman Login") { _, _ ->
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun createPart(fieldName: String, text: String): MultipartBody.Part {
        return MultipartBody.Part.createFormData(
            fieldName, 
            null, 
            text.toRequestBody("text/plain".toMediaTypeOrNull())
        )
    }
    
    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(cacheDir, "proof_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
