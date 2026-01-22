package com.sysdos.kasirpintar.viewmodel

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sysdos.kasirpintar.R
import com.sysdos.kasirpintar.data.model.Product
import java.util.Locale

class ProductAdapter(
    private val onItemClick: (Product) -> Unit,
    private val onItemLongClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback()) {

    private var cartList: List<Product> = emptyList()

    fun updateCartCounts(cartItems: List<Product>) {
        this.cartList = cartItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        // Pastikan pakai productList[position] atau getItem(position) sesuai base adapter kakak
        holder.bind(productList[position])
    }

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        private val tvStock: TextView = itemView.findViewById(R.id.tvStock)
        private val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvCartBadge)
        private val tvSku: TextView = itemView.findViewById(R.id.tvProductSku)

        fun bind(product: Product) {
            tvName.text = product.name
            tvPrice.text = String.format(Locale("id", "ID"), "Rp %,d", product.price.toLong())

            // TAMPILKAN SKU
            if (!product.barcode.isNullOrEmpty()) {
                tvSku.text = "SKU: ${product.barcode}"
                tvSku.visibility = View.VISIBLE
            } else {
                tvSku.visibility = View.GONE
            }

            // ============================================================
            // 🔥 1. AMBIL SETTINGAN "STOK LOS" (Ganti nama jadi storePrefs)
            // ============================================================
            val context = itemView.context
            val storePrefs = context.getSharedPreferences("store_prefs", android.content.Context.MODE_PRIVATE)
            val isStockSystemActive = storePrefs.getBoolean("use_stock_system", true)

            // 🔢 HITUNG SISA STOK
            // Pastikan 'cartList' bisa diakses di sini (biasanya passed dari Activity/Fragment)
            val totalQtyInCart = cartList.filter {
                it.id == product.id || it.parentId == product.id
            }.sumOf { it.stock }

            val currentStock = product.stock - totalQtyInCart

            // TAMPILAN TEXT STOK
            if (currentStock <= 0) {
                if (!isStockSystemActive) {
                    // KASUS STOK LOS (Oranye)
                    tvStock.text = "Stok: $currentStock (Los)"
                    tvStock.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                } else {
                    // KASUS WAJIB STOK (Merah)
                    tvStock.text = "Habis!"
                    tvStock.setTextColor(android.graphics.Color.RED)
                }
            } else {
                tvStock.text = "Stok: $currentStock"
                tvStock.setTextColor(android.graphics.Color.parseColor("#1976D2"))
            }

            // BADGE KERANJANG
            if (totalQtyInCart > 0) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = totalQtyInCart.toString()
            } else {
                tvBadge.visibility = View.GONE
            }

            // ============================================================
            // 🔥 2. LOAD GAMBAR (Ganti nama jadi ipPrefs)
            // ============================================================
            if (!product.imagePath.isNullOrEmpty()) {
                val ipPrefs = itemView.context.getSharedPreferences("SysdosSettings", android.content.Context.MODE_PRIVATE)
                val serverIp = ipPrefs.getString("SERVER_IP", "192.168.1.10:9000")

                // Gabung URL: http://192.168.1.10:9000/uploads/foto.jpg
                val fullUrl = "http://$serverIp${product.imagePath}"

                com.bumptech.glide.Glide.with(itemView.context)
                    .load(fullUrl)
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .centerCrop()
                    .into(imgProduct)
            } else {
                imgProduct.setImageResource(R.mipmap.ic_launcher)
            }

            // ============================================================
            // 🔥 3. LOGIKA KLIK (Stok Los vs Wajib Stok)
            // ============================================================
            itemView.setOnClickListener {
                if (isStockSystemActive) {
                    // WAJIB CEK STOK
                    if (currentStock > 0) {
                        onItemClick(product)
                    } else {
                        android.widget.Toast.makeText(context, "Stok Habis! (Cek Pengaturan Toko)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // STOK LOS (Bebas Klik)
                    onItemClick(product)
                }
            }

            itemView.setOnLongClickListener {
                onItemLongClick(product)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
    }
}