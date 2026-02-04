package com.sysdos.kasirpintar.viewmodel

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
        holder.bind(getItem(position))
    }

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ... (Definisi variabel View tetap sama) ...
        private val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        private val tvStock: TextView = itemView.findViewById(R.id.tvStock)
        private val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvCartBadge)
        private val tvSku: TextView = itemView.findViewById(R.id.tvProductSku)
        // 🔥 NEW BADGE
        private val tvType: TextView = itemView.findViewById(R.id.tvTypeBadge)

        fun bind(product: Product) {
            
            tvName.text = product.name
            tvPrice.text = String.format(Locale("id", "ID"), "Rp %,d", product.price.toLong())

            // 🔥 LOGIKA BADGE TIPE
            if (product.isIngredient) {
                tvType.visibility = View.VISIBLE
                tvType.text = "BAHAN"
                tvType.background.setTint(Color.parseColor("#FF9800")) // Orange
            } else if (!product.trackStock) {
                tvType.visibility = View.VISIBLE
                tvType.text = "MENU / JASA"
                tvType.background.setTint(Color.parseColor("#9C27B0")) // Ungu
            } else {
                tvType.visibility = View.GONE
            }

            if (!product.barcode.isNullOrEmpty()) {
                tvSku.text = "SKU: ${product.barcode}"
                tvSku.visibility = View.VISIBLE
            } else {
                tvSku.visibility = View.GONE
            }

            // ... (Bagian Stok & Settingan Toko tetap sama) ...
            val context = itemView.context
            val storePrefs = context.getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
            val isStockSystemActive = storePrefs.getBoolean("use_stock_system", true)

            val totalQtyInCart = cartList.filter {
                it.id == product.id || it.parentId == product.id
            }.sumOf { it.stock }

            val currentStock = product.stock - totalQtyInCart

            // 🔥 LOGIKA TAMPILAN STOK (Support Variabel trackStock)
            val shouldRestrictStock = isStockSystemActive && product.trackStock

            if (currentStock <= 0) {
                if (!shouldRestrictStock) {
                     // Produk Jasa / Menu Resep (Stok Virtual)
                    tvStock.text = "Stok: - ${product.unit}"
                    tvStock.setTextColor(Color.parseColor("#1976D2"))
                } else {
                    tvStock.text = "Habis!"
                    tvStock.setTextColor(Color.RED)
                }
            } else {
                tvStock.text = "Stok: $currentStock ${product.unit}"
                tvStock.setTextColor(Color.parseColor("#1976D2"))
            }

            if (totalQtyInCart > 0) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = totalQtyInCart.toString()
            } else {
                tvBadge.visibility = View.GONE
            }

            // ============================================================
            // 🖼️ LOGIKA LOAD GAMBAR (Simple Version like StockOpname)
            // ============================================================
            if (!product.imagePath.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(product.imagePath)
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .centerCrop()
                    .into(imgProduct)
            } else {
                imgProduct.setImageResource(R.mipmap.ic_launcher)
            }

            // ... (Bagian Klik Listener tetap sama) ...
            itemView.setOnClickListener {
                // 🔥 HAPUS LOGIKA DI SINI (Request User)
                // Biarkan MainActivity / ViewModel yang validasi
                onItemClick(product)
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