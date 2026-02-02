package com.sysdos.kasirpintar

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class WelcomeActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var layoutDots: LinearLayout
    
    // 🔥 AUTO SLIDE LOGIC
    private val sliderHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sliderRunnable = object : Runnable {
        override fun run() {
            if (::viewPager.isInitialized && viewPager.adapter != null) {
                val itemCount = viewPager.adapter?.itemCount ?: 0
                if (itemCount > 0) {
                    val nextItem = (viewPager.currentItem + 1) % itemCount
                    viewPager.currentItem = nextItem
                }
            }
            sliderHandler.postDelayed(this, 4000) // 4 Detik
        }
    }

    override fun onPause() {
        super.onPause()
        sliderHandler.removeCallbacks(sliderRunnable)
    }

    override fun onResume() {
        super.onResume()
        sliderHandler.postDelayed(sliderRunnable, 4000)
    }

    private val slides = listOf(
        WelcomeSlide(
            "Kelola Toko Mudah",
            "Pantau stok, karyawan, dan cabang\ndalam satu aplikasi.",
            R.drawable.icon_menu_toko
        ),
        WelcomeSlide(
            "Transaksi Cepat",
            "Catat penjualan dengan cepat\ndan cetak struk seketika.",
            R.drawable.icon_menu_kasir
        ),
        WelcomeSlide(
            "Laporan Lengkap",
            "Analisa keuntungan dan omzet\nharian secara real-time.",
            R.drawable.icon_menu_laporan
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        viewPager = findViewById(R.id.viewPagerWelcome)
        layoutDots = findViewById(R.id.layoutDots)
        val btnLogin = findViewById<Button>(R.id.btnWelcomeLogin)
        val btnRegister = findViewById<Button>(R.id.btnWelcomeRegister)

        // Setup Adapter
        val adapter = WelcomeAdapter(slides)
        viewPager.adapter = adapter

        // Setup Dots
        setupDots(0)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setupDots(position)
            }
        })

        // Listeners
        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnRegister.setOnClickListener {
             // 🔥 MASUK KE HALAMAN PILIH PAKET (Full Screen) 🔥
             val intent = Intent(this, SubscriptionPlanActivity::class.java)
             startActivity(intent)
        }
    }

    private fun setupDots(position: Int) {
        val dots = arrayOfNulls<TextView>(slides.size)
        layoutDots.removeAllViews()

        for (i in dots.indices) {
            dots[i] = TextView(this)
            dots[i]?.text = Html.fromHtml("&#8226;") // Bullet character
            dots[i]?.textSize = 35f
            dots[i]?.setTextColor(
                if (i == position) ContextCompat.getColor(this, R.color.colorPrimary) // Active
                else ContextCompat.getColor(this, R.color.gray_600) // Inactive
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            layoutDots.addView(dots[i], params)
        }
    }

    // DATA MODEL
    data class WelcomeSlide(val title: String, val desc: String, val imageRes: Int)

    // ADAPTER
    inner class WelcomeAdapter(private val items: List<WelcomeSlide>) :
        RecyclerView.Adapter<WelcomeAdapter.WelcomeViewHolder>() {

        inner class WelcomeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val img: ImageView = itemView.findViewById(R.id.ivSlideImage)
            val title: TextView = itemView.findViewById(R.id.tvSlideTitle)
            val desc: TextView = itemView.findViewById(R.id.tvSlideDesc)

            fun bind(item: WelcomeSlide) {
                img.setImageResource(item.imageRes)
                title.text = item.title
                desc.text = item.desc
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WelcomeViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_welcome_slide, parent, false)
            return WelcomeViewHolder(view)
        }

        override fun onBindViewHolder(holder: WelcomeViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }
}