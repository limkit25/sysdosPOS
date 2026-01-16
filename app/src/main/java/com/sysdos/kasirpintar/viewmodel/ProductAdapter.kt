package com.sysdos.kasirpintar.viewmodel

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView // Tambahkan ini jika pakai CardView untuk badge
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

    // 🔥 UBAH DISINI: Simpan List Utuh, bukan Map
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
        private val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        private val tvStock: TextView = itemView.findViewById(R.id.tvStock)
        private val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)

        // Badge Merah
        private val tvBadge: TextView = itemView.findViewById(R.id.tvCartBadge)
        // Jika di XML badge dibungkus CardView, bind juga (opsional, sesuaikan XML)
        // private val cvBadge: CardView? = itemView.findViewById(R.id.cvCartBadge)

        fun bind(product: Product) {
            tvName.text = product.name
            tvPrice.text = String.format(Locale("id", "ID"), "Rp %,d", product.price.toLong())

            // ============================================================
            // 🔥 LOGIKA HITUNG JUMLAH (TERMASUK VARIAN) 🔥
            // ============================================================

            // Kita cari di keranjang:
            // 1. Item yang ID-nya sama dengan Produk ini (Produk Biasa)
            // 2. ATAU Item yang ParentID-nya sama dengan Produk ini (Produk Varian)
            val totalQtyInCart = cartList.filter {
                it.id == product.id || it.parentId == product.id
            }.sumOf { it.stock }

            // Hitung Stok Sisa untuk Tampilan
            val currentStock = product.stock - totalQtyInCart
            tvStock.text = "Stok: $currentStock"

            if (currentStock <= 0) {
                tvStock.setTextColor(Color.RED)
                tvStock.text = "Habis!"
            } else {
                tvStock.setTextColor(Color.parseColor("#1976D2"))
            }

            // ============================================================
            // 🔥 TAMPILKAN BADGE MERAH 🔥
            // ============================================================
            if (totalQtyInCart > 0) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = totalQtyInCart.toString()
                // cvBadge?.visibility = View.VISIBLE // Uncoment jika pakai CardView wrapper
            } else {
                tvBadge.visibility = View.GONE
                // cvBadge?.visibility = View.GONE
            }

            // ============================================================
            // 🖼️ LOAD GAMBAR (GLIDE)
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

            // KLIK LISTENER
            itemView.setOnClickListener { onItemClick(product) }
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