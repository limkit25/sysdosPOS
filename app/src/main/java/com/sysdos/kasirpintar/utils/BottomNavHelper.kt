package com.sysdos.kasirpintar.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sysdos.kasirpintar.DashboardActivity
import com.sysdos.kasirpintar.MainActivity
import com.sysdos.kasirpintar.ProductListActivity
import com.sysdos.kasirpintar.PurchaseActivity
import com.sysdos.kasirpintar.SalesReportActivity
import com.sysdos.kasirpintar.R

object BottomNavHelper {

    fun setup(context: Context, navView: BottomNavigationView, viewModel: com.sysdos.kasirpintar.viewmodel.ProductViewModel) {
        val activity = context as Activity

        // Set selected item based on current activity
        val selectedId = when (activity) {
            is DashboardActivity -> R.id.nav_bot_home
            is MainActivity -> R.id.nav_bot_kasir
            is ProductListActivity -> R.id.nav_bot_produk
            is PurchaseActivity -> R.id.nav_bot_purch
            is SalesReportActivity -> R.id.nav_bot_laporan
            else -> -1
        }

        if (selectedId != -1) {
            navView.selectedItemId = selectedId
        }

        // 🔥 LOGIKA PERMISSION / LIMIT MENU
        val session = context.getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val role = session.getString("role", "admin")

        if (role?.trim()?.lowercase() == "kasir") {
            // 🔥 CLEAR MENU BAWAAN & REPLACE DENGAN MENU KHUSUS KASIR
            navView.menu.clear()
            navView.inflateMenu(R.menu.bottom_nav_menu_kasir)
        }
        // Manager biasanya boleh akses semua, jadi tidak dilimit (sesuai request user "limit kasir")

        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_bot_home -> {
                    if (activity !is DashboardActivity) {
                        startActivity(context, DashboardActivity::class.java)
                    }
                    true
                }
                R.id.nav_bot_kasir -> {
                    // 🔥 CEK LISENSI DULU (Sama seperti di Dashboard)
                    val prefs = context.getSharedPreferences("app_license", Context.MODE_PRIVATE)
                    val isFullVersion = prefs.getBoolean("is_full_version", false)
                    val isExpired = prefs.getBoolean("is_expired", false)

                    if (isExpired && !isFullVersion) {
                        // 🔥 AMBIL PESAN ASLI DARI SERVER (BIAR GAK SELALU 'TRIAL')
                        val msg = prefs.getString("license_msg", "Masa Aktif Habis") ?: "Masa Aktif Habis"
                        
                        androidx.appcompat.app.AlertDialog.Builder(context)
                            .setTitle("⚠️ MASA AKTIF HABIS")
                            .setMessage("$msg\n\nSilakan hubungi Admin untuk perpanjangan.")
                            .setPositiveButton("HUBUNGI ADMIN") { _, _ ->
                                try {
                                    val nomorAdmin = "628179842043" // Sesuaikan dg Dashboard
                                    val pesan = "Halo Admin, lisensi saya habis ($msg). Saya mau perpanjang."
                                    val url = "https://api.whatsapp.com/send?phone=$nomorAdmin&text=${java.net.URLEncoder.encode(pesan, "UTF-8")}"
                                    val intent = Intent(Intent.ACTION_VIEW)
                                    intent.data = android.net.Uri.parse(url)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Gagal buka WhatsApp", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Tutup", null)
                            .show()
                        false // Jangan pindah tab
                    } else {
                        // Lolos Lisensi -> Cek Sesi Shift
                        if (activity !is MainActivity) {
                            checkAndOpenPOS(context, viewModel)
                        }
                        true
                    }
                }
                R.id.nav_bot_produk -> {
                    if (activity !is ProductListActivity) {
                        startActivity(context, ProductListActivity::class.java)
                    }
                    true
                }
                R.id.nav_bot_purch -> {
                    if (activity !is PurchaseActivity) {
                        startActivity(context, PurchaseActivity::class.java)
                    }
                    true
                }
                R.id.nav_bot_laporan -> {
                    if (activity !is SalesReportActivity) {
                        startActivity(context, SalesReportActivity::class.java)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // =================================================================
    // 🔥 LOGIKA CEK SESI / BUKA SHIFT (DIPINDAHKAN KESINI)
    // =================================================================
    fun checkAndOpenPOS(context: Context, viewModel: com.sysdos.kasirpintar.viewmodel.ProductViewModel) {
        val prefs = context.getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
        val isOpen = prefs.getBoolean("IS_OPEN_GLOBAL_SESSION", false)

        if (isOpen) {
            // Sesi sudah buka, langsung gass ke POS
            startActivity(context, MainActivity::class.java)
        } else {
            // Sesi belum buka, tampilkan modal dialog
            showInputModalDialogForPOS(context, viewModel, prefs)
        }
    }

    private fun showInputModalDialogForPOS(
        context: Context,
        viewModel: com.sysdos.kasirpintar.viewmodel.ProductViewModel,
        prefs: android.content.SharedPreferences
    ) {
        val session = context.getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val username = session.getString("username", "default") ?: "default"

        val input = android.widget.EditText(context)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Contoh: 200000"

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("🏪 Buka Shift Baru")
            .setMessage("Masukkan Modal Awal:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("BUKA") { _, _ ->
                val modalStr = input.text.toString()
                if (modalStr.isNotEmpty()) {
                    val modal = modalStr.toDouble()
                    
                    // 1. Simpan Status Sesi ke Prefs
                    prefs.edit()
                        .putBoolean("IS_OPEN_GLOBAL_SESSION", true)
                        .putFloat("MODAL_AWAL_GLOBAL", modal.toFloat())
                        .putLong("START_TIME_GLOBAL", System.currentTimeMillis())
                        .apply()
                    
                    // 2. Simpan Log Buka Shift ke Database (via ViewModel)
                    viewModel.openShift(username, modal)
                    
                    android.widget.Toast.makeText(context, "Shift Toko Dibuka!", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // 3. Masuk ke POS
                    startActivity(context, MainActivity::class.java)
                } else {
                    android.widget.Toast.makeText(context, "Modal tidak boleh kosong", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal") { _, _ -> 
                // Jika batal, tidak jadi masuk POS. 
                // Jika kita tadi klik BottomNav, mungkin kita perlu reset seleksi?
                // Tapi agak ribet, biarkan saja tetap di tab lama.
            }
            .show()
    }

    private fun startActivity(context: Context, clazz: Class<*>) {
        val intent = Intent(context, clazz)
        
        // 🔥 IMPROVEMENT: Handle Stack untuk Dashboard agar tidak numpuk
        if (clazz == DashboardActivity::class.java) {
             intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        context.startActivity(intent)
        
        // Matikan activity saat ini KECUALI kita sedang di Dashboard
        // (Agar Dashboard tetap ada di stack paling bawah)
        if (context !is DashboardActivity) {
             (context as Activity).finish() 
             // Override transition biar transisi lebih smooth (opsional)
             (context as Activity).overridePendingTransition(0, 0)
        }
    }
}
