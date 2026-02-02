package com.sysdos.kasirpintar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.LeadRequest
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]
        
        // Debug: Cek Rencana Intent
        val plan = intent.getStringExtra("SUBSCRIPTION_PLAN") ?: "FREE_TRIAL"
        // Toast.makeText(this, "Debug Plan: $plan", Toast.LENGTH_SHORT).show()

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etStoreName = findViewById<EditText>(R.id.etStoreName)
        val spBusinessType = findViewById<Spinner>(R.id.spBusinessType)
        val etAddress = findViewById<EditText>(R.id.etAddress)
        
        val etOwnerName = findViewById<EditText>(R.id.etOwnerName)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etPin = findViewById<EditText>(R.id.etPin)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etReferral = findViewById<EditText>(R.id.etReferral)
        
        val cbAgree = findViewById<CheckBox>(R.id.cbAgree)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // Setup Spinner Tipe Bisnis
        val businessTypes = arrayOf("F&B (Makanan & Minuman)", "Retail / Toko", "Jasa", "Lainnya")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, businessTypes)
        spBusinessType.adapter = adapter

        btnBack.setOnClickListener { finish() }

        btnRegister.setOnClickListener {
            val storeName = etStoreName.text.toString().trim()
            val businessType = spBusinessType.selectedItem.toString()
            val address = etAddress.text.toString().trim()
            val ownerName = etOwnerName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val pin = etPin.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val referral = etReferral.text.toString().trim()

            if (storeName.isEmpty() || address.isEmpty() || ownerName.isEmpty() || phone.isEmpty() || pin.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Mohon lengkapi semua data wajib!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pin.length != 6) {
                Toast.makeText(this, "PIN harus 6 digit angka", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!cbAgree.isChecked) {
                Toast.makeText(this, "Anda harus menyetujui Syarat & Ketentuan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val loading = android.app.ProgressDialog(this)
            loading.setMessage("Memproses pendaftaran...")
            loading.setCancelable(false)
            loading.show()

            // 1. Cek Email di Cloud
            viewModel.checkUserOnCloud(email) { response ->
                if (response != null && !response.message.lowercase().contains("tidak ditemukan")) {
                    loading.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Gagal Daftar")
                        .setMessage("Email ini SUDAH TERDAFTAR di sistem pusat.\nSilakan Login saja.")
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .show()
                } else {
                    // 2. Daftar ke Server Pusat (Lead)
                    val req = LeadRequest(
                        name = ownerName,
                        store_name = storeName,
                        store_address = address,
                        store_phone = phone,
                        phone = phone,
                        email = email
                    )

                    ApiClient.webClient.registerLead(req).enqueue(object : Callback<ResponseBody> {
                        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                            // 3. Simpan User secara Lokal
                            saveLocalUserAndLogin(loading, ownerName, email, phone, pin, storeName, address, businessType, referral)
                        }

                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                            // Tetap lanjut save lokal meski offline (atau handling lain) -> Disini kita lanjut saja
                            // Atau mau strict online? Asumsi lanjut biar UX bagus
                            saveLocalUserAndLogin(loading, ownerName, email, phone, pin, storeName, address, businessType, referral)
                        }
                    })
                }
            }
        }
    }

    private fun saveLocalUserAndLogin(
        loading: android.app.ProgressDialog, 
        name: String, email: String, phone: String, pin: String, 
        storeName: String, address: String, 
        businessType: String, referral: String
    ) {
        viewModel.logoutAndReset {
            getSharedPreferences("app_license", Context.MODE_PRIVATE).edit().clear().apply()
            
            // Simpan User dengan Role ADMIN (Owner)
            val newUser = User(
                name = name, 
                username = email, 
                phone = phone, 
                password = pin, // PIN jadi password
                role = "admin"
            )
            
            // 🔥 TUNGGU SAMPAI INSERT SELESAI BARU PINDAH ACTIVTY
            viewModel.insertUserWithCallback(newUser) {
                // Auto Login Session
                val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                val editor = session.edit()
                editor.putString("username", newUser.username)
                editor.putString("role", newUser.role)
                editor.putString("fullname", newUser.name)
                editor.putString("branch_name", "Pusat")
                editor.putBoolean("is_logged_in", true)
                editor.apply()
                
                loading.dismiss()

                // 🔥 LOGIC BARU: CEK DARI PILIHAN DI AWAL (LOGIN SCREEN) 🔥
                val plan = intent.getStringExtra("SUBSCRIPTION_PLAN") ?: "FREE_TRIAL"
                val price = intent.getStringExtra("PLAN_PRICE") ?: "Rp 0"

                // Debugging (Remove later)
                // Toast.makeText(this@RegisterActivity, "Plan: $plan", Toast.LENGTH_LONG).show()

                if (plan == "FREE_TRIAL") {
                    // FREE LIST -> Langsung Settings
                    Toast.makeText(this, "Selamat Datang! (Trial Mode)", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, StoreSettingsActivity::class.java)
                    intent.putExtra("IS_INITIAL_SETUP", true)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    // PREMIUM -> Ke Form Aktivasi
                    val intent = Intent(this, ActivationActivity::class.java)
                    intent.putExtra("PLAN_NAME", plan)
                    intent.putExtra("PRICE", price)
                    intent.putExtra("STORE_NAME", storeName)
                    intent.putExtra("STORE_PHONE", phone)
                    intent.putExtra("STORE_TYPE", businessType)
                    
                    // 🔥 SUDAH PILIH DI AWAL, JADI JANGAN TANYA LAGI DI FORM
                    intent.putExtra("IS_PLAN_FIXED", true) 
                    
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
