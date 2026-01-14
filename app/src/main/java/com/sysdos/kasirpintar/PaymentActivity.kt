package com.sysdos.kasirpintar

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class PaymentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        val webView = findViewById<WebView>(R.id.wvPayment)
        val btnClose = findViewById<Button>(R.id.btnClosePayment)

        // 1. Ambil URL dari Activity sebelumnya
        val paymentUrl = intent.getStringExtra("PAYMENT_URL") ?: ""

        // 2. Setting Wajib WebView (Agar Midtrans jalan)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // 3. Agar link tetap buka di dalam aplikasi (bukan lempar ke Chrome)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url ?: "")
                return true
            }
        }

        // 4. Buka Link
        if (paymentUrl.isNotEmpty()) {
            webView.loadUrl(paymentUrl)
        }

        // 5. Tombol Tutup
        btnClose.setOnClickListener {
            finish() // Kembali ke aplikasi utama
        }
    }
}