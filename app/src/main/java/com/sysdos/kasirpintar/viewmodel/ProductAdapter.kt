package com.sysdos.kasirpintar.viewmodel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // <--- PENTING: Jangan lupa Import Glide
import com.sysdos.kasirpintar.R
import com.sysdos.kasirpintar.data.model.Product
import java.util.Locale

class ProductAdapter(
    private val onItemClick: (Product) -> Unit,
    private val onItemLongClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback()) {

    // Menyimpan jumlah stok di keranjang (untuk update UI stok sisa)
    private var cartCounts: Map<Int, Int> = emptyMap()

    fun updateCartCounts(cartItems: List<Product>) {
        cartCounts = cartItems.groupBy { it.id }.mapValues { it.value.sumOf { item -> item.stock } }
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
        // ID View
        private val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        private val tvStock: TextView = itemView.findViewById(R.id.tvStock)

        // Pastikan di item_product.xml ID-nya benar: imgProduct
        private val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)

        private val tvBadge: TextView = itemView.findViewById(R.id.tvCartBadge)

        fun bind(product: Product) {
            tvName.text = product.name
            tvPrice.text = String.format(Locale("id", "ID"), "Rp %,d", product.price.toLong())

            // --- LOGIKA STOK SISA ---
            val qtyInCart = cartCounts[product.id] ?: 0
            val currentStock = product.stock - qtyInCart
            tvStock.text = "Stok: $currentStock"

            if (currentStock <= 0) {
                tvStock.setTextColor(android.graphics.Color.RED)
                tvStock.text = "Habis!"
            } else {
                tvStock.setTextColor(android.graphics.Color.parseColor("#1976D2"))
            }

            // --- LOGIKA BADGE ---
            if (qtyInCart > 0) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = qtyInCart.toString()
            } else {
                tvBadge.visibility = View.GONE
            }

            // ============================================================
            // 🔥 PERBAIKAN: GUNAKAN GLIDE UNTUK MUAT GAMBAR 🔥
            // ============================================================
            if (!product.imagePath.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(product.imagePath) // URL HTTP dari database
                    .placeholder(R.mipmap.ic_launcher) // Gambar loading
                    .error(R.mipmap.ic_launcher) // Gambar kalau error/gagal
                    .centerCrop() // Agar gambar pas di kotak
                    .into(imgProduct)
            } else {
                // Kalau tidak ada gambar, pakai icon default
                imgProduct.setImageResource(R.mipmap.ic_launcher)
            }
            // ============================================================

            // KLIK ITEM
            itemView.setOnClickListener { onItemClick(product) }

            // KLIK TAHAN
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